package dev.anthropic.pidroid.journal

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TaskJournalDaoTest {
    private lateinit var db: TaskJournalDatabase
    private lateinit var dao: TaskJournalDao

    @Before
    fun setup() {
        db = TaskJournalDatabase.createInMemory(RuntimeEnvironment.getApplication())
        dao = db.taskDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `insert and retrieve task`() = runTest {
        val task = TaskEntry(id = "task_1", prompt = "Hello", status = TaskStatus.CREATED)
        dao.insert(task)

        val retrieved = dao.getById("task_1")
        assertEquals("task_1", retrieved?.id)
        assertEquals(TaskStatus.CREATED, retrieved?.status)
    }

    @Test
    fun `getById returns null for unknown ID`() = runTest {
        assertNull(dao.getById("nonexistent"))
    }

    @Test
    fun `update changes task status`() = runTest {
        val task = TaskEntry(id = "task_2", prompt = "Test", status = TaskStatus.CREATED)
        dao.insert(task)
        dao.update(task.copy(status = TaskStatus.ACTIVE))

        val retrieved = dao.getById("task_2")
        assertEquals(TaskStatus.ACTIVE, retrieved?.status)
    }

    @Test
    fun `getActiveTasks returns only ACTIVE`() = runTest {
        dao.insert(TaskEntry(id = "t1", prompt = "a", status = TaskStatus.ACTIVE))
        dao.insert(TaskEntry(id = "t2", prompt = "b", status = TaskStatus.COMPLETED))
        dao.insert(TaskEntry(id = "t3", prompt = "c", status = TaskStatus.ACTIVE))

        val active = dao.getActiveTasks()
        assertEquals(2, active.size)
        assertTrue(active.all { it.status == TaskStatus.ACTIVE })
    }

    @Test
    fun `markAllActiveAsInterrupted transitions active tasks`() = runTest {
        dao.insert(TaskEntry(id = "t1", prompt = "a", status = TaskStatus.ACTIVE))
        dao.insert(TaskEntry(id = "t2", prompt = "b", status = TaskStatus.ACTIVE))
        dao.insert(TaskEntry(id = "t3", prompt = "c", status = TaskStatus.COMPLETED))

        dao.markAllActiveAsInterrupted()

        val t1 = dao.getById("t1")
        val t2 = dao.getById("t2")
        val t3 = dao.getById("t3")
        assertEquals(TaskStatus.INTERRUPTED, t1?.status)
        assertEquals(TaskStatus.INTERRUPTED, t2?.status)
        assertEquals(TaskStatus.COMPLETED, t3?.status) // unchanged
    }

    @Test
    fun `getDashboardTasks returns INTERRUPTED and PARKED`() = runTest {
        dao.insert(TaskEntry(id = "t1", prompt = "a", status = TaskStatus.INTERRUPTED))
        dao.insert(TaskEntry(id = "t2", prompt = "b", status = TaskStatus.PARKED))
        dao.insert(TaskEntry(id = "t3", prompt = "c", status = TaskStatus.ACTIVE))

        val dashboard = dao.getDashboardTasks()
        assertEquals(2, dashboard.size)
    }

    @Test
    fun `deleteExpired removes old terminal tasks`() = runTest {
        val now = System.currentTimeMillis()
        dao.insert(TaskEntry(id = "t1", prompt = "a", status = TaskStatus.COMPLETED, expiresAt = now - 1000))
        dao.insert(TaskEntry(id = "t2", prompt = "b", status = TaskStatus.FAILED, expiresAt = now + 100000))
        dao.insert(TaskEntry(id = "t3", prompt = "c", status = TaskStatus.ACTIVE, expiresAt = now - 1000))

        dao.deleteExpired(now)

        assertNull(dao.getById("t1")) // expired + terminal → deleted
        assertEquals("t2", dao.getById("t2")?.id) // not yet expired
        assertEquals("t3", dao.getById("t3")?.id) // expired but ACTIVE (not terminal)
    }
}
