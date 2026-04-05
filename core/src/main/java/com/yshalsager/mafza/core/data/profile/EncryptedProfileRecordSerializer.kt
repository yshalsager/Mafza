package com.yshalsager.mafza.core.data.profile

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object EncryptedProfileRecordSerializer : Serializer<EncryptedProfileRecord> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: EncryptedProfileRecord = EncryptedProfileRecord()

    override suspend fun readFrom(input: InputStream): EncryptedProfileRecord {
        return try {
            val raw_bytes = input.readBytes()
            if (raw_bytes.isEmpty()) return defaultValue

            json.decodeFromString(EncryptedProfileRecord.serializer(), raw_bytes.decodeToString())
        } catch (serialization_exception: SerializationException) {
            throw CorruptionException(
                message = "Failed to decode encrypted profile record",
                cause = serialization_exception
            )
        }
    }

    override suspend fun writeTo(t: EncryptedProfileRecord, output: OutputStream) {
        val payload = json.encodeToString(EncryptedProfileRecord.serializer(), t)
        output.write(payload.encodeToByteArray())
    }
}
