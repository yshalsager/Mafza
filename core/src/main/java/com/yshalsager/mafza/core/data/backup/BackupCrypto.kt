package com.yshalsager.mafza.core.data.backup

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val BACKUP_SCHEMA_VERSION = 1
const val BACKUP_KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
const val BACKUP_KDF_ITERATIONS = 210_000
const val BACKUP_SALT_BYTES = 16
const val BACKUP_NONCE_BYTES = 12
const val BACKUP_KEY_SIZE_BITS = 256
const val BACKUP_GCM_TAG_BITS = 128
const val BACKUP_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

@Serializable
data class EncryptedBackupEnvelope(
    val schema_version: Int,
    val kdf_algorithm: String,
    val kdf_iterations: Int,
    val salt_b64: String,
    val nonce_b64: String,
    val cipher_text_b64: String
)

object BackupCrypto {
    fun encrypt_payload(
        payload_bytes: ByteArray,
        passphrase: CharArray,
        schema_version: Int = BACKUP_SCHEMA_VERSION,
        secure_random: SecureRandom = SecureRandom()
    ): EncryptedBackupEnvelope {
        val salt = ByteArray(BACKUP_SALT_BYTES).also(secure_random::nextBytes)
        val nonce = ByteArray(BACKUP_NONCE_BYTES).also(secure_random::nextBytes)
        val secret_key = derive_secret_key(passphrase, salt, BACKUP_KDF_ITERATIONS)
        val cipher_text = Cipher.getInstance(BACKUP_CIPHER_TRANSFORMATION).run {
            init(Cipher.ENCRYPT_MODE, secret_key, GCMParameterSpec(BACKUP_GCM_TAG_BITS, nonce))
            doFinal(payload_bytes)
        }
        return EncryptedBackupEnvelope(
            schema_version = schema_version,
            kdf_algorithm = BACKUP_KDF_ALGORITHM,
            kdf_iterations = BACKUP_KDF_ITERATIONS,
            salt_b64 = encode_base64(salt),
            nonce_b64 = encode_base64(nonce),
            cipher_text_b64 = encode_base64(cipher_text)
        )
    }

    fun decrypt_payload(envelope: EncryptedBackupEnvelope, passphrase: CharArray): ByteArray {
        if (envelope.kdf_algorithm != BACKUP_KDF_ALGORITHM) {
            throw IllegalArgumentException("Unsupported backup kdf algorithm")
        }
        if (envelope.kdf_iterations <= 0) {
            throw IllegalArgumentException("Invalid backup kdf iterations")
        }

        val salt = decode_base64(envelope.salt_b64)
        val nonce = decode_base64(envelope.nonce_b64)
        val cipher_text = decode_base64(envelope.cipher_text_b64)
        val secret_key = derive_secret_key(passphrase, salt, envelope.kdf_iterations)
        return Cipher.getInstance(BACKUP_CIPHER_TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, secret_key, GCMParameterSpec(BACKUP_GCM_TAG_BITS, nonce))
            doFinal(cipher_text)
        }
    }

    private fun derive_secret_key(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val passphrase_copy = passphrase.copyOf()
        val pbe_key_spec = PBEKeySpec(passphrase_copy, salt, iterations, BACKUP_KEY_SIZE_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(BACKUP_KDF_ALGORITHM).generateSecret(pbe_key_spec).encoded
            SecretKeySpec(encoded, "AES")
        } finally {
            passphrase_copy.fill('\u0000')
            pbe_key_spec.clearPassword()
        }
    }

    private fun encode_base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode_base64(value: String): ByteArray = Base64.getDecoder().decode(value)
}
