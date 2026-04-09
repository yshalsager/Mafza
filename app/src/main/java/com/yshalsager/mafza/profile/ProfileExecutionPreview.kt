package com.yshalsager.mafza.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R

@Composable
internal fun ActionExecutionPreviewCard(
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>
) {
    val preview_items = build_execution_preview_items(
        action_policy_rows = action_policy_rows,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_execution_preview_title),
                style = MaterialTheme.typography.titleSmall
            )
            if (preview_items.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_execution_preview_empty),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                preview_items.forEachIndexed { index, item ->
                    val state_label = if (item.enabled) {
                        stringResource(R.string.profile_execution_preview_enabled)
                    } else {
                        stringResource(R.string.profile_execution_preview_disabled)
                    }
                    Text(
                        text = stringResource(
                            R.string.profile_execution_preview_row,
                            index + 1,
                            item.label,
                            state_label
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun build_execution_preview_items(
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>
): List<ExecutionPreviewItem> {
    val action_items = action_policy_rows.map { policy ->
        ExecutionPreviewItem(
            execution_order = policy.execution_order,
            label = stringResource(action_type_label_res(policy.action_id)),
            enabled = policy.enabled
        )
    }

    val binding_items = message_app_bindings.mapIndexed { index, binding ->
        val package_label = binding.package_name.trim().ifEmpty {
            stringResource(R.string.profile_execution_preview_unset_binding)
        }
        ExecutionPreviewItem(
            execution_order = if (binding.policy_mode == ProfilePolicyMode.OVERRIDE) {
                parse_int_or_fallback(
                    value = binding.policy_execution_order,
                    fallback = index + 1,
                    min_value = PROFILE_MIN_POLICY_ORDER,
                    max_value = PROFILE_MAX_POLICY_ORDER
                )
            } else {
                index + 1
            },
            label = stringResource(R.string.profile_execution_preview_binding_item, package_label),
            enabled = if (binding.policy_mode == ProfilePolicyMode.OVERRIDE) {
                binding.policy_enabled
            } else {
                binding.enabled
            }
        )
    }

    val telegram_items = telegram_bot_actions.mapIndexed { index, action ->
        val action_label = action.label.trim()
            .ifEmpty { action.chat_id.trim() }
            .ifEmpty { stringResource(R.string.profile_execution_preview_unset_telegram_bot) }
        ExecutionPreviewItem(
            execution_order = if (action.policy_mode == ProfilePolicyMode.OVERRIDE) {
                parse_int_or_fallback(
                    value = action.policy_execution_order,
                    fallback = index + 1,
                    min_value = PROFILE_MIN_POLICY_ORDER,
                    max_value = PROFILE_MAX_POLICY_ORDER
                )
            } else {
                index + 1
            },
            label = stringResource(R.string.profile_execution_preview_telegram_item, action_label),
            enabled = if (action.policy_mode == ProfilePolicyMode.OVERRIDE) {
                action.policy_enabled
            } else {
                action.enabled
            }
        )
    }

    val intent_items = intent_actions.mapIndexed { index, intent_action ->
        val intent_label = intent_action.label.trim()
            .ifEmpty { intent_action.intent_action.trim() }
            .ifEmpty { stringResource(R.string.profile_execution_preview_unset_intent) }
        ExecutionPreviewItem(
            execution_order = if (intent_action.policy_mode == ProfilePolicyMode.OVERRIDE) {
                parse_int_or_fallback(
                    value = intent_action.policy_execution_order,
                    fallback = index + 1,
                    min_value = PROFILE_MIN_POLICY_ORDER,
                    max_value = PROFILE_MAX_POLICY_ORDER
                )
            } else {
                index + 1
            },
            label = stringResource(R.string.profile_execution_preview_intent_item, intent_label),
            enabled = if (intent_action.policy_mode == ProfilePolicyMode.OVERRIDE) {
                intent_action.policy_enabled
            } else {
                intent_action.enabled
            }
        )
    }

    return (action_items + telegram_items + binding_items + intent_items)
        .sortedWith(
            compareBy<ExecutionPreviewItem> { it.execution_order }
                .thenBy { it.label.lowercase() }
        )
}
