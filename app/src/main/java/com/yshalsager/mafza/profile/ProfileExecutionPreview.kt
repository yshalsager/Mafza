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
import com.yshalsager.mafza.core.contracts.ActionId

@Composable
internal fun ActionExecutionPreviewCard(
    sms_recipients: List<String>,
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    self_uninstall_enabled: Boolean
) {
    val preview_items = build_execution_preview_items(
        sms_recipients = sms_recipients,
        action_policy_rows = action_policy_rows,
        telegram_bot_actions = telegram_bot_actions,
        message_app_bindings = message_app_bindings,
        intent_actions = intent_actions,
        self_uninstall_enabled = self_uninstall_enabled
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
    sms_recipients: List<String>,
    action_policy_rows: List<EditableActionPolicyRow>,
    telegram_bot_actions: List<EditableTelegramBotAction>,
    message_app_bindings: List<EditableMessageBinding>,
    intent_actions: List<EditableIntentAction>,
    self_uninstall_enabled: Boolean
): List<ExecutionPreviewItem> {
    val action_policy_by_id = action_policy_rows.associateBy { it.action_id }
    var original_index = 0

    val non_policy_steps = listOf(
        PlannedExecutionPreviewStep(
            original_index = original_index++,
            policy = null,
            label = stringResource(R.string.profile_execution_preview_location_item),
            locally_enabled = true
        )
    )

    val notify_steps = buildList {
        sms_recipients.forEachIndexed { index, _ ->
            add(
                PlannedExecutionPreviewStep(
                    original_index = original_index++,
                    policy = action_policy_for(action_policy_by_id, ActionId.SEND_SMS),
                    label = stringResource(R.string.profile_execution_preview_sms_item, index + 1),
                    locally_enabled = true
                )
            )
        }

        message_app_bindings
            .filter { it.enabled && it.binding_id.trim().isNotEmpty() }
            .forEachIndexed { index, binding ->
                val package_label = binding.package_name.trim().ifEmpty {
                    stringResource(R.string.profile_execution_preview_unset_binding)
                }
                add(
                    PlannedExecutionPreviewStep(
                        original_index = original_index++,
                        policy = binding_policy(binding, index),
                        label = stringResource(R.string.profile_execution_preview_binding_item, package_label),
                        locally_enabled = true
                    )
                )
            }

        telegram_bot_actions
            .filter { it.enabled && it.id.trim().isNotEmpty() }
            .forEachIndexed { index, action ->
                val action_label = action.label.trim()
                    .ifEmpty { action.chat_id.trim() }
                    .ifEmpty { stringResource(R.string.profile_execution_preview_unset_telegram_bot) }
                add(
                    PlannedExecutionPreviewStep(
                        original_index = original_index++,
                        policy = telegram_policy(action, index),
                        label = stringResource(R.string.profile_execution_preview_telegram_item, action_label),
                        locally_enabled = true
                    )
                )
            }

        intent_actions.forEachIndexed { index, intent_action ->
            val intent_label = intent_action.label.trim()
                .ifEmpty { intent_action.intent_action.trim() }
                .ifEmpty { stringResource(R.string.profile_execution_preview_unset_intent) }
            add(
                PlannedExecutionPreviewStep(
                    original_index = original_index++,
                    policy = intent_policy(intent_action, index),
                    label = stringResource(R.string.profile_execution_preview_intent_item, intent_label),
                    locally_enabled = intent_action.enabled
                )
            )
        }
    }

    val destructive_steps = listOf(
        PlannedExecutionPreviewStep(
            original_index = original_index++,
            policy = action_policy_for(action_policy_by_id, ActionId.UNINSTALL_APPS),
            label = stringResource(action_type_label_res(ActionId.UNINSTALL_APPS)),
            locally_enabled = true
        ),
        PlannedExecutionPreviewStep(
            original_index = original_index++,
            policy = action_policy_for(action_policy_by_id, ActionId.DELETE_PATHS),
            label = stringResource(action_type_label_res(ActionId.DELETE_PATHS)),
            locally_enabled = true
        ),
        PlannedExecutionPreviewStep(
            original_index = original_index++,
            policy = action_policy_for(action_policy_by_id, ActionId.ADVANCED_SHELL_COMMANDS),
            label = stringResource(action_type_label_res(ActionId.ADVANCED_SHELL_COMMANDS)),
            locally_enabled = true
        )
    )

    val finalize_steps = listOf(
        PlannedExecutionPreviewStep(
            original_index = original_index++,
            policy = action_policy_for(action_policy_by_id, ActionId.SELF_UNINSTALL),
            label = stringResource(action_type_label_res(ActionId.SELF_UNINSTALL)),
            locally_enabled = self_uninstall_enabled
        )
    )

    val ordered_steps = non_policy_steps +
        ordered_policy_steps(notify_steps) +
        ordered_policy_steps(destructive_steps) +
        ordered_policy_steps(finalize_steps)

    return ordered_steps.mapIndexed { index, step ->
        ExecutionPreviewItem(
            execution_order = index + 1,
            label = step.label,
            enabled = step.locally_enabled && (step.policy?.enabled ?: true)
        )
    }
}

private fun ordered_policy_steps(steps: List<PlannedExecutionPreviewStep>): List<PlannedExecutionPreviewStep> {
    return steps.sortedWith(
        compareBy<PlannedExecutionPreviewStep> { it.policy?.execution_order ?: Int.MAX_VALUE }
            .thenBy { it.original_index }
    )
}

private fun action_policy_for(
    action_policy_by_id: Map<ActionId, EditableActionPolicyRow>,
    action_id: ActionId
): PreviewPolicy {
    val policy = action_policy_by_id[action_id]
    return if (policy != null) {
        PreviewPolicy(enabled = policy.enabled, execution_order = policy.execution_order)
    } else {
        PreviewPolicy(enabled = true, execution_order = Int.MAX_VALUE)
    }
}

private fun binding_policy(binding: EditableMessageBinding, fallback_order: Int): PreviewPolicy {
    return PreviewPolicy(
        enabled = binding.policy_enabled,
        execution_order = parse_int_or_fallback(
            value = binding.policy_execution_order,
            fallback = fallback_order + 1,
            min_value = PROFILE_MIN_POLICY_ORDER,
            max_value = PROFILE_MAX_POLICY_ORDER
        )
    )
}

private fun telegram_policy(action: EditableTelegramBotAction, fallback_order: Int): PreviewPolicy {
    return PreviewPolicy(
        enabled = action.policy_enabled,
        execution_order = parse_int_or_fallback(
            value = action.policy_execution_order,
            fallback = fallback_order + 1,
            min_value = PROFILE_MIN_POLICY_ORDER,
            max_value = PROFILE_MAX_POLICY_ORDER
        )
    )
}

private fun intent_policy(intent_action: EditableIntentAction, fallback_order: Int): PreviewPolicy {
    return PreviewPolicy(
        enabled = intent_action.policy_enabled,
        execution_order = parse_int_or_fallback(
            value = intent_action.policy_execution_order,
            fallback = fallback_order + 1,
            min_value = PROFILE_MIN_POLICY_ORDER,
            max_value = PROFILE_MAX_POLICY_ORDER
        )
    )
}

private data class PlannedExecutionPreviewStep(
    val original_index: Int,
    val policy: PreviewPolicy?,
    val label: String,
    val locally_enabled: Boolean
)

private data class PreviewPolicy(
    val enabled: Boolean,
    val execution_order: Int
)
