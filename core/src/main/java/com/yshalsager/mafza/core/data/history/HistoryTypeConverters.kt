package com.yshalsager.mafza.core.data.history

import androidx.room.TypeConverter
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepStatus
import com.yshalsager.mafza.core.contracts.TriggerSource

class HistoryTypeConverters {
    @TypeConverter
    fun trigger_source_to_string(value: TriggerSource): String = value.name

    @TypeConverter
    fun string_to_trigger_source(value: String): TriggerSource = TriggerSource.valueOf(value)

    @TypeConverter
    fun execution_mode_to_string(value: ExecutionMode): String = value.name

    @TypeConverter
    fun string_to_execution_mode(value: String): ExecutionMode = ExecutionMode.valueOf(value)

    @TypeConverter
    fun run_status_to_string(value: RunStatus): String = value.name

    @TypeConverter
    fun string_to_run_status(value: String): RunStatus = RunStatus.valueOf(value)

    @TypeConverter
    fun step_status_to_string(value: StepStatus): String = value.name

    @TypeConverter
    fun string_to_step_status(value: String): StepStatus = StepStatus.valueOf(value)

    @TypeConverter
    fun action_id_to_string(value: ActionId): String = value.name

    @TypeConverter
    fun string_to_action_id(value: String): ActionId = ActionId.valueOf(value)
}
