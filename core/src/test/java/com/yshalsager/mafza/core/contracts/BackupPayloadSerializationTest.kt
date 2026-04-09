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
                triggers_enabled = false
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
    }
}
