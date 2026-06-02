package dev.anthropic.pidroid.journal

/**
 * Manages task lifecycle transitions and crash recovery.
 */
class TaskResumeManager(private val dao: TaskJournalDao) {

    /**
     * Create a new task.
     */
    suspend fun createTask(id: String, prompt: String, expiresAt: Long? = null): TaskEntry {
        val task = TaskEntry(
            id = id,
            prompt = prompt,
            status = TaskStatus.CREATED,
            expiresAt = expiresAt,
        )
        dao.insert(task)
        return task
    }

    /**
     * Transition a task to a new status.
     * @return the updated task, or null if the transition is invalid
     */
    suspend fun transition(taskId: String, newStatus: TaskStatus, errorMessage: String? = null): TaskEntry? {
        val task = dao.getById(taskId) ?: return null
        if (!TaskStatus.isValidTransition(task.status, newStatus)) return null

        val updated = task.copy(
            status = newStatus,
            updatedAt = System.currentTimeMillis(),
            errorMessage = errorMessage ?: task.errorMessage,
        )
        dao.update(updated)
        return updated
    }

    /**
     * Checkpoint a task's progress.
     */
    suspend fun checkpoint(taskId: String, stepIndex: Int, stepStateJson: String?): TaskEntry? {
        val task = dao.getById(taskId) ?: return null
        if (task.status != TaskStatus.ACTIVE) return null

        val updated = task.copy(
            stepIndex = stepIndex,
            stepStateJson = stepStateJson,
            updatedAt = System.currentTimeMillis(),
        )
        dao.update(updated)
        return updated
    }

    /**
     * Get tasks that can be resumed (interrupted + parked).
     */
    suspend fun getResumableTasks(): List<TaskEntry> = dao.getDashboardTasks()

    /**
     * Get the interrupted tasks for crash recovery.
     */
    suspend fun getInterruptedTasks(): List<TaskEntry> = dao.getInterruptedTasks()

    /**
     * Clean up expired terminal tasks.
     */
    suspend fun garbageCollect() {
        dao.deleteExpired()
    }
}
