package com.yshalsager.mafza

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.history.RunHistoryDao
import com.yshalsager.mafza.core.data.history.RunHistoryEntity
import com.yshalsager.mafza.core.data.history.StepHistoryEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HistoryScreen(
    history_dao: RunHistoryDao,
    on_open_run_details: (String) -> Unit
) {
    val runs by history_dao.latest_runs_flow(limit = 100).collectAsStateWithLifecycle(initialValue = emptyList())
    val expanded_run_ids = remember { mutableStateListOf<String>() }

    if (runs.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(runs, key = { run -> run.run_id }) { run ->
            val is_expanded = expanded_run_ids.contains(run.run_id)
            RunHistoryRow(
                run = run,
                is_expanded = is_expanded,
                history_dao = history_dao,
                on_toggle_expand = {
                    if (is_expanded) expanded_run_ids.remove(run.run_id) else expanded_run_ids.add(run.run_id)
                },
                on_open_run_details = on_open_run_details
            )
        }
    }
}

@Composable
private fun RunHistoryRow(
    run: RunHistoryEntity,
    is_expanded: Boolean,
    history_dao: RunHistoryDao,
    on_toggle_expand: () -> Unit,
    on_open_run_details: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_toggle_expand)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.history_run_id, run.run_id.take(8)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = format_epoch_ms(run.started_at_epoch_ms),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PillBadge(text = run.mode.mode_label())
                PillBadge(text = run.trigger.trigger_label())
                PillBadge(text = run.status.run_status_label())
            }
            if (is_expanded) {
                val steps by history_dao.steps_for_run_flow(run.run_id).collectAsStateWithLifecycle(initialValue = emptyList())
                Text(
                    text = stringResource(R.string.history_steps_title),
                    style = MaterialTheme.typography.titleSmall
                )
                if (steps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_steps_empty),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    steps.forEach { step ->
                        StepCompactRow(step = step)
                    }
                }
                TextButton(onClick = { on_open_run_details(run.run_id) }) {
                    Text(text = stringResource(R.string.history_open_run_details))
                }
            }
        }
    }
}

@Composable
private fun StepCompactRow(step: StepHistoryEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = step.step_id,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        PillBadge(text = step.status.step_status_label())
    }
}

@Composable
internal fun RunDetailsScreen(
    run_id: String,
    history_dao: RunHistoryDao
) {
    val run by history_dao.run_by_id_flow(run_id).collectAsStateWithLifecycle(initialValue = null)
    val steps by history_dao.steps_for_run_flow(run_id).collectAsStateWithLifecycle(initialValue = emptyList())

    val run_entity = run
    if (run_entity == null) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.run_details_not_found),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.run_details_run_id_full, run_entity.run_id),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.run_details_trigger, run_entity.trigger.trigger_label()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.run_details_mode, run_entity.mode.mode_label()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.run_details_status, run_entity.status.run_status_label()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.run_details_started_at, format_epoch_ms(run_entity.started_at_epoch_ms)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(
                            R.string.run_details_completed_at,
                            run_entity.completed_at_epoch_ms?.let(::format_epoch_ms).orEmpty()
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.run_details_steps_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        items(steps, key = { step -> "${step.run_id}:${step.step_index}" }) { step ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.run_details_step_index, step.step_index + 1),
                            style = MaterialTheme.typography.labelLarge
                        )
                        PillBadge(text = step.status.step_status_label())
                    }
                    Text(
                        text = step.step_id,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val details = step.details
                    if (!details.isNullOrBlank()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.run_details_step_time_range,
                            format_epoch_ms(step.started_at_epoch_ms),
                            format_epoch_ms(step.finished_at_epoch_ms)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PillBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun format_epoch_ms(epoch_ms: Long): String {
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .format(Instant.ofEpochMilli(epoch_ms).atZone(ZoneId.systemDefault()))
}

@Composable
private fun ExecutionMode.mode_label(): String {
    return stringResource(
        when (this) {
            ExecutionMode.LIVE -> R.string.history_mode_live
            ExecutionMode.DRY_RUN -> R.string.history_mode_dry_run
        }
    )
}

@Composable
private fun TriggerSource.trigger_label(): String {
    return stringResource(
        when (this) {
            TriggerSource.MANUAL_IN_APP -> R.string.history_trigger_manual
            TriggerSource.SHORTCUT -> R.string.history_trigger_shortcut
            TriggerSource.WIDGET -> R.string.history_trigger_widget
            TriggerSource.QUICK_SETTINGS_TILE -> R.string.history_trigger_quick_settings_tile
        }
    )
}

@Composable
private fun RunStatus.run_status_label(): String {
    return stringResource(
        when (this) {
            RunStatus.RUNNING -> R.string.history_run_status_running
            RunStatus.CANCELLED_PRE_START -> R.string.history_run_status_cancelled_pre_start
            RunStatus.COMPLETED_SUCCESS -> R.string.history_run_status_completed_success
            RunStatus.COMPLETED_PARTIAL -> R.string.history_run_status_completed_partial
            RunStatus.COMPLETED_FAILED -> R.string.history_run_status_completed_failed
        }
    )
}

@Composable
private fun StepStatus.step_status_label(): String {
    return stringResource(
        when (this) {
            StepStatus.SUCCESS -> R.string.history_step_status_success
            StepStatus.FAILED -> R.string.history_step_status_failed
            StepStatus.TIMED_OUT -> R.string.history_step_status_timed_out
            StepStatus.SKIPPED_UNAVAILABLE -> R.string.history_step_status_skipped_unavailable
            StepStatus.SKIPPED_DRY_RUN -> R.string.history_step_status_skipped_dry_run
            StepStatus.CANCELLED_PRE_START -> R.string.history_step_status_cancelled_pre_start
        }
    )
}
