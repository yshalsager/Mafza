package com.yshalsager.mafza.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ProfileAddActionSheet(
    show: Boolean,
    selected_group: ProfileActionGroup?,
    recent_action_types: List<ProfileActionRowType>,
    self_uninstall_enabled: Boolean,
    on_dismiss: () -> Unit,
    on_select_group: (ProfileActionGroup) -> Unit,
    on_select_action_type: (ProfileActionRowType) -> Unit,
    on_back_to_groups: () -> Unit
) {
    if (!show) return

    ModalBottomSheet(onDismissRequest = on_dismiss) {
        if (selected_group == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_add_action_choose_group_title),
                    style = MaterialTheme.typography.titleMedium
                )
                ProfileActionGroup.entries.forEach { group ->
                    Button(
                        onClick = { on_select_group(group) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(profile_action_group_label_res(group)))
                    }
                }
            }
        } else {
            val available_types = profile_action_types_for_group(selected_group)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.profile_add_action_choose_type_title),
                    style = MaterialTheme.typography.titleMedium
                )
                if (recent_action_types.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_add_action_recent_label),
                        style = MaterialTheme.typography.bodySmall
                    )
                    recent_action_types
                        .filter { recent_type -> recent_type in available_types }
                        .forEach { recent_type ->
                            val disabled = recent_type == ProfileActionRowType.SELF_UNINSTALL && self_uninstall_enabled
                            TextButton(
                                onClick = { on_select_action_type(recent_type) },
                                enabled = !disabled,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(profile_action_row_type_label_res(recent_type)))
                            }
                        }
                }
                available_types.forEach { row_type ->
                    val disabled = row_type == ProfileActionRowType.SELF_UNINSTALL && self_uninstall_enabled
                    Button(
                        onClick = { on_select_action_type(row_type) },
                        enabled = !disabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(profile_action_row_type_label_res(row_type)))
                    }
                }
                TextButton(
                    onClick = on_back_to_groups,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.profile_add_action_back_action))
                }
            }
        }
    }
}
