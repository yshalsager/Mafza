package com.yshalsager.mafza.core.data.history

import androidx.room.withTransaction
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
}
