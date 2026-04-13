package com.yshalsager.mafza.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileMappingsOrderAndTimeoutTest {
    @Test
    fun `moving message binding keeps override policy execution order`() {
        val moved = move_message_binding(
            bindings = listOf(
                EditableMessageBinding(
                    binding_id = "a",
                    package_name = "com.a",
                    activity_name = "",
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "40",
                    policy_mode = ProfilePolicyMode.OVERRIDE
                ),
                EditableMessageBinding(
                    binding_id = "b",
                    package_name = "com.b",
                    activity_name = "",
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "1",
                    policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
                )
            ),
            from_index = 0,
            to_index = 1
        )

        val moved_override = moved.first { it.binding_id == "a" }
        assertEquals("40", moved_override.policy_execution_order)
    }

    @Test
    fun `moving telegram action keeps override policy execution order`() {
        val moved = move_telegram_bot_action(
            actions = listOf(
                EditableTelegramBotAction(
                    id = "a",
                    label = "a",
                    bot_token = "123456:abcdefghijklmnopqrstuvwxyz",
                    chat_id = "12345",
                    template_override = "",
                    timeout_seconds = "20",
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "55",
                    policy_mode = ProfilePolicyMode.OVERRIDE
                ),
                EditableTelegramBotAction(
                    id = "b",
                    label = "b",
                    bot_token = "123456:abcdefghijklmnopqrstuvwxyz",
                    chat_id = "12345",
                    template_override = "",
                    timeout_seconds = "20",
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "1",
                    policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
                )
            ),
            from_index = 0,
            to_index = 1
        )

        val moved_override = moved.first { it.id == "a" }
        assertEquals("55", moved_override.policy_execution_order)
    }

    @Test
    fun `moving intent action keeps override policy execution order`() {
        val moved = move_intent_action(
            intent_actions = listOf(
                EditableIntentAction(
                    id = "a",
                    label = "A",
                    intent_action = "android.intent.action.VIEW",
                    data_uri = "",
                    mime_type = "",
                    categories_multiline = "",
                    package_name = "",
                    activity_name = "",
                    extras_json = "",
                    flags_multiline = "",
                    timeout_seconds = "0",
                    continue_on_failure = true,
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "77",
                    policy_mode = ProfilePolicyMode.OVERRIDE
                ),
                EditableIntentAction(
                    id = "b",
                    label = "B",
                    intent_action = "android.intent.action.VIEW",
                    data_uri = "",
                    mime_type = "",
                    categories_multiline = "",
                    package_name = "",
                    activity_name = "",
                    extras_json = "",
                    flags_multiline = "",
                    timeout_seconds = "0",
                    continue_on_failure = true,
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "1",
                    policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
                )
            ),
            from_index = 0,
            to_index = 1
        )

        val moved_override = moved.first { it.id == "a" }
        assertEquals("77", moved_override.policy_execution_order)
    }

    @Test
    fun `new intent action defaults to global timeout inheritance`() {
        val action = create_empty_intent_action(default_execution_order = 1)
        assertEquals("0", action.timeout_seconds)
    }

    @Test
    fun `building profile intents keeps zero timeout for global fallback`() {
        val spec = build_profile_intent_actions(
            intent_actions = listOf(
                EditableIntentAction(
                    id = "intent_1",
                    label = "Open",
                    intent_action = "android.intent.action.VIEW",
                    data_uri = "",
                    mime_type = "",
                    categories_multiline = "",
                    package_name = "",
                    activity_name = "",
                    extras_json = "",
                    flags_multiline = "",
                    timeout_seconds = "0",
                    continue_on_failure = true,
                    enabled = true,
                    policy_enabled = true,
                    policy_required = false,
                    policy_continue_on_failure = true,
                    policy_execution_order = "1",
                    policy_mode = ProfilePolicyMode.INHERIT_DEFAULT
                )
            )
        ).single()

        assertEquals(0, spec.timeout_seconds)
    }
}
