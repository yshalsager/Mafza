package com.yshalsager.mafza.core.contracts

interface EmergencyEngine {
    fun start(trigger: TriggerSource, mode: ExecutionMode): RunId
    fun cancelWithinWindow(run_id: RunId): Boolean
}

interface EmergencyStep {
    suspend fun execute(ctx: StepContext): StepResult
}

enum class StepBranch {
    NOTIFY,
    DESTRUCTIVE,
    FINALIZE
}

interface PolicyBoundEmergencyStep : EmergencyStep {
    val action_id: ActionId
    val policy_key: String
        get() = ActionPolicyKeys.for_action(action_id)
    val branch: StepBranch
}

data class StepContext(
    val run_id: RunId,
    val trigger: TriggerSource,
    val mode: ExecutionMode,
    val profile: EmergencyProfile,
    val started_at_epoch_ms: Long
)

data class StepResult(
    val step_id: String,
    val status: StepStatus,
    val details: String? = null,
    val started_at_epoch_ms: Long,
    val finished_at_epoch_ms: Long
)
