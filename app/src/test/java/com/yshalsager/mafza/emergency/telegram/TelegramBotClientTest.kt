package com.yshalsager.mafza.emergency.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramBotClientTest {
    @Test
    fun `parse_response includes telegram description on http error`() {
        val client = RealTelegramBotClient()

        val parsed = invoke_parse_response(
            client = client,
            status_code = 400,
            response_body = """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""
        )

        assertFalse(parsed.success)
        assertEquals("telegram_api_error_400: Bad Request: chat not found", parsed.details)
    }

    @Test
    fun `parse_response falls back to http code when error body is not json`() {
        val client = RealTelegramBotClient()

        val parsed = invoke_parse_response(
            client = client,
            status_code = 403,
            response_body = "forbidden"
        )

        assertFalse(parsed.success)
        assertEquals("telegram_http_403", parsed.details)
    }

    @Test
    fun `parse_response includes description when ok is false on success status`() {
        val client = RealTelegramBotClient()

        val parsed = invoke_parse_response(
            client = client,
            status_code = 200,
            response_body = """{"ok":false,"description":"Bad Request: chat not found"}"""
        )

        assertFalse(parsed.success)
        assertEquals("telegram_api_error: Bad Request: chat not found", parsed.details)
    }

    @Test
    fun `parse_response returns success when ok is true`() {
        val client = RealTelegramBotClient()

        val parsed = invoke_parse_response(
            client = client,
            status_code = 200,
            response_body = """{"ok":true,"result":{"id":1}}"""
        )

        assertTrue(parsed.success)
        assertEquals("telegram_ok", parsed.details)
    }

    private fun invoke_parse_response(
        client: RealTelegramBotClient,
        status_code: Int,
        response_body: String
    ): ParsedResponse {
        val parse_method = RealTelegramBotClient::class.java.getDeclaredMethod(
            "parse_response",
            Int::class.javaPrimitiveType,
            String::class.java
        )
        parse_method.isAccessible = true
        val response = parse_method.invoke(client, status_code, response_body)!!
        val response_class = response.javaClass
        val success = response_class.getDeclaredMethod("getSuccess").invoke(response) as Boolean
        val details = response_class.getDeclaredMethod("getDetails").invoke(response) as String?
        return ParsedResponse(success = success, details = details)
    }

    private data class ParsedResponse(
        val success: Boolean,
        val details: String?
    )
}
