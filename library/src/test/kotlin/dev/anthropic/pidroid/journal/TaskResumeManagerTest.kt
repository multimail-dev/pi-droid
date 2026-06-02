package dev.anthropic.pidroid.journal

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TaskResumeManagerTest {
    private lateinit var db: TaskJournalDatabase
    private lateinit var dao: TaskJournalDao
    private lateinit var manager: TaskResumeManager

    @Before
    fun setup() {
        db = TaskJournalDatabase.createInMemory(RuntimeEnvironment.getApplication())
        dao = db.taskDao()
        manager = TaskResumeManager(dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `createTask creates with CREATED status`() = runTest {
        val task = manager.createTask("t1", "Test prompt")
        assertEquals(TaskStatus.CREATED, task.status)
        assertEquals("Test prompt", task.prompt)
    }

    @Test
    fun `transition CREATED to ACTIVE succeeds`() = runTest {
        manager.createTask("t1", "Test")
        val result = manager.transition("t1", TaskStatus.ACTIVE)
        assertNotNull(result)
        assertEquals(TaskStatus.ACTIVE, result!!.status)
    }

    @Test
    fun `transition COMPLETED to ACTIVE fails (invalid)`() = runTest {
        manager.createTask("t1", "Test")
        manager.transition("t1", TaskStatus.ACTIVE)
        manager.transition("t1", TaskStatus.COMPLETED)

        val result = manager.transition("t1", TaskStatus.ACTIVE)
        assertNull(result) // Invalid transition
    }

    @Test
    fun `checkpoint updates step_index and step_state_json`() = runTest {
        manager.createTask("t1", "Test")
        manager.transition("t1", TaskStatus.ACTIVE)

        val checkpointed = manager.checkpoint("t1", 3, """{"tool":"read_notifications"}""")
        assertNotNull(checkpointed)
        assertEquals(3, checkpointed!!.stepIndex)
        assertEquals("""{"tool":"read_notifications"}""", checkpointed.stepStateJson)
    }

    @Test
    fun `checkpoint on non-ACTIVE task returns null`() = runTest {
        manager.createTask("t1", "Test")
        // Still in CREATED, not ACTIVE
        val result = manager.checkpoint("t1", 1, "{}")
        assertNull(result)
    }

    @Test
    fun `INTERRUPTED to RESUMING transition works`() = runTest {
        manager.createTask("t1", "Test")
        manager.transition("t1", TaskStatus.ACTIVE)
        manager.transition("t1", TaskStatus.INTERRUPTED)

        val result = manager.transition("t1", TaskStatus.RESUMING)
        assertNotNull(result)
        assertEquals(TaskStatus.RESUMING, result!!.status)
    }

    @Test
    fun `getResumableTasks returns INTERRUPTED and PARKED`() = runTest {
        manager.createTask("t1", "A")
        manager.transition("t1", TaskStatus.ACTIVE)
        manager.transition("t1", TaskStatus.INTERRUPTED)

        manager.createTask("t2", "B")
        manager.transition("t2", TaskStatus.ACTIVE)
        manager.transition("t2", TaskStatus.PARKED)

        manager.createTask("t3", "C")
        manager.transition("t3", TaskStatus.ACTIVE)
        manager.transition("t3", TaskStatus.COMPLETED)

        val resumable = manager.getResumableTasks()
        assertEquals(2, resumable.size)
    }

    @Test
    fun `transition with errorMessage stores it`() = runTest {
        manager.createTask("t1", "Test")
        manager.transition("t1", TaskStatus.ACTIVE)
        val result = manager.transition("t1", TaskStatus.FAILED, errorMessage = "OOM crash")
        assertEquals("OOM crash", result?.errorMessage)
    }
}
