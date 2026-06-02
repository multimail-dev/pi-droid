package dev.anthropic.pidroid.journal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskStatusTest {

    @Test
    fun `CREATED can transition to ACTIVE`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.CREATED, TaskStatus.ACTIVE))
    }

    @Test
    fun `CREATED can transition to DISCARDED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.CREATED, TaskStatus.DISCARDED))
    }

    @Test
    fun `ACTIVE can transition to COMPLETED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.ACTIVE, TaskStatus.COMPLETED))
    }

    @Test
    fun `ACTIVE can transition to FAILED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.ACTIVE, TaskStatus.FAILED))
    }

    @Test
    fun `ACTIVE can transition to INTERRUPTED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.ACTIVE, TaskStatus.INTERRUPTED))
    }

    @Test
    fun `ACTIVE can transition to PARKED`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.ACTIVE, TaskStatus.PARKED))
    }

    @Test
    fun `INTERRUPTED can transition to RESUMING`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.INTERRUPTED, TaskStatus.RESUMING))
    }

    @Test
    fun `RESUMING can transition to ACTIVE`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.RESUMING, TaskStatus.ACTIVE))
    }

    @Test
    fun `PARKED can transition to ACTIVE`() {
        assertTrue(TaskStatus.isValidTransition(TaskStatus.PARKED, TaskStatus.ACTIVE))
    }

    @Test
    fun `COMPLETED cannot transition to ACTIVE (terminal)`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.COMPLETED, TaskStatus.ACTIVE))
    }

    @Test
    fun `FAILED cannot transition to ACTIVE (terminal)`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.FAILED, TaskStatus.ACTIVE))
    }

    @Test
    fun `CREATED cannot transition to COMPLETED directly`() {
        assertFalse(TaskStatus.isValidTransition(TaskStatus.CREATED, TaskStatus.COMPLETED))
    }

    @Test
    fun `TERMINAL_STATUSES contains COMPLETED, FAILED, DISCARDED`() {
        assertTrue(TaskStatus.COMPLETED in TaskStatus.TERMINAL_STATUSES)
        assertTrue(TaskStatus.FAILED in TaskStatus.TERMINAL_STATUSES)
        assertTrue(TaskStatus.DISCARDED in TaskStatus.TERMINAL_STATUSES)
        assertFalse(TaskStatus.ACTIVE in TaskStatus.TERMINAL_STATUSES)
    }
}
