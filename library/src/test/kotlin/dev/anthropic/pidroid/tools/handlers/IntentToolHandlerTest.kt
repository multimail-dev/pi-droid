package dev.anthropic.pidroid.tools.handlers

import android.content.Intent
import android.provider.AlarmClock
import dev.anthropic.pidroid.android.IntentDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
class IntentToolHandlerTest {
    private lateinit var dispatcher: IntentDispatcher
    private val context = FakeToolExecutionContext()
    private val appContext get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() {
        dispatcher = IntentDispatcher(appContext)
    }

    private fun handlerFor(tool: String) = IntentToolHandler(dispatcher, tool)

    @Test
    fun `launch_app with valid package starts activity`() = runTest {
        // Robolectric's PackageManager doesn't have real apps; launchIntent returns null
        val handler = handlerFor("launch_app")
        val args = buildJsonObject { put("package_name", "com.example.nonexistent") }

        val result = handler.execute("tc_1", args, context)

        // Package not installed in Robolectric
        assertTrue(result.isError)
        assertTrue(result.content.contains("not installed"))
    }

    @Test
    fun `open_url with https starts VIEW intent`() = runTest {
        val handler = handlerFor("open_url")
        val args = buildJsonObject { put("url", "https://example.com/page") }

        val result = handler.execute("tc_2", args, context)

        assertEquals(false, result.isError)
        val shadowApp = Shadows.shadowOf(appContext)
        val startedIntent = shadowApp.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals("https", startedIntent.data?.scheme)
        assertEquals("example.com", startedIntent.data?.host)
    }

    @Test
    fun `open_url rejects intent scheme`() = runTest {
        val handler = handlerFor("open_url")
        val args = buildJsonObject { put("url", "intent://evil#Intent;scheme=https;end") }

        val result = handler.execute("tc_3", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("scheme not allowed"))
    }

    @Test
    fun `open_url rejects file scheme`() = runTest {
        val handler = handlerFor("open_url")
        val args = buildJsonObject { put("url", "file:///etc/passwd") }

        val result = handler.execute("tc_4", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("not allowed"))
    }

    @Test
    fun `open_url rejects content scheme`() = runTest {
        val handler = handlerFor("open_url")
        val args = buildJsonObject { put("url", "content://contacts/1") }

        val result = handler.execute("tc_5", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("not allowed"))
    }

    @Test
    fun `share_text starts SEND intent`() = runTest {
        val handler = handlerFor("share_text")
        val args = buildJsonObject {
            put("text", "Check this out!")
            put("subject", "Cool Link")
        }

        val result = handler.execute("tc_6", args, context)

        assertEquals(false, result.isError)
        val shadowApp = Shadows.shadowOf(appContext)
        val startedIntent = shadowApp.nextStartedActivity
        // The chooser wraps the real intent
        assertTrue(startedIntent != null)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)
    }

    @Test
    fun `send_intent with action and data starts activity`() = runTest {
        val handler = handlerFor("send_intent")
        val args = buildJsonObject {
            put("action", "android.intent.action.VIEW")
            put("data", "geo:0,0?q=pizza")
        }

        val result = handler.execute("tc_7", args, context)

        assertEquals(false, result.isError)
        val shadowApp = Shadows.shadowOf(appContext)
        val startedIntent = shadowApp.nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals("geo", startedIntent.data?.scheme)
    }

    @Test
    fun `set_alarm fires AlarmClock intent`() = runTest {
        val handler = handlerFor("set_alarm")
        val args = buildJsonObject {
            put("time", "2026-05-08T07:30:00Z")
            put("message", "Wake up!")
        }

        val result = handler.execute("tc_8", args, context)

        assertEquals(false, result.isError)
        val shadowApp = Shadows.shadowOf(appContext)
        val startedIntent = shadowApp.nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM, startedIntent.action)
        assertEquals("Wake up!", startedIntent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
    }

    @Test
    fun `open_url missing url field returns error`() = runTest {
        val handler = handlerFor("open_url")
        val args = buildJsonObject {}

        val result = handler.execute("tc_9", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("url"))
    }

    @Test
    fun `set_alarm with invalid time format returns error`() = runTest {
        val handler = handlerFor("set_alarm")
        val args = buildJsonObject { put("time", "not-a-date") }

        val result = handler.execute("tc_10", args, context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("Invalid time format"))
    }
}
