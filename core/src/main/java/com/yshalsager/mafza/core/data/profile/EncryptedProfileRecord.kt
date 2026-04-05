package com.yshalsager.mafza.core.data.profile

import kotlinx.serialization.Serializable

@Serializable
data class EncryptedProfileRecord(
    val version: Int = 1,
    val encrypted: Boolean = false,
    val iv_base64: String = "",
    val cipher_text_base64: String = ""
)
