package com.yshalsager.mafza.core.contracts

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun backup_payload_roundtrip_preserves_schema_and_profile_data() {
        val payload = BackupPayload(
            schema_version = 1,
            exported_at_epoch_ms = 1_700_000_000_000,
            app_version = "0.1.0",
            profile = EmergencyProfile(
                sms_recipients = listOf("+201111111111"),
                message_template = "Alert {timestamp}",
                triggers_enabled = false,
                action_bindings = listOf(
                    ActionBinding(
                        action_id = ActionId.NOTIFY_MESSAGE_APP,
                        binding_id = "notify_1",
                        package_name = "org.telegram.messenger",
                        activity_name = "org.telegram.ui.LaunchActivity",
                        enabled = true
                    )
                ),
                action_policies = listOf(
                    ActionPolicy(
                        action_id = ActionId.NOTIFY_MESSAGE_APP,
                        policy_key = "binding:notify_message_app:notify_1",
                        enabled = true,
                        required = true,
                        continue_on_failure = false,
                        execution_order = 3
                    )
                ),
                intent_actions = listOf(
                    IntentActionSpec(
                        id = "intent_1",
                        label = "Open map",
                        intent_action = "android.intent.action.VIEW",
                        data_uri = "geo:30.0,31.0",
                        mime_type = null,
                        categories = emptyList(),
                        package_name = "com.google.android.apps.maps",
                        activity_name = null,
                        extras_json = null,
                        flags = emptyList(),
                        continue_on_failure = true,
                        enabled = true
                    )
                ),
                advanced_shell_commands = listOf(
                    ShellCommandSpec(
                        id = "shell_1",
                        label = "Disable data",
                        argv = listOf("svc", "data", "disable"),
                        raw_shell = null,
                        timeout_seconds = 5,
                        continue_on_failure = true,
                        enabled = true
                    )
                )
            ),
            include_history = true,
            history = listOf(
                RunHistoryExportItem(
                    run_id = "run-1",
                    started_at_epoch_ms = 100L,
                    completed_at_epoch_ms = 200L,
                    trigger = TriggerSource.MANUAL_IN_APP,
                    mode = ExecutionMode.DRY_RUN,
                    status = RunStatus.COMPLETED_PARTIAL
                )
            )
        )

        val encoded = json.encodeToString(BackupPayload.serializer(), payload)
        val decoded = json.decodeFromString(BackupPayload.serializer(), encoded)

        assertEquals(1, decoded.schema_version)
        assertEquals("0.1.0", decoded.app_version)
        assertEquals(listOf("+201111111111"), decoded.profile.sms_recipients)
        assertTrue(decoded.include_history)
        assertEquals(1, decoded.history.size)
        assertEquals("run-1", decoded.history.first().run_id)
        assertEquals("notify_1", decoded.profile.action_bindings.single().binding_id)
        assertEquals("binding:notify_message_app:notify_1", decoded.profile.action_policies.single().policy_key)
        assertEquals("intent_1", decoded.profile.intent_actions.single().id)
        assertEquals("shell_1", decoded.profile.advanced_shell_commands.single().id)
    }
}
