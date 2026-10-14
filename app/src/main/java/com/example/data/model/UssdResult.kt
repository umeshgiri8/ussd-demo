package com.example.data.model

data class UssdResult(
    val id: Long = 0,
    val code: String,
    val responseMessage: String,
    val isSuccess: Boolean,
    val failureReason: String? = null,
    val executionType: ExecutionType = ExecutionType.AUTOMATIC_IN_APP,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0,
    val simName: String = ""
)

enum class ExecutionType(val displayName: String) {
    AUTOMATIC_IN_APP("In-App Telephony Intercept"),
    DIALER_FALLBACK("Phone App Dialer"),
    SIMULATION("Test Simulation Mode")
}
