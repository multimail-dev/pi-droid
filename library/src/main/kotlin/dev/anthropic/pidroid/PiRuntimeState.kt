package dev.anthropic.pidroid

/**
 * Observable state of the Pi runtime.
 */
data class PiRuntimeState(
    val status: RuntimeStatus = RuntimeStatus.IDLE,
    val currentTaskId: String? = null,
    val turnCount: Int = 0,
    val lastError: String? = null,
)

enum class RuntimeStatus {
    /** Runtime is initialized but no agent task is running */
    IDLE,
    /** Agent loop is actively processing (streaming, executing tools) */
    PROCESSING,
    /** Agent is waiting for user confirmation on a tool call */
    AWAITING_CONFIRMATION,
    /** Runtime is shutting down */
    SHUTTING_DOWN,
}
