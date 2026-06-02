package dev.anthropic.pidroid.audit

import dev.anthropic.pidroid.tools.ConfirmationResult
import dev.anthropic.pidroid.tools.RiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AuditLoggerTest {
    private lateinit var db: AuditDatabase
    private lateinit var dao: AuditDao
    private lateinit var logger: AuditLogger

    @Before
    fun setup() {
        db = AuditDatabase.createInMemory(RuntimeEnvironment.getApplication())
        dao = db.auditDao()
        logger = AuditLogger(dao)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `tool execution logged with tool name and risk level`() = runTest {
        logger.logToolExecution(
            toolName = "read_notifications",
            riskLevel = RiskLevel.READ_ONLY,
            durationMs = 42,
        )

        val entries = dao.getRecent(10)
        assertEquals(1, entries.size)
        assertEquals("read_notifications", entries[0].toolName)
        assertEquals("READ_ONLY", entries[0].riskLevel)
        assertEquals(42L, entries[0].durationMs)
        assertEquals("tool_execution", entries[0].eventType)
    }

    @Test
    fun `confirmation result logged`() = runTest {
        logger.logConfirmation("create_calendar_event", ConfirmationResult.APPROVED)

        val entries = dao.getRecent(10)
        assertEquals(1, entries.size)
        assertEquals("APPROVED", entries[0].confirmationResult)
        assertEquals("confirmation", entries[0].eventType)
    }

    @Test
    fun `error tool results logged with error flag`() = runTest {
        logger.logToolExecution(
            toolName = "open_url",
            riskLevel = RiskLevel.LOCAL_WRITE,
            durationMs = 5,
            isError = true,
        )

        val errors = dao.getErrors(10)
        assertEquals(1, errors.size)
        assertTrue(errors[0].isError)
    }

    @Test
    fun `entries ordered by timestamp descending`() = runTest {
        logger.logToolExecution("tool_a", RiskLevel.READ_ONLY, durationMs = 10)
        Thread.sleep(10) // ensure different timestamps
        logger.logToolExecution("tool_b", RiskLevel.READ_ONLY, durationMs = 20)

        val entries = dao.getRecent(10)
        assertEquals("tool_b", entries[0].toolName) // most recent first
        assertEquals("tool_a", entries[1].toolName)
    }

    @Test
    fun `no tool arguments stored (metadata-only)`() = runTest {
        logger.logToolExecution(
            toolName = "search_contacts",
            riskLevel = RiskLevel.READ_ONLY,
            durationMs = 100,
            metadataJson = """{"result_count": 3}""",
        )

        val entries = dao.getRecent(10)
        // metadataJson only contains schema-level metadata, not the actual arguments
        assertEquals("""{"result_count": 3}""", entries[0].metadataJson)
    }
}
