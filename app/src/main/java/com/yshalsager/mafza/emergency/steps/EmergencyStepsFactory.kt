package com.yshalsager.mafza.emergency.steps

import android.content.Context
import com.yshalsager.mafza.core.contracts.EmergencyStep
import com.yshalsager.mafza.core.contracts.StepContext

object EmergencyStepsFactory {
    fun create_steps(
        app_context: Context,
        step_context: StepContext
    ): List<EmergencyStep> {
        val run_step_state = RunStepState()
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

        step_context.profile.intent_actions.forEach { intent_action ->
            steps += LaunchIntentActionStep(
                app_context = app_context,
                intent_action_spec = intent_action
            )
        }
        return steps
    }
}
