package com.yshalsager.mafza.emergency.location

import com.yshalsager.mafza.emergency.steps.CellSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class OpenCellIdLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy_meters: Float?
)

interface OpenCellIdLookupClient {
    suspend fun lookup(
        cell_snapshot: CellSnapshot,
        api_key: String,
        timeout_seconds: Int
    ): OpenCellIdLocation?
}

class RealOpenCellIdLookupClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : OpenCellIdLookupClient {
    override suspend fun lookup(
        cell_snapshot: CellSnapshot,
        api_key: String,
        timeout_seconds: Int
    ): OpenCellIdLocation? = withContext(Dispatchers.IO) {
        val key = api_key.trim()
        val mcc = cell_snapshot.mcc?.trim().orEmpty()
        val mnc = cell_snapshot.mnc?.trim().orEmpty()
        val lac = cell_snapshot.area_code?.trim().orEmpty()
        val cell_id = cell_snapshot.cell_id?.trim().orEmpty()
        if (key.isEmpty() || mcc.isEmpty() || mnc.isEmpty() || lac.isEmpty() || cell_id.isEmpty()) return@withContext null

        val radio = map_radio_type_for_opencellid(cell_snapshot.radio_type)
        val query_params = linkedMapOf(
            "key" to key,
            "mcc" to mcc,
            "mnc" to mnc,
            "lac" to lac,
            "cellid" to cell_id,
            "format" to "json"
        ).apply {
            if (radio != null) put("radio", radio)
        }
        val query = query_params.entries.joinToString("&") { (param, value) ->
            "${url_encode(param)}=${url_encode(value)}"
        }
        val timeout_millis = timeout_seconds.coerceIn(1, 60) * 1_000
        val endpoint = URL("https://opencellid.org/cell/get?$query")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout_millis
            readTimeout = timeout_millis
            useCaches = false
            requestMethod = "GET"
        }

        return@withContext try {
            val status_code = connection.responseCode
            if (status_code !in 200..299) {
                null
            } else {
                val body = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
                val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                val latitude = root?.get("lat")?.jsonPrimitive?.doubleOrNull
                val longitude = root?.get("lon")?.jsonPrimitive?.doubleOrNull
                if (latitude == null || longitude == null) {
                    null
                } else {
                    val accuracy_meters = root["range"]?.jsonPrimitive?.doubleOrNull?.toFloat()
                    OpenCellIdLocation(
                        latitude = latitude,
                        longitude = longitude,
                        accuracy_meters = accuracy_meters
                    )
                }
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun url_encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun map_radio_type_for_opencellid(raw_radio_type: String?): String? {
        return when (raw_radio_type?.trim()?.lowercase()) {
            "gsm" -> "GSM"
            "wcdma", "tdscdma" -> "UMTS"
            "lte" -> "LTE"
            "nr" -> "NR"
            "cdma" -> "CDMA"
            else -> null
        }
    }
}
