package com.yshalsager.mafza.emergency.providers

import com.yshalsager.mafza.core.contracts.ActionId
import com.yshalsager.mafza.core.contracts.ActionProvider

class ActionProviderRegistry(
    providers: List<ActionProvider>
) {
    private val providers_by_action_id = providers.associateBy { it.actionId() }

    fun provider_for(action_id: ActionId): ActionProvider? = providers_by_action_id[action_id]
}
