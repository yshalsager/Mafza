package com.yshalsager.mafza.emergency.telegram

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class TelegramBotCheckResult(
    val ready: Boolean,
    val details: String?
)

data class TelegramBotSendResult(
    val success: Boolean,
    val details: String?
)

interface TelegramBotClient {
    fun check_bot(
        bot_token: String,
        timeout_seconds: Int
    ): TelegramBotCheckResult

    fun send_message(
        bot_token: String,
        chat_id: String,
        text: String,
        timeout_seconds: Int
    ): TelegramBotSendResult
}

class RealTelegramBotClient(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : TelegramBotClient {
    override fun check_bot(
        bot_token: String,
        timeout_seconds: Int
    ): TelegramBotCheckResult {
        val response = execute_request(
            bot_token = bot_token,
            method_name = "getMe",
            timeout_seconds = timeout_seconds,
            body = null
        )
        if (!response.success) {
            return TelegramBotCheckResult(
                ready = false,
                details = response.details
            )
        }
        return TelegramBotCheckResult(
            ready = true,
            details = "telegram_get_me_ok"
        )
    }

    override fun send_message(
        bot_token: String,
        chat_id: String,
        text: String,
        timeout_seconds: Int
    ): TelegramBotSendResult {
        val encoded_chat_id = url_encode(chat_id)
        val encoded_text = url_encode(text)
        val body = "chat_id=$encoded_chat_id&text=$encoded_text"
        val response = execute_request(
            bot_token = bot_token,
            method_name = "sendMessage",
            timeout_seconds = timeout_seconds,
            body = body
        )
        return TelegramBotSendResult(
            success = response.success,
            details = response.details
        )
    }

    private fun execute_request(
        bot_token: String,
        method_name: String,
        timeout_seconds: Int,
        body: String?
    ): TelegramApiResponse {
        val timeout_millis = timeout_seconds.coerceIn(1, 60) * 1_000
        val endpoint = URL("https://api.telegram.org/bot$bot_token/$method_name")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout_millis
            readTimeout = timeout_millis
            useCaches = false
            requestMethod = if (body == null) "GET" else "POST"
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            }
        }
        return try {
            if (body != null) {
                connection.outputStream.use { output_stream ->
                    output_stream.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }

            val status_code = connection.responseCode
            val stream = if (status_code in 200..299) connection.inputStream else connection.errorStream
            val response_body = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            parse_response(status_code, response_body)
        } catch (throwable: Throwable) {
            val error_message = sanitize_description(throwable.message)
            TelegramApiResponse(
                success = false,
                details = if (error_message != null) {
                    "telegram_transport_error: $error_message"
                } else {
                    "telegram_transport_error"
                }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parse_response(
        status_code: Int,
        response_body: String
    ): TelegramApiResponse {
        val root = runCatching { json.parseToJsonElement(response_body).jsonObject }.getOrNull()
        val error_code = root?.get("error_code")?.jsonPrimitive?.contentOrNull
        val error_description = sanitize_description(root?.get("description")?.jsonPrimitive?.contentOrNull)

        if (status_code !in 200..299) {
            val fallback_http_code = "telegram_http_$status_code"
            val base_details = if (error_code != null) "telegram_api_error_$error_code" else fallback_http_code
            return TelegramApiResponse(
                success = false,
                details = if (error_description != null) "$base_details: $error_description" else base_details
            )
        }

        if (root == null) {
            return TelegramApiResponse(
                success = false,
                details = "telegram_invalid_json"
            )
        }
        val ok = root["ok"]?.jsonPrimitive?.booleanOrNull == true
        if (ok) {
            return TelegramApiResponse(
                success = true,
                details = "telegram_ok"
            )
        }

        val details = if (error_code != null) "telegram_api_error_$error_code" else "telegram_api_error"
        return TelegramApiResponse(
            success = false,
            details = if (error_description != null) "$details: $error_description" else details
        )
    }

    private fun url_encode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun sanitize_description(raw: String?): String? {
        val normalized = raw
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        if (normalized.isEmpty()) return null
        return normalized.take(180)
    }

    private data class TelegramApiResponse(
        val success: Boolean,
        val details: String?
    )
}
