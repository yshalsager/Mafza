package com.yshalsager.mafza.core.data.profile

import androidx.datastore.core.DataStore
import com.yshalsager.mafza.core.contracts.EmergencyProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class EncryptedProfileStore(
    private val data_store: DataStore<EncryptedProfileRecord>,
    private val profile_cipher: ProfileCipher,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    val profile_flow: Flow<EmergencyProfile> = data_store.data.map { encrypted_record ->
        decode_profile(encrypted_record)
    }

    suspend fun read_profile(): EmergencyProfile = profile_flow.first()

    suspend fun write_profile(profile: EmergencyProfile) {
        val payload = json.encodeToString(EmergencyProfile.serializer(), profile).encodeToByteArray()
        val encrypted_payload = profile_cipher.encrypt(payload)

        data_store.updateData {
            EncryptedProfileRecord(
                encrypted = true,
                iv_base64 = encrypted_payload.iv_base64(),
                cipher_text_base64 = encrypted_payload.cipher_text_base64()
            )
        }
    }

    suspend fun reset_profile() {
        data_store.updateData { EncryptedProfileRecord() }
    }

    private fun decode_profile(record: EncryptedProfileRecord): EmergencyProfile {
        if (!record.encrypted) return EmergencyProfile()
        if (record.iv_base64.isBlank() || record.cipher_text_base64.isBlank()) {
            throw InvalidEncryptedProfileException("Encrypted profile payload is missing")
        }

        val encrypted_payload = EncryptedPayload.from_base64(record.iv_base64, record.cipher_text_base64)
        val plain_bytes = profile_cipher.decrypt(encrypted_payload)
        return json.decodeFromString(EmergencyProfile.serializer(), plain_bytes.decodeToString())
    }
}

class InvalidEncryptedProfileException(message: String) : IllegalStateException(message)
