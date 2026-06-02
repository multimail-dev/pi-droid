package dev.anthropic.pidroid.journal

/**
 * Handles crash recovery on app start.
 *
 * When the app was killed while tasks were ACTIVE, those tasks are
 * transitioned to INTERRUPTED. The UI can then offer to resume them.
 */
class CrashRecoveryManager(private val dao: TaskJournalDao) {

    /**
     * Called on app start. Marks all active tasks as interrupted.
     * @return the number of tasks that were interrupted
     */
    suspend fun onAppStart(): Int {
        val activeTasks = dao.getActiveTasks()
        if (activeTasks.isEmpty()) return 0

        dao.markAllActiveAsInterrupted()
        return activeTasks.size
    }

    /**
     * Get tasks available for resume (interrupted tasks).
     */
    suspend fun getRecoverableTasks(): List<TaskEntry> = dao.getInterruptedTasks()
}
