package com.yshalsager.mafza.profile

internal enum class ProfileValidationIssueKey {
    CANCEL_WINDOW,
    LOCATION_TIMEOUT,
    SMS_TIMEOUT,
    INTENT_TIMEOUT,
    SMS_RECIPIENTS_REQUIRED,
    UNINSTALL_REQUIRED,
    UNINSTALL_INVALID,
    DELETE_REQUIRED,
    DELETE_INVALID,
    SHELL_REQUIRED,
    SHELL_TIMEOUT_INVALID,
    SHELL_PAYLOAD_INVALID,
    BINDING_PACKAGE_INVALID,
    BINDING_ORDER_INVALID,
    INTENT_STEP_TIMEOUT_INVALID,
    INTENT_ORDER_INVALID
}

internal data class ProfileValidationIssue(
    val key: ProfileValidationIssueKey,
    val message: String
)
