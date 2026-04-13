package com.yshalsager.mafza.core.data.history

import androidx.room.withTransaction
import com.yshalsager.mafza.core.contracts.RunHistoryCommandAuditExportItem
import com.yshalsager.mafza.core.contracts.RunHistoryExportItem
import com.yshalsager.mafza.core.contracts.RunHistoryStepExportItem
import kotlinx.serialization.json.Json

class RunHistoryStore(
    private val database: MafzaHistoryDatabase,
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
) {
    private val dao = database.run_history_dao()

    suspend fun insert_with_retention(
        bundle: RunHistoryInsertBundle,
        keep_latest: Int = HISTORY_RETENTION_COUNT
    ) {
        database.withTransaction {
            dao.upsert_run(bundle.run)
            dao.delete_steps_for_run(bundle.run.run_id)
            dao.delete_command_audits_for_run(bundle.run.run_id)

            if (bundle.steps.isNotEmpty()) dao.insert_steps(bundle.steps)
            if (bundle.command_audits.isNotEmpty()) {
                val sanitized = bundle.command_audits.map { audit ->
                    audit.copy(stderr_snippet = RunHistoryRetentionPolicy.sanitize_stderr_snippet(audit.stderr_snippet))
                }
                dao.insert_command_audits(sanitized)
            }

            val all_run_ids = dao.all_run_ids_desc()
            val run_ids_to_prune = RunHistoryRetentionPolicy.run_ids_to_prune(all_run_ids, keep_latest)
            if (run_ids_to_prune.isNotEmpty()) dao.delete_runs_by_ids(run_ids_to_prune)
        }
    }

    suspend fun export_redacted_audit_json(limit: Int = HISTORY_RETENTION_COUNT): String {
        val runs = dao.latest_runs(limit)
        if (runs.isEmpty()) {
            return json.encodeToString(RedactedAuditExport.serializer(), RedactedAuditExport(runs = emptyList()))
        }

        val run_ids = runs.map { it.run_id }
        val steps_by_run = dao.steps_for_runs(run_ids).groupBy { it.run_id }
        val audits_by_run = dao.command_audits_for_runs(run_ids).groupBy { it.run_id }

        val export = RedactedAuditExport(
            runs = runs.map { run ->
                RedactedRunAudit(
                    run_id = run.run_id,
                    started_at_epoch_ms = run.started_at_epoch_ms,
                    completed_at_epoch_ms = run.completed_at_epoch_ms,
                    trigger = run.trigger,
                    mode = run.mode,
                    status = run.status,
                    step_statuses = steps_by_run[run.run_id].orEmpty().map { step ->
                        RedactedStepStatus(step_id = step.step_id, status = step.status)
                    },
                    command_audits = audits_by_run[run.run_id].orEmpty().map { audit ->
                        RedactedCommandAudit(
                            action_id = audit.action_id,
                            target_summary = audit.target_summary,
                            exit_code = audit.exit_code,
                            stderr_snippet = audit.stderr_snippet
                        )
                    }
                )
            }
        )

        return json.encodeToString(RedactedAuditExport.serializer(), export)
    }

    suspend fun export_runs_for_backup(limit: Int = HISTORY_RETENTION_COUNT): List<RunHistoryExportItem> {
        val runs = dao.latest_runs(limit)
        if (runs.isEmpty()) return emptyList()

        val run_ids = runs.map { it.run_id }
        val steps_by_run = dao.steps_for_runs(run_ids).groupBy { it.run_id }
        val audits_by_run = dao.command_audits_for_runs(run_ids).groupBy { it.run_id }

        return runs.map { run ->
            RunHistoryExportItem(
                run_id = run.run_id,
                started_at_epoch_ms = run.started_at_epoch_ms,
                completed_at_epoch_ms = run.completed_at_epoch_ms ?: run.started_at_epoch_ms,
                trigger = run.trigger,
                mode = run.mode,
                status = run.status,
                steps = steps_by_run[run.run_id].orEmpty().map { step ->
                    RunHistoryStepExportItem(
                        step_index = step.step_index,
                        step_id = step.step_id,
                        status = step.status,
                        details = step.details,
                        started_at_epoch_ms = step.started_at_epoch_ms,
                        finished_at_epoch_ms = step.finished_at_epoch_ms
                    )
                },
                command_audits = audits_by_run[run.run_id].orEmpty().map { audit ->
                    RunHistoryCommandAuditExportItem(
                        step_index = audit.step_index,
                        command_index = audit.command_index,
                        action_id = audit.action_id,
                        target_summary = audit.target_summary,
                        exit_code = audit.exit_code,
                        stderr_snippet = audit.stderr_snippet
                    )
                }
            )
        }
    }

    suspend fun replace_runs_from_backup(runs: List<RunHistoryExportItem>, keep_latest: Int = HISTORY_RETENTION_COUNT) {
        database.withTransaction {
            dao.delete_all_runs()
            if (runs.isEmpty()) return@withTransaction

            val selected_runs = runs
                .sortedByDescending { it.started_at_epoch_ms }
                .distinctBy { it.run_id }
                .take(keep_latest)
            val normalized_runs = selected_runs.map { run ->
                RunHistoryEntity(
                    run_id = run.run_id,
                    started_at_epoch_ms = run.started_at_epoch_ms,
                    completed_at_epoch_ms = run.completed_at_epoch_ms,
                    trigger = run.trigger,
                    mode = run.mode,
                    status = run.status
                )
            }
            val normalized_steps = selected_runs.flatMap { run ->
                run.steps.map { step ->
                    StepHistoryEntity(
                        run_id = run.run_id,
                        step_index = step.step_index,
                        step_id = step.step_id,
                        status = step.status,
                        details = step.details,
                        started_at_epoch_ms = step.started_at_epoch_ms,
                        finished_at_epoch_ms = step.finished_at_epoch_ms
                    )
                }
            }
            val normalized_audits = selected_runs.flatMap { run ->
                run.command_audits.map { audit ->
                    CommandAuditEntity(
                        run_id = run.run_id,
                        step_index = audit.step_index,
                        command_index = audit.command_index,
                        action_id = audit.action_id,
                        target_summary = audit.target_summary,
                        exit_code = audit.exit_code,
                        stderr_snippet = RunHistoryRetentionPolicy.sanitize_stderr_snippet(audit.stderr_snippet)
                    )
                }
            }

            dao.insert_runs(normalized_runs)
            if (normalized_steps.isNotEmpty()) dao.insert_steps(normalized_steps)
            if (normalized_audits.isNotEmpty()) dao.insert_command_audits(normalized_audits)
        }
    }
}
