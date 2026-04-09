package com.yshalsager.mafza.core.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert_run(run: RunHistoryEntity)

    @Query("DELETE FROM step_history WHERE run_id = :run_id")
    suspend fun delete_steps_for_run(run_id: String)

    @Query("DELETE FROM command_audit WHERE run_id = :run_id")
    suspend fun delete_command_audits_for_run(run_id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_steps(steps: List<StepHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_command_audits(command_audits: List<CommandAuditEntity>)

    @Query("SELECT run_id FROM run_history ORDER BY started_at_epoch_ms DESC")
    suspend fun all_run_ids_desc(): List<String>

    @Query("DELETE FROM run_history WHERE run_id IN (:run_ids)")
    suspend fun delete_runs_by_ids(run_ids: List<String>)

    @Query("SELECT * FROM run_history ORDER BY started_at_epoch_ms DESC LIMIT :limit")
    suspend fun latest_runs(limit: Int): List<RunHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_runs(runs: List<RunHistoryEntity>)

    @Query("DELETE FROM run_history")
    suspend fun delete_all_runs()

    @Query("SELECT * FROM run_history ORDER BY started_at_epoch_ms DESC LIMIT :limit")
    fun latest_runs_flow(limit: Int): Flow<List<RunHistoryEntity>>

    @Query("SELECT * FROM run_history WHERE run_id = :run_id LIMIT 1")
    fun run_by_id_flow(run_id: String): Flow<RunHistoryEntity?>

    @Query("SELECT * FROM step_history WHERE run_id IN (:run_ids) ORDER BY run_id ASC, step_index ASC")
    suspend fun steps_for_runs(run_ids: List<String>): List<StepHistoryEntity>

    @Query("SELECT * FROM step_history WHERE run_id = :run_id ORDER BY step_index ASC")
    fun steps_for_run_flow(run_id: String): Flow<List<StepHistoryEntity>>

    @Query("SELECT * FROM command_audit WHERE run_id IN (:run_ids) ORDER BY run_id ASC, step_index ASC, command_index ASC")
    suspend fun command_audits_for_runs(run_ids: List<String>): List<CommandAuditEntity>
}
