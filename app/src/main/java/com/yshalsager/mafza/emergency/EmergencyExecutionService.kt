package com.yshalsager.mafza.emergency

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yshalsager.mafza.R
import com.yshalsager.mafza.emergency.steps.EmergencyStepsFactory
import com.yshalsager.mafza.core.contracts.ExecutionMode
import com.yshalsager.mafza.core.contracts.RunStatus
import com.yshalsager.mafza.core.contracts.StepResult
import com.yshalsager.mafza.core.contracts.TriggerSource
import com.yshalsager.mafza.core.data.history.CommandAuditEntity
import com.yshalsager.mafza.core.data.history.MafzaHistoryDatabase
import com.yshalsager.mafza.core.data.history.RunHistoryEntity
import com.yshalsager.mafza.core.data.history.RunHistoryInsertBundle
import com.yshalsager.mafza.core.data.history.RunHistoryStore
import com.yshalsager.mafza.core.data.history.StepHistoryEntity
import com.yshalsager.mafza.core.data.profile.AndroidKeystoreProfileCipher
import com.yshalsager.mafza.core.data.profile.ProfileDataStoreFactory
import com.yshalsager.mafza.core.execution.DefaultEmergencyEngine
import com.yshalsager.mafza.core.execution.EngineEvent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class EmergencyExecutionService : Service() {
    private val service_scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var latest_run_id: String? = null
    @Volatile
    private var foreground_notification_started = false

    private val profile_store by lazy {
        ProfileDataStoreFactory.create_profile_store(
            context = applicationContext,
            profile_cipher = AndroidKeystoreProfileCipher()
        )
    }
    private val history_store by lazy {
        RunHistoryStore(
            database = MafzaHistoryDatabase.create(applicationContext)
        )
    }

    private val active_runs = ConcurrentHashMap<String, ActiveRunTrace>()

    private val emergency_engine by lazy {
        DefaultEmergencyEngine(
            scope = service_scope,
            profile_reader = { profile_store.read_profile() },
            steps_provider = { step_context ->
                EmergencyStepsFactory.create_steps(
                    app_context = applicationContext,
                    step_context = step_context
                )
            },
            on_event = ::handle_engine_event
        )
    }

    override fun onCreate() {
        super.onCreate()
        create_notification_channel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, start_id: Int): Int {
        when (intent?.action) {
            EmergencyServiceContract.ACTION_START_RUN -> {
                ensure_foreground_notification_started(getString(R.string.emergency_notification_idle))
                val trigger = parse_trigger_source(
                    intent.getStringExtra(EmergencyServiceContract.EXTRA_TRIGGER_SOURCE)
                )
                val requested_mode = parse_execution_mode(
                    intent.getStringExtra(EmergencyServiceContract.EXTRA_EXECUTION_MODE)
                )
                service_scope.launch {
                    val should_start = should_start_run(trigger)
                    if (!should_start) {
                        stopSelfResult(start_id)
                        return@launch
                    }
                    val run_id = emergency_engine.start(
                        trigger = trigger,
                        mode = requested_mode
                    )
                    latest_run_id = run_id
                }
            }

            EmergencyServiceContract.ACTION_CANCEL_RUN -> {
                val run_id = intent.getStringExtra(EmergencyServiceContract.EXTRA_RUN_ID) ?: latest_run_id
                if (run_id != null) {
                    emergency_engine.cancelWithinWindow(run_id)
                } else {
                    stopSelf()
                }
            }

            else -> stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        service_scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        foreground_notification_started = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handle_engine_event(event: EngineEvent) {
        when (event) {
            is EngineEvent.RunStarted -> {
                active_runs[event.run_id] = ActiveRunTrace(
                    trigger = event.trigger,
                    mode = event.mode,
                    started_at_epoch_ms = event.started_at_epoch_ms
                )
                update_notification(getString(R.string.emergency_notification_running))
            }

            is EngineEvent.CancelWindowOpened -> {
                update_notification(getString(R.string.emergency_notification_cancel_window))
            }

            is EngineEvent.IgnoredDuplicateTrigger -> Unit

            is EngineEvent.StepCompleted -> {
                val run_trace = active_runs[event.run_id] ?: return
                run_trace.step_results += event.step_result
            }

            is EngineEvent.RunCancelledPreStart -> {
                update_notification(getString(R.string.emergency_notification_cancelled))
            }

            is EngineEvent.RunCompleted -> {
                val run_trace = active_runs.remove(event.run_id)
                val completed_run_id = event.run_id
                service_scope.launch {
                    try {
                        if (run_trace != null) {
                            persist_run_history(event.run_id, run_trace, event.run_status, event.completed_at_epoch_ms)
                        }
                    } catch (throwable: Throwable) {
                        Log.e(LOG_TAG, "Failed to persist run history for run_id=${event.run_id}", throwable)
                    } finally {
                        val should_stop_service = completed_run_id == latest_run_id && active_runs.isEmpty()
                        if (should_stop_service) {
                            runCatching { update_notification(getString(R.string.emergency_notification_idle)) }
                            stopSelf()
                        }
                    }
                }
            }
        }
    }

    private suspend fun persist_run_history(
        run_id: String,
        run_trace: ActiveRunTrace,
        run_status: RunStatus,
        completed_at_epoch_ms: Long
    ) {
        val run_entity = RunHistoryEntity(
            run_id = run_id,
            started_at_epoch_ms = run_trace.started_at_epoch_ms,
            completed_at_epoch_ms = completed_at_epoch_ms,
            trigger = run_trace.trigger,
            mode = run_trace.mode,
            status = run_status
        )

        val step_entities = run_trace.step_results.mapIndexed { index, step_result ->
            step_result.to_step_history_entity(run_id = run_id, step_index = index)
        }

        val bundle = RunHistoryInsertBundle(
            run = run_entity,
            steps = step_entities,
            command_audits = emptyList<CommandAuditEntity>()
        )
        history_store.insert_with_retention(bundle)
    }

    private fun StepResult.to_step_history_entity(run_id: String, step_index: Int): StepHistoryEntity {
        return StepHistoryEntity(
            run_id = run_id,
            step_index = step_index,
            step_id = step_id,
            status = status,
            details = details,
            started_at_epoch_ms = started_at_epoch_ms,
            finished_at_epoch_ms = finished_at_epoch_ms
        )
    }

    private fun parse_trigger_source(raw: String?): TriggerSource {
        return runCatching { TriggerSource.valueOf(raw.orEmpty()) }
            .getOrDefault(TriggerSource.MANUAL_IN_APP)
    }

    private fun parse_execution_mode(raw: String?): ExecutionMode {
        return runCatching { ExecutionMode.valueOf(raw.orEmpty()) }
            .getOrDefault(ExecutionMode.LIVE)
    }

    private suspend fun should_start_run(trigger: TriggerSource): Boolean {
        if (trigger == TriggerSource.MANUAL_IN_APP) return true
        return runCatching { profile_store.read_profile().triggers_enabled }
            .getOrDefault(true)
    }

    private fun create_notification_channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.emergency_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.emergency_notification_channel_description)
        }
        val notification_manager = getSystemService(NotificationManager::class.java)
        notification_manager.createNotificationChannel(channel)
    }

    private fun start_foreground_notification(content_text: String) {
        val notification = build_notification(content_text)
        startForeground(NOTIFICATION_ID, notification)
        foreground_notification_started = true
    }

    private fun ensure_foreground_notification_started(content_text: String) {
        if (foreground_notification_started) return
        start_foreground_notification(content_text)
    }

    private fun update_notification(content_text: String) {
        val notification_manager = getSystemService(NotificationManager::class.java)
        notification_manager.notify(NOTIFICATION_ID, build_notification(content_text))
    }

    private fun build_notification(content_text: String) = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle(getString(R.string.emergency_notification_title))
        .setContentText(content_text)
        .setOngoing(true)
        .build()

    private data class ActiveRunTrace(
        val trigger: TriggerSource,
        val mode: ExecutionMode,
        val started_at_epoch_ms: Long,
        val step_results: MutableList<StepResult> = mutableListOf()
    )

    companion object {
        private const val LOG_TAG = "EmergencyExecService"
        private const val NOTIFICATION_CHANNEL_ID = "mafza_emergency_execution"
        private const val NOTIFICATION_ID = 1001
    }
}
