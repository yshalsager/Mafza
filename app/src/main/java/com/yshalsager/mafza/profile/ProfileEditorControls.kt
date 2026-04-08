package com.yshalsager.mafza.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R

@Composable
internal fun AdvancedExecutionRuleToggle(
    show_advanced_execution_rule: Boolean,
    on_toggle_advanced: () -> Unit,
    show_label_res_id: Int = R.string.profile_execution_rule_show_action
) {
    TextButton(
        onClick = on_toggle_advanced,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (show_advanced_execution_rule) {
                stringResource(R.string.profile_advanced_hide_action)
            } else {
                stringResource(show_label_res_id)
            }
        )
    }
}

@Composable
internal fun PolicyModeSelector(
    policy_mode: ProfilePolicyMode,
    on_select_mode: (ProfilePolicyMode) -> Unit
) {
    Text(
        text = stringResource(R.string.profile_execution_rule_mode_label),
        style = MaterialTheme.typography.bodySmall
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val inherit_selected = policy_mode == ProfilePolicyMode.INHERIT_DEFAULT
        TextButton(
            onClick = { on_select_mode(ProfilePolicyMode.INHERIT_DEFAULT) },
            modifier = Modifier.weight(1f),
            enabled = !inherit_selected
        ) {
            Text(text = stringResource(R.string.profile_execution_rule_mode_inherit))
        }
        TextButton(
            onClick = { on_select_mode(ProfilePolicyMode.OVERRIDE) },
            modifier = Modifier.weight(1f),
            enabled = inherit_selected
        ) {
            Text(text = stringResource(R.string.profile_execution_rule_mode_override))
        }
    }
}

internal inline fun <T> List<T>.update_item(index: Int, transform: (T) -> T): List<T> =
    mapIndexed { current_index, item ->
        if (current_index == index) transform(item) else item
    }
