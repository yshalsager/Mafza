package com.yshalsager.mafza.core.data.backup

import com.yshalsager.mafza.core.contracts.BackupPayload
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunHistoryCommandAuditExportItem
import com.yshalsager.mafza.core.contracts.RunHistoryExportItem
import com.yshalsager.mafza.core.contracts.RunHistoryStepExportItem
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedBackupServiceRestoreSemanticsTest {
    @Test
    fun apply_restored_payload_replaces_profile_and_history_when_history_is_included() = runBlocking {
        val current_profile = EmergencyProfile(notify_target = "old-target")
        val current_history = listOf(run_history_item("old-run", 1L))
        val payload = BackupPayload(
            schema_version = BACKUP_SCHEMA_VERSION,
            exported_at_epoch_ms = 100L,
            app_version = "0.1.0",
            profile = EmergencyProfile(notify_target = "new-target"),
            include_history = true,
            history = listOf(run_history_item("new-run", 2L))
        )

        var read_profile_calls = 0
        var export_history_calls = 0
        val written_profiles = mutableListOf<EmergencyProfile>()
        val replaced_histories = mutableListOf<List<RunHistoryExportItem>>()

        val restored_history_count = apply_restored_payload_with_rollback(
            payload = payload,
            read_profile = {
                read_profile_calls += 1
                current_profile
            },
            write_profile = { written_profiles += it },
            export_history = {
                export_history_calls += 1
                current_history
            },
            replace_history = { replaced_histories += it }
        )

        assertEquals(1, restored_history_count)
        assertEquals(1, read_profile_calls)
        assertEquals(1, export_history_calls)
        assertEquals(listOf(payload.profile), written_profiles)
        assertEquals(listOf(payload.history), replaced_histories)
        assertEquals("notify_sms_0", replaced_histories.first().first().steps.first().step_id)
        assertEquals(ActionId.ADVANCED_SHELL_COMMANDS, replaced_histories.first().first().command_audits.first().action_id)
    }

    @Test
    fun apply_restored_payload_rolls_back_profile_and_history_when_history_replace_fails() = runBlocking {
        val current_profile = EmergencyProfile(message_template = "old-template")
        val current_history = listOf(run_history_item("old-run", 1L))
        val payload = BackupPayload(
            schema_version = BACKUP_SCHEMA_VERSION,
            exported_at_epoch_ms = 100L,
            app_version = "0.1.0",
            profile = EmergencyProfile(message_template = "new-template"),
            include_history = true,
            history = listOf(run_history_item("new-run", 2L))
        )

        val written_profiles = mutableListOf<EmergencyProfile>()
        val replaced_histories = mutableListOf<List<RunHistoryExportItem>>()
        var first_history_replace = true

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                apply_restored_payload_with_rollback(
                    payload = payload,
                    read_profile = { current_profile },
                    write_profile = { written_profiles += it },
                    export_history = { current_history },
                    replace_history = {
                        replaced_histories += it
                        if (first_history_replace) {
                            first_history_replace = false
                            throw IllegalStateException("history replace failed")
                        }
                    }
                )
            }
        }

        assertEquals(listOf(payload.profile, current_profile), written_profiles)
        assertEquals(listOf(payload.history, current_history), replaced_histories)
    }

    @Test
    fun apply_restored_payload_skips_history_restore_when_history_not_included() = runBlocking {
        val payload = BackupPayload(
            schema_version = BACKUP_SCHEMA_VERSION,
            exported_at_epoch_ms = 100L,
            app_version = "0.1.0",
            profile = EmergencyProfile(message_template = "updated"),
            include_history = false,
            history = listOf(run_history_item("ignored", 2L))
        )

        var export_history_called = false
        var replace_history_called = false
        val written_profiles = mutableListOf<EmergencyProfile>()

        val restored_history_count = apply_restored_payload_with_rollback(
            payload = payload,
            read_profile = { EmergencyProfile() },
            write_profile = { written_profiles += it },
            export_history = {
                export_history_called = true
                emptyList()
            },
            replace_history = {
                replace_history_called = true
            }
        )

        assertEquals(0, restored_history_count)
        assertEquals(listOf(payload.profile), written_profiles)
        assertFalse(export_history_called)
        assertFalse(replace_history_called)
        assertTrue(payload.history.isNotEmpty())
    }

    private fun run_history_item(run_id: String, started_at: Long): RunHistoryExportItem {
        return RunHistoryExportItem(
            run_id = run_id,
            started_at_epoch_ms = started_at,
            completed_at_epoch_ms = started_at + 1L,
            trigger = TriggerSource.MANUAL_IN_APP,
            mode = ExecutionMode.LIVE,
            status = RunStatus.COMPLETED_SUCCESS,
            steps = listOf(
                RunHistoryStepExportItem(
                    step_index = 0,
                    step_id = "notify_sms_0",
                    status = StepStatus.SUCCESS,
                    details = "sent",
                    started_at_epoch_ms = started_at,
                    finished_at_epoch_ms = started_at + 1L
                )
            ),
            command_audits = listOf(
                RunHistoryCommandAuditExportItem(
                    step_index = 0,
                    command_index = 0,
                    action_id = ActionId.ADVANCED_SHELL_COMMANDS,
                    target_summary = "svc data disable",
                    exit_code = 0,
                    stderr_snippet = null
                )
            )
        )
    }
}
