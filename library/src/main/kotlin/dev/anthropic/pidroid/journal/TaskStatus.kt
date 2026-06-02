package dev.anthropic.pidroid.journal

/**
 * Status state machine for agent tasks.
 *
 * Valid transitions:
 * - CREATED → ACTIVE
 * - ACTIVE → COMPLETED, FAILED, INTERRUPTED, PARKED
 * - INTERRUPTED → RESUMING
 * - RESUMING → ACTIVE
 * - PARKED → ACTIVE
 *
 * Terminal statuses: COMPLETED, FAILED, DISCARDED
 */
enum class TaskStatus {
    CREATED,
    ACTIVE,
    COMPLETED,
    FAILED,
    INTERRUPTED,
    RESUMING,
    PARKED,
    DISCARDED;

    companion object {
        val TERMINAL_STATUSES = setOf(COMPLETED, FAILED, DISCARDED)

        val VALID_TRANSITIONS: Map<TaskStatus, Set<TaskStatus>> = mapOf(
            CREATED to setOf(ACTIVE, DISCARDED),
            ACTIVE to setOf(COMPLETED, FAILED, INTERRUPTED, PARKED),
            INTERRUPTED to setOf(RESUMING, DISCARDED),
            RESUMING to setOf(ACTIVE, FAILED),
            PARKED to setOf(ACTIVE, DISCARDED),
        )

        fun isValidTransition(from: TaskStatus, to: TaskStatus): Boolean {
            return VALID_TRANSITIONS[from]?.contains(to) ?: false
        }
    }
}
