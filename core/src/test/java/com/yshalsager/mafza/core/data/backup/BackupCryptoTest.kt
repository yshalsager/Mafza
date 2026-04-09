package com.yshalsager.mafza.core.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

class BackupCryptoTest {
    @Test
    fun encrypt_then_decrypt_roundtrip_returns_original_payload() {
        val plain_payload = """{"hello":"mafza","n":1}""".encodeToByteArray()
        val passphrase = "super-secure-passphrase".toCharArray()
        val envelope = BackupCrypto.encrypt_payload(
            payload_bytes = plain_payload,
            passphrase = passphrase
        )
        passphrase.fill('\u0000')

        val decrypted = BackupCrypto.decrypt_payload(
            envelope = envelope,
            passphrase = "super-secure-passphrase".toCharArray()
        )

        assertArrayEquals(plain_payload, decrypted)
    }

    @Test
    fun decrypt_with_wrong_passphrase_fails() {
        val envelope = BackupCrypto.encrypt_payload(
            payload_bytes = "secret-data".encodeToByteArray(),
            passphrase = "correct-passphrase".toCharArray()
        )

        assertThrows(AEADBadTagException::class.java) {
            BackupCrypto.decrypt_payload(
                envelope = envelope,
                passphrase = "wrong-passphrase".toCharArray()
            )
        }
    }

    @Test
    fun decrypt_rejects_unsupported_kdf_algorithm() {
        val envelope = BackupCrypto.encrypt_payload(
            payload_bytes = "secret-data".encodeToByteArray(),
            passphrase = "correct-passphrase".toCharArray()
        ).copy(kdf_algorithm = "PBKDF2WithHmacSHA1")

        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt_payload(
                envelope = envelope,
                passphrase = "correct-passphrase".toCharArray()
            )
        }

        assertNotEquals("", error.message)
    }

    @Test
    fun decrypt_rejects_non_positive_kdf_iterations() {
        val envelope = BackupCrypto.encrypt_payload(
            payload_bytes = "secret-data".encodeToByteArray(),
            passphrase = "correct-passphrase".toCharArray()
        ).copy(kdf_iterations = 0)

        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt_payload(
                envelope = envelope,
                passphrase = "correct-passphrase".toCharArray()
            )
        }
    }
}
