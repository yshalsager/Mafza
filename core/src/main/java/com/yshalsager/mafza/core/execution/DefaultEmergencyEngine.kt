package com.yshalsager.mafza.core.execution

import com.yshalsager.mafza.core.contracts.EmergencyEngine
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.PolicyBoundEmergencyStep
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionPolicy
import com.yshalsager.mafza.core.contracts.ActionPolicyKeys
import com.yshalsager.mafza.core.contracts.RunId
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.RunStatusDeriver
import com.yshalsager.mafza.core.contracts.StepBranch
import com.yshalsager.mafza.core.contracts.StepContext
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class DefaultEmergencyEngine(
    private val scope: CoroutineScope,
    private val profile_reader: suspend () -> EmergencyProfile,
    private val steps_provider: suspend (StepContext) -> List<EmergencyStep>,
    private val on_event: (EngineEvent) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val run_id_factory: () -> RunId = { UUID.randomUUID().toString() },
    private val cancel_window_millis_provider: (EmergencyProfile) -> Long = { profile ->
        profile.cancel_window_seconds.coerceIn(1, 30).toLong() * 1_000L
    }
) : EmergencyEngine {
    private val state_lock = Any()
    private var active_run_state: ActiveRunState? = null

    override fun start(trigger: TriggerSource, mode: ExecutionMode): RunId {
        synchronized(state_lock) {
            val current_state = active_run_state
            if (current_state != null) {
                on_event(
                    EngineEvent.IgnoredDuplicateTrigger(
                        active_run_id = current_state.run_id,
                        requested_trigger = trigger,
                        requested_mode = mode
                    )
                )
                return current_state.run_id
            }

            val run_id = run_id_factory()
            val run_job = scope.launch(start = CoroutineStart.ATOMIC) { execute_run(run_id, trigger, mode) }
            active_run_state = ActiveRunState(
                run_id = run_id,
                job = run_job,
                cancel_window_open = true
            )
            return run_id
        }
    }

    override fun cancelWithinWindow(run_id: RunId): Boolean {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return false
            if (current_state.run_id != run_id) return false
            if (!current_state.cancel_window_open) return false

            current_state.cancelled_pre_start = true
            current_state.job.cancel()
            return true
        }
    }

    private suspend fun execute_run(run_id: RunId, trigger: TriggerSource, mode: ExecutionMode) {
        val step_statuses = mutableListOf<StepStatus>()
        var required_step_failed = false
        try {
            val started_at = clock()
            on_event(
                EngineEvent.RunStarted(
                    run_id = run_id,
                    trigger = trigger,
                    mode = mode,
                    started_at_epoch_ms = started_at
                )
            )

            val profile_snapshot = try {
                profile_reader()
            } catch (cancelled_exception: CancellationException) {
                if (is_cancelled_pre_start(run_id)) {
                    on_event(EngineEvent.RunCancelledPreStart(run_id))
                    complete_run(
                        run_id = run_id,
                        run_status = RunStatus.CANCELLED_PRE_START,
                        step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                    )
                    return
                }
                throw cancelled_exception
            } catch (_: Throwable) {
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.COMPLETED_FAILED,
                    step_statuses = emptyList()
                )
                return
            }
            val cancel_window_millis = cancel_window_millis_provider(profile_snapshot)
            val cancel_window_ends_at = started_at + cancel_window_millis
            val remaining_cancel_window_millis = (cancel_window_ends_at - clock()).coerceAtLeast(0L)

            set_cancel_window_state(run_id, is_open = true)
            on_event(
                EngineEvent.CancelWindowOpened(
                    run_id = run_id,
                    window_ends_at_epoch_ms = cancel_window_ends_at
                )
            )

            val cancelled_pre_start = try {
                wait_cancel_window(run_id, remaining_cancel_window_millis)
            } finally {
                set_cancel_window_state(run_id, is_open = false)
            }
            if (cancelled_pre_start) {
                on_event(EngineEvent.RunCancelledPreStart(run_id))
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.CANCELLED_PRE_START,
                    step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                )
                return
            }

            val step_context = StepContext(
                run_id = run_id,
                trigger = trigger,
                mode = mode,
                profile = profile_snapshot,
                started_at_epoch_ms = started_at
            )
            val action_policies = profile_snapshot.action_policies

            val steps = try {
                steps_provider(step_context)
            } catch (cancelled_exception: CancellationException) {
                throw cancelled_exception
            } catch (_: Throwable) {
                emptyList()
            }
            val planned_steps = build_step_execution_plan(steps, action_policies)
            val blocked_branches = mutableSetOf<StepBranch>()
            planned_steps.forEach { planned_step ->
                val policy_step = planned_step.step as? PolicyBoundEmergencyStep
                val branch = policy_step?.branch
                val policy = planned_step.policy
                val step_id = planned_step.step::class.simpleName ?: "unknown_step"

                if (branch != null && branch in blocked_branches) {
                    val skipped_result = StepResult(
                        step_id = step_id,
                        status = StepStatus.SKIPPED_UNAVAILABLE,
                        details = "skipped_by_branch_stop",
                        started_at_epoch_ms = clock(),
                        finished_at_epoch_ms = clock()
                    )
                    step_statuses += skipped_result.status
                    if (policy?.required == true && is_required_step_failure(skipped_result.status, mode)) {
                        required_step_failed = true
                    }
                    on_event(EngineEvent.StepCompleted(run_id = run_id, step_result = skipped_result))
                    return@forEach
                }

                if (policy != null && !policy.enabled) {
                    val disabled_result = StepResult(
                        step_id = step_id,
                        status = StepStatus.SKIPPED_UNAVAILABLE,
                        details = "disabled_by_policy",
                        started_at_epoch_ms = clock(),
                        finished_at_epoch_ms = clock()
                    )
                    step_statuses += disabled_result.status
                    if (policy.required && is_required_step_failure(disabled_result.status, mode)) {
                        required_step_failed = true
                    }
                    on_event(EngineEvent.StepCompleted(run_id = run_id, step_result = disabled_result))
                    return@forEach
                }

                val step_start = clock()
                val step_result = try {
                    planned_step.step.execute(step_context)
                } catch (cancelled_exception: CancellationException) {
                    throw cancelled_exception
                } catch (throwable: Throwable) {
                    StepResult(
                        step_id = step_id,
                        status = StepStatus.FAILED,
                        details = throwable.message,
                        started_at_epoch_ms = step_start,
                        finished_at_epoch_ms = clock()
                    )
                }

                step_statuses += step_result.status
                if (policy?.required == true && is_required_step_failure(step_result.status, mode)) {
                    required_step_failed = true
                }
                on_event(EngineEvent.StepCompleted(run_id = run_id, step_result = step_result))

                if (
                    branch != null &&
                    policy != null &&
                    step_result.status in listOf(StepStatus.FAILED, StepStatus.TIMED_OUT) &&
                    !policy.continue_on_failure
                ) {
                    blocked_branches += branch
                }
            }

            val run_status = RunStatusDeriver.derive_run_status(
                is_running = false,
                cancelled_pre_start = false,
                step_statuses = step_statuses,
                required_step_failed = required_step_failed
            )
            complete_run(run_id = run_id, run_status = run_status, step_statuses = step_statuses)
        } catch (cancelled_exception: CancellationException) {
            if (is_cancelled_pre_start(run_id)) {
                on_event(EngineEvent.RunCancelledPreStart(run_id))
                complete_run(
                    run_id = run_id,
                    run_status = RunStatus.CANCELLED_PRE_START,
                    step_statuses = listOf(StepStatus.CANCELLED_PRE_START)
                )
                return
            }
            complete_run(
                run_id = run_id,
                run_status = RunStatus.COMPLETED_FAILED,
                step_statuses = step_statuses
            )
        } finally {
            clear_active_run(run_id)
        }
    }

    private suspend fun wait_cancel_window(run_id: RunId, cancel_window_millis: Long): Boolean {
        return try {
            delay(cancel_window_millis)
            false
        } catch (cancelled_exception: CancellationException) {
            if (is_cancelled_pre_start(run_id)) return true
            throw cancelled_exception
        }
    }

    private fun complete_run(run_id: RunId, run_status: RunStatus, step_statuses: List<StepStatus>) {
        val completed_at = clock()
        on_event(
            EngineEvent.RunCompleted(
                run_id = run_id,
                run_status = run_status,
                step_statuses = step_statuses,
                completed_at_epoch_ms = completed_at
            )
        )
    }

    private fun build_step_execution_plan(
        steps: List<EmergencyStep>,
        action_policies: List<ActionPolicy>
    ): List<PlannedExecutionStep> {
        val policy_index = build_policy_index(action_policies)
        val indexed_steps = steps.mapIndexed { index, step -> IndexedStep(index = index, step = step) }
        val non_policy_steps = indexed_steps
            .filter { it.step !is PolicyBoundEmergencyStep }
            .map { PlannedExecutionStep(step = it.step, policy = null, order = it.index) }

        val notify_steps = ordered_policy_steps_for_branch(
            indexed_steps = indexed_steps,
            policy_index = policy_index,
            branch = StepBranch.NOTIFY
        )
        val destructive_steps = ordered_policy_steps_for_branch(
            indexed_steps = indexed_steps,
            policy_index = policy_index,
            branch = StepBranch.DESTRUCTIVE
        )
        val finalize_steps = ordered_policy_steps_for_branch(
            indexed_steps = indexed_steps,
            policy_index = policy_index,
            branch = StepBranch.FINALIZE
        )

        return non_policy_steps + notify_steps + destructive_steps + finalize_steps
    }

    private fun ordered_policy_steps_for_branch(
        indexed_steps: List<IndexedStep>,
        policy_index: PolicyIndex,
        branch: StepBranch
    ): List<PlannedExecutionStep> {
        return indexed_steps
            .mapNotNull { indexed_step ->
                val policy_step = indexed_step.step as? PolicyBoundEmergencyStep ?: return@mapNotNull null
                if (policy_step.branch != branch) return@mapNotNull null

                val resolved_policy = when (val resolution = resolve_policy_for_step(policy_index, policy_step)) {
                    is PolicyResolution.Resolved -> resolution.policy
                    PolicyResolution.Invalid -> invalid_action_policy(policy_step.action_id, policy_step.policy_key)
                    PolicyResolution.Missing -> default_action_policy(policy_step.action_id, policy_step.policy_key)
                }
                PlannedExecutionStep(
                    step = indexed_step.step,
                    policy = resolved_policy,
                    order = resolved_policy.execution_order,
                    original_index = indexed_step.index
                )
            }
            .sortedWith(compareBy({ it.order }, { it.original_index }))
    }

    private fun build_policy_index(action_policies: List<ActionPolicy>): PolicyIndex {
        val normalized = action_policies.map { policy ->
            policy to policy.policy_key.trim()
        }
        val actions_with_malformed_policy_keys = normalized
            .filter { (_, normalized_policy_key) -> normalized_policy_key.isEmpty() }
            .map { (policy, _) -> policy.action_id }
            .toSet()
        val grouped_by_key = normalized
            .filter { (_, normalized_policy_key) -> normalized_policy_key.isNotEmpty() }
            .groupBy(
                keySelector = { (_, normalized_policy_key) -> normalized_policy_key },
                valueTransform = { (policy, _) -> policy }
            )
        val duplicate_policy_keys = grouped_by_key
            .filterValues { policies -> policies.size > 1 }
            .keys
        val unique_policies_by_key = grouped_by_key
            .filterValues { policies -> policies.size == 1 }
            .mapValues { (_, policies) -> policies.first() }
        return PolicyIndex(
            unique_policies_by_key = unique_policies_by_key,
            duplicate_policy_keys = duplicate_policy_keys,
            actions_with_malformed_policy_keys = actions_with_malformed_policy_keys
        )
    }

    private fun resolve_policy_for_step(
        policy_index: PolicyIndex,
        policy_step: PolicyBoundEmergencyStep
    ): PolicyResolution {
        if (policy_step.action_id in policy_index.actions_with_malformed_policy_keys) {
            return PolicyResolution.Invalid
        }

        val normalized_specific_policy_key = policy_step.policy_key.trim()
        if (normalized_specific_policy_key.isNotEmpty()) {
            if (normalized_specific_policy_key in policy_index.duplicate_policy_keys) {
                return PolicyResolution.Invalid
            }
            policy_index.unique_policies_by_key[normalized_specific_policy_key]?.let { policy ->
                return PolicyResolution.Resolved(policy)
            }
        }

        val action_policy_key = ActionPolicyKeys.for_action(policy_step.action_id)
        if (action_policy_key in policy_index.duplicate_policy_keys) {
            return PolicyResolution.Invalid
        }
        policy_index.unique_policies_by_key[action_policy_key]?.let { policy ->
            return PolicyResolution.Resolved(policy)
        }
        return PolicyResolution.Missing
    }

    private fun default_action_policy(action_id: ActionId, policy_key: String): ActionPolicy {
        return ActionPolicy(
            action_id = action_id,
            policy_key = policy_key,
            enabled = true,
            required = false,
            continue_on_failure = true,
            execution_order = Int.MAX_VALUE
        )
    }

    private fun invalid_action_policy(action_id: ActionId, policy_key: String): ActionPolicy {
        return ActionPolicy(
            action_id = action_id,
            policy_key = policy_key,
            enabled = false,
            required = true,
            continue_on_failure = false,
            execution_order = Int.MAX_VALUE
        )
    }

    private fun is_required_step_failure(status: StepStatus, mode: ExecutionMode): Boolean {
        if (status == StepStatus.SUCCESS) return false
        if (status == StepStatus.CANCELLED_PRE_START) return false
        if (mode == ExecutionMode.DRY_RUN && status == StepStatus.SKIPPED_DRY_RUN) return false
        return true
    }

    private fun set_cancel_window_state(run_id: RunId, is_open: Boolean) {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return
            if (current_state.run_id != run_id) return
            current_state.cancel_window_open = is_open
        }
    }

    private fun is_cancelled_pre_start(run_id: RunId): Boolean {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return false
            if (current_state.run_id != run_id) return false
            return current_state.cancelled_pre_start
        }
    }

    private fun clear_active_run(run_id: RunId) {
        synchronized(state_lock) {
            val current_state = active_run_state ?: return
            if (current_state.run_id == run_id) active_run_state = null
        }
    }

    private data class ActiveRunState(
        val run_id: RunId,
        val job: Job,
        var cancel_window_open: Boolean = false,
        var cancelled_pre_start: Boolean = false
    )

    private data class IndexedStep(
        val index: Int,
        val step: EmergencyStep
    )

    private data class PlannedExecutionStep(
        val step: EmergencyStep,
        val policy: com.yshalsager.mafza.core.contracts.ActionPolicy?,
        val order: Int,
        val original_index: Int = Int.MAX_VALUE
    )

    private data class PolicyIndex(
        val unique_policies_by_key: Map<String, ActionPolicy>,
        val duplicate_policy_keys: Set<String>,
        val actions_with_malformed_policy_keys: Set<ActionId>
    )

    private sealed interface PolicyResolution {
        data class Resolved(val policy: ActionPolicy) : PolicyResolution
        data object Invalid : PolicyResolution
        data object Missing : PolicyResolution
    }
}
