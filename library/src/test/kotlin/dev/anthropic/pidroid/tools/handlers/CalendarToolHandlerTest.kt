package dev.anthropic.pidroid.tools.handlers

import android.content.Intent
import android.provider.CalendarContract
import dev.anthropic.pidroid.android.CalendarAccessor
import dev.anthropic.pidroid.tools.FakePermissionChecker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarToolHandlerTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var accessor: CalendarAccessor
    private lateinit var handler: CalendarToolHandler
    private val context = FakeToolExecutionContext()
    private val appContext get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        permissionChecker.grantedPermissions.add("android.permission.READ_CALENDAR")
        permissionChecker.grantedPermissions.add("android.permission.WRITE_CALENDAR")
        accessor = CalendarAccessor(appContext)
        handler = CalendarToolHandler(appContext, accessor, permissionChecker)
    }

    @Test
    fun `read_calendar_events with permission returns results`() = runTest {
        val args = buildJsonObject {
            put("start_time", "2026-05-08T09:00:00Z")
            put("end_time", "2026-05-08T17:00:00Z")
        }

        val result = handler.execute("tc_1", args, context)

        // ContentProvider not seeded in Robolectric, returns empty
        assertEquals(false, result.isError)
        assertEquals("[]", result.content)
    }

    @Test
    fun `read_calendar_events without READ_CALENDAR returns error`() = runTest {
        permissionChecker.grantedPermissions.remove("android.permission.READ_CALENDAR")
        val args = buildJsonObject {
            put("start_time", "2026-05-08T09:00:00Z")
            put("end_time", "2026-05-08T17:00:00Z")
        }

        val result = handler.execute("tc_2", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("READ_CALENDAR"))
    }

    @Test
    fun `create_calendar_event fires INSERT intent with correct extras`() = runTest {
        val args = buildJsonObject {
            put("title", "Team Standup")
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T10:30:00Z")
            put("description", "Daily sync")
            put("location", "Room 4B")
        }

        val result = handler.execute("tc_3", args, context)

        assertEquals(false, result.isError)
        assertTrue(result.content.contains("Team Standup"))

        val shadowApp = Shadows.shadowOf(appContext)
        val startedIntent = shadowApp.nextStartedActivity
        assertTrue(startedIntent != null)
        assertEquals(Intent.ACTION_INSERT, startedIntent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, startedIntent.data)
        assertEquals("Team Standup", startedIntent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Daily sync", startedIntent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals("Room 4B", startedIntent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
    }

    @Test
    fun `create_calendar_event without WRITE_CALENDAR returns error`() = runTest {
        permissionChecker.grantedPermissions.remove("android.permission.WRITE_CALENDAR")
        val args = buildJsonObject {
            put("title", "Meeting")
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T11:00:00Z")
        }

        val result = handler.execute("tc_4", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("WRITE_CALENDAR"))
    }

    @Test
    fun `missing required start_time returns error`() = runTest {
        val args = buildJsonObject {
            put("end_time", "2026-05-08T17:00:00Z")
        }

        val result = handler.execute("tc_5", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("start_time"))
    }

    @Test
    fun `empty time range returns empty array`() = runTest {
        val args = buildJsonObject {
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T10:00:00Z")
        }

        val result = handler.execute("tc_6", args, context)

        assertEquals(false, result.isError)
        assertEquals("[]", result.content)
    }
}
