package com.yshalsager.mafza.profile

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yshalsager.mafza.ui.theme.MafzaTheme
import org.junit.Rule
import org.junit.Test

class ProfileTelegramBotEditorSectionTest {
    @get:Rule
    val compose_rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun telegram_error_details_are_displayed_in_test_result() {
        val action = create_empty_telegram_bot_action(default_execution_order = 1)
        val row = TelegramBotActionRow(
            row_id = "telegram:${action.id}",
            ui_order = 1,
            item_index = 0,
            telegram_bot_action = action
        )

        compose_rule.setContent {
            MafzaTheme {
                TelegramBotActionEditorSection(
                    row = row,
                    telegram_bot_actions = listOf(action),
                    on_update_telegram_bot_actions = {},
                    test_status = "error:telegram_api_error_400: Bad Request: chat not found",
                    on_test_bot = {},
                    on_clear_test_status = {},
                    show_advanced_execution_rule = false,
                    on_toggle_advanced = {},
                    on_mark_profile_dirty = {}
                )
            }
        }

        compose_rule.onNodeWithText("Bad Request: chat not found", substring = true).assertIsDisplayed()
    }
}
