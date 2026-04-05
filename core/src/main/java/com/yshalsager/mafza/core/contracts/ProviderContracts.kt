package com.yshalsager.mafza.core.contracts

import kotlinx.serialization.Serializable

interface ActionProvider {
    fun actionId(): ActionId
    fun isAvailable(binding: ActionBinding): Boolean
    fun capabilities(binding: ActionBinding): ProviderCapabilities
    suspend fun preflight(binding: ActionBinding): ProviderPreflightResult
    suspend fun execute(request: ProviderRequest): ProviderExecutionResult
}

@Serializable
data class ProviderCapabilities(
    val supports_template: Boolean,
    val supports_target: Boolean,
    val supports_auto_send: Boolean
)

data class ProviderPreflightResult(
    val ready: Boolean,
    val blocking_reason: String? = null,
    val warnings: List<String> = emptyList()
)

data class ProviderRequest(
    val run_id: RunId,
    val mode: ExecutionMode,
    val action_binding: ActionBinding,
    val notify_target: String,
    val rendered_message: String,
    val timeout_seconds: Int
)

data class ProviderExecutionResult(
    val status: StepStatus,
    val details: String? = null
)
