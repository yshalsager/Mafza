package com.yshalsager.mafza.core.data.profile

import android.util.Base64

data class EncryptedPayload(
    val iv: ByteArray,
    val cipher_text: ByteArray
) {
    fun iv_base64(): String = Base64.encodeToString(iv, Base64.NO_WRAP)
    fun cipher_text_base64(): String = Base64.encodeToString(cipher_text, Base64.NO_WRAP)

    companion object {
        fun from_base64(iv_base64: String, cipher_text_base64: String): EncryptedPayload {
            return EncryptedPayload(
                iv = Base64.decode(iv_base64, Base64.NO_WRAP),
                cipher_text = Base64.decode(cipher_text_base64, Base64.NO_WRAP)
            )
        }
    }
}

interface ProfileCipher {
    fun encrypt(plain_bytes: ByteArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): ByteArray
}
