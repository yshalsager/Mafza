package com.yshalsager.mafza.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yshalsager.mafza.R

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileActionGroupHeader(
    title: String,
    expanded: Boolean,
    on_toggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = on_toggle) {
                Text(
                    text = if (expanded) {
                        stringResource(R.string.profile_section_hide_action)
                    } else {
                        stringResource(R.string.profile_section_show_action)
                    }
                )
            }
        }
    }
}

@Composable
internal fun ProfileActionRowCard(
    title: String,
    summary: String,
    branch_label: String,
    lane_label: String,
    can_move_up: Boolean,
    can_move_down: Boolean,
    can_move_top: Boolean,
    can_move_bottom: Boolean,
    on_edit: () -> Unit,
    on_move_up: () -> Unit,
    on_move_down: () -> Unit,
    on_move_top: () -> Unit,
    on_move_bottom: () -> Unit,
    on_remove: () -> Unit
) {
    var show_overflow by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$branch_label • $lane_label",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { show_overflow = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.profile_action_row_menu_action)
                        )
                    }
                    DropdownMenu(
                        expanded = show_overflow,
                        onDismissRequest = { show_overflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.profile_action_row_move_top_action)) },
                            onClick = {
                                show_overflow = false
                                on_move_top()
                            },
                            enabled = can_move_top
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.profile_action_row_move_bottom_action)) },
                            onClick = {
                                show_overflow = false
                                on_move_bottom()
                            },
                            enabled = can_move_bottom
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.profile_action_row_remove_action)) },
                            onClick = {
                                show_overflow = false
                                on_remove()
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = on_edit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.profile_action_row_edit_action))
                }
                TextButton(
                    onClick = on_move_up,
                    modifier = Modifier.weight(1f),
                    enabled = can_move_up
                ) {
                    Text(text = stringResource(R.string.profile_action_row_move_up_action))
                }
                TextButton(
                    onClick = on_move_down,
                    modifier = Modifier.weight(1f),
                    enabled = can_move_down
                ) {
                    Text(text = stringResource(R.string.profile_action_row_move_down_action))
                }
            }
        }
    }
}
