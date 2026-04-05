package com.yshalsager.mafza.emergency.steps

import android.content.Context
import com.yshalsager.mafza.emergency.providers.ActionProviderRegistry
import com.yshalsager.mafza.emergency.providers.IntentMessageAppProvider
import com.yshalsager.mafza.emergency.shell.ShizukuCommandExecutor
import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.StepContext

object EmergencyStepsFactory {
    fun create_steps(
        app_context: Context,
        step_context: StepContext
    ): List<EmergencyStep> {
        val run_step_state = RunStepState()
        val command_executor = ShizukuCommandExecutor(app_context = app_context)
        val action_provider_registry = ActionProviderRegistry(
            providers = listOf(
                IntentMessageAppProvider(app_context = app_context)
            )
        )
        val steps = mutableListOf<EmergencyStep>()
        steps += LocationStep(
            app_context = app_context,
            run_step_state = run_step_state
        )

        step_context.profile.sms_recipients.forEachIndexed { index, recipient ->
            steps += SmsRecipientStep(
                app_context = app_context,
                run_step_state = run_step_state,
                recipient = recipient,
                recipient_index = index
            )
        }
        step_context.profile.action_bindings
            .filter { it.action_id == ActionId.NOTIFY_MESSAGE_APP && it.enabled && it.binding_id.trim().isNotEmpty() }
            .forEachIndexed { index, action_binding ->
                steps += MessageAppProviderStep(
                    app_context = app_context,
                    action_provider_registry = action_provider_registry,
                    run_step_state = run_step_state,
                    action_binding = action_binding,
                    binding_index = index
                )
            }

        step_context.profile.intent_actions.forEach { intent_action ->
            steps += LaunchIntentActionStep(
                app_context = app_context,
                intent_action_spec = intent_action
            )
        }

        steps += UninstallAppsStep(command_executor = command_executor)
        steps += DeletePathsStep(command_executor = command_executor)
        steps += AdvancedShellCommandsStep(command_executor = command_executor)
        steps += SelfUninstallStep(
            app_context = app_context,
            command_executor = command_executor
        )
        return steps
    }
}
