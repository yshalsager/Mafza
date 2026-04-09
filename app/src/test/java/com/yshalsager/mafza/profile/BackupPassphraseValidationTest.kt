package com.yshalsager.mafza.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPassphraseValidationTest {
    @Test
    fun export_passphrase_validation_fails_when_too_short() {
        val result = validate_export_backup_passphrase(
            passphrase = "short",
            passphrase_confirm = "short"
        )

        assertEquals(ExportBackupPassphraseValidation.TOO_SHORT, result)
    }

    @Test
    fun export_passphrase_validation_fails_on_mismatch() {
        val result = validate_export_backup_passphrase(
            passphrase = "very-strong-123",
            passphrase_confirm = "very-strong-124"
        )

        assertEquals(ExportBackupPassphraseValidation.MISMATCH, result)
    }

    @Test
    fun export_passphrase_validation_passes_for_matching_long_passphrase() {
        val result = validate_export_backup_passphrase(
            passphrase = "very-strong-123",
            passphrase_confirm = "very-strong-123"
        )

        assertEquals(ExportBackupPassphraseValidation.VALID, result)
    }

    @Test
    fun restore_passphrase_validation_requires_non_blank_value() {
        assertFalse(is_restore_backup_passphrase_valid(""))
        assertFalse(is_restore_backup_passphrase_valid("   "))
        assertTrue(is_restore_backup_passphrase_valid("passphrase"))
    }
}
