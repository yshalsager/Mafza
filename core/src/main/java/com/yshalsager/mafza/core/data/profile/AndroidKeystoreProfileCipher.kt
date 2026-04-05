package com.yshalsager.mafza.core.data.profile

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreProfileCipher(
    private val key_alias: String = KEY_ALIAS
) : ProfileCipher {
    override fun encrypt(plain_bytes: ByteArray): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, get_or_create_secret_key())
        val cipher_text = cipher.doFinal(plain_bytes)

        return EncryptedPayload(iv = cipher.iv, cipher_text = cipher_text)
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val gcm_spec = GCMParameterSpec(AUTH_TAG_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, get_or_create_secret_key(), gcm_spec)
        return cipher.doFinal(payload.cipher_text)
    }

    private fun get_or_create_secret_key(): SecretKey {
        val key_store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing_key = key_store.getKey(key_alias, null) as? SecretKey
        if (existing_key != null) return existing_key

        val key_generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val key_spec = KeyGenParameterSpec.Builder(
            key_alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()

        key_generator.init(key_spec)
        return key_generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val AUTH_TAG_BITS = 128
        const val KEY_ALIAS = "mafza_profile_datastore_key_v1"
    }
}
