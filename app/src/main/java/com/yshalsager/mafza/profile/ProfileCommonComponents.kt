package com.yshalsager.mafza.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R

@Composable
internal fun ExecutionRuleEditor(
    rule: EditableActionPolicyRow,
    on_update: (EditableActionPolicyRow) -> Unit
) {
    val order_error = int_range_error(
        value = rule.execution_order.toString(),
        min_value = PROFILE_MIN_POLICY_ORDER,
        max_value = PROFILE_MAX_POLICY_ORDER
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_execution_rule_enabled_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = rule.enabled,
            onCheckedChange = { enabled ->
                on_update(rule.copy(enabled = enabled))
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_execution_rule_required_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = rule.required,
            onCheckedChange = { required ->
                on_update(rule.copy(required = required))
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.profile_execution_rule_continue_label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = rule.continue_on_failure,
            onCheckedChange = { continue_on_failure ->
                on_update(rule.copy(continue_on_failure = continue_on_failure))
            }
        )
    }
    OutlinedTextField(
        value = rule.execution_order.toString(),
        onValueChange = { value ->
            val parsed = value.trim().toIntOrNull() ?: rule.execution_order
            on_update(rule.copy(execution_order = parsed))
        },
        label = { Text(text = stringResource(R.string.profile_execution_rule_order_label)) },
        modifier = Modifier.fillMaxWidth(),
        isError = order_error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            if (order_error != null) {
                Text(text = order_error)
            }
        },
        singleLine = true
    )
}

@Composable
internal fun CollapsibleInlineSection(
    title: String,
    summary: String,
    initially_expanded: Boolean,
    expanded: Boolean? = null,
    on_expanded_change: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var local_expanded by remember(initially_expanded) { mutableStateOf(initially_expanded) }
    val is_expanded = expanded ?: local_expanded

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = {
                    val next_expanded = !is_expanded
                    if (expanded != null) {
                        on_expanded_change?.invoke(next_expanded)
                    } else {
                        local_expanded = next_expanded
                        on_expanded_change?.invoke(next_expanded)
                    }
                }
            ) {
                Text(
                    text = if (is_expanded) {
                        stringResource(R.string.profile_section_hide_action)
                    } else {
                        stringResource(R.string.profile_section_show_action)
                    }
                )
            }
        }

        if (is_expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun ProfileHintCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        )
    }
}

@Composable
internal fun ProfileEmptyActionsOnboardingCard(
    on_add_first_action: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.profile_onboarding_empty_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.profile_onboarding_empty_body),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.profile_onboarding_empty_step_1),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.profile_onboarding_empty_step_2),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.profile_onboarding_empty_step_3),
                style = MaterialTheme.typography.bodySmall
            )
            FilledTonalButton(
                onClick = on_add_first_action,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.profile_onboarding_add_first_action))
            }
        }
    }
}

@Composable
internal fun ProfileSaveBar(
    has_unsaved_changes: Boolean,
    is_saving: Boolean,
    can_save: Boolean,
    saved_successfully: Boolean,
    save_error_message: String?,
    on_discard: () -> Unit,
    on_save: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = on_discard,
                    enabled = has_unsaved_changes && !is_saving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.profile_discard_action))
                }
                Button(
                    onClick = on_save,
                    enabled = can_save && has_unsaved_changes,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (is_saving) {
                            stringResource(R.string.profile_save_in_progress)
                        } else {
                            stringResource(R.string.profile_save_action)
                        }
                    )
                }
            }

            Text(
                text = if (has_unsaved_changes) {
                    stringResource(R.string.profile_unsaved_changes)
                } else {
                    stringResource(R.string.profile_no_pending_changes)
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (saved_successfully) {
                Text(
                    text = stringResource(R.string.profile_saved_successfully),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (save_error_message != null) {
                Text(
                    text = stringResource(R.string.profile_save_error_prefix) + save_error_message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
internal fun TimeoutField(
    label: String,
    value: String,
    error_message: String?,
    on_value_change: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = on_value_change,
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        isError = error_message != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            if (error_message != null) {
                Text(text = error_message)
            }
        },
        singleLine = true
    )
}

@Composable
internal fun CollapsibleSectionCard(
    title: String,
    summary: String,
    initially_expanded: Boolean,
    expanded: Boolean? = null,
    on_expanded_change: ((Boolean) -> Unit)? = null,
    is_critical: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var local_expanded by remember(initially_expanded) { mutableStateOf(initially_expanded) }
    val is_expanded = expanded ?: local_expanded
    val container_color = if (is_critical) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container_color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(
                    onClick = {
                        val next_expanded = !is_expanded
                        if (expanded != null) {
                            on_expanded_change?.invoke(next_expanded)
                        } else {
                            local_expanded = next_expanded
                            on_expanded_change?.invoke(next_expanded)
                        }
                    }
                ) {
                    Text(
                        text = if (is_expanded) {
                            stringResource(R.string.profile_section_hide_action)
                        } else {
                            stringResource(R.string.profile_section_show_action)
                        }
                    )
                }
            }

            if (is_expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun ToggleField(
    title: String,
    checked: Boolean,
    on_checked_change: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = on_checked_change
            )
        }
    }
}

internal data class AppChooserOption(
    val label: String,
    val package_name: String,
    val activity_name: String? = null
)

@Composable
internal fun SearchableAppChooserDialog(
    title: String,
    options: List<AppChooserOption>,
    on_select: (AppChooserOption) -> Unit,
    on_dismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalized_query = query.trim()
    val filtered_options = options.filter { option ->
        normalized_query.isEmpty() ||
            option.label.contains(normalized_query, ignoreCase = true) ||
            option.package_name.contains(normalized_query, ignoreCase = true) ||
            (option.activity_name?.contains(normalized_query, ignoreCase = true) == true)
    }

    AlertDialog(
        onDismissRequest = on_dismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(text = stringResource(R.string.profile_picker_search_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (filtered_options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_picker_no_results),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = filtered_options,
                            key = { option -> "${option.package_name}:${option.activity_name.orEmpty()}" }
                        ) { option ->
                            val option_detail = option.activity_name
                                ?.takeIf { it.isNotBlank() }
                                ?.let { "${option.package_name} • $it" }
                                ?: option.package_name
                            TextButton(
                                onClick = { on_select(option) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = option_detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = on_dismiss) {
                Text(text = stringResource(R.string.run_live_cancel_action))
            }
        }
    )
}
