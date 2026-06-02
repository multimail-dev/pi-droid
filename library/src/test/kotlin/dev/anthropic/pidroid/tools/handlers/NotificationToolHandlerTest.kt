package dev.anthropic.pidroid.tools.handlers

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dev.anthropic.pidroid.android.NotificationAccessor
import dev.anthropic.pidroid.android.PiNotificationListenerService
import dev.anthropic.pidroid.tools.FakePermissionChecker
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
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
class NotificationToolHandlerTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var accessor: NotificationAccessor
    private lateinit var handler: NotificationToolHandler
    private val context = FakeToolExecutionContext()

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        permissionChecker.notificationListenerEnabled = true
        accessor = NotificationAccessor(RuntimeEnvironment.getApplication())
        handler = NotificationToolHandler(accessor, permissionChecker)
        PiNotificationListenerService.activeNotifications.clear()
    }

    @After
    fun teardown() {
        PiNotificationListenerService.activeNotifications.clear()
    }

    private fun injectNotification(
        packageName: String,
        title: String,
        text: String,
        postTime: Long = System.currentTimeMillis(),
        key: String = "$packageName:${System.nanoTime()}",
    ) {
        val notification = Notification.Builder(RuntimeEnvironment.getApplication(), "test")
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        // Set extras manually since builder may not populate on all Robolectric versions
        notification.extras = Bundle().apply {
            putString("android.title", title)
            putString("android.text", text)
        }

        val sbn = StatusBarNotification(
            packageName,
            null,  // opPkg
            0,     // id
            null,  // tag
            1000,  // uid
            0,     // initialPid
            0,     // score
            notification,
            android.os.UserHandle.getUserHandleForUid(1000),
            postTime,
        )

        synchronized(PiNotificationListenerService.activeNotifications) {
            PiNotificationListenerService.activeNotifications[key] = sbn
        }
    }

    @Test
    fun `read_notifications with no filter returns all notifications`() = runTest {
        injectNotification("com.whatsapp", "Alice", "Hey there!")
        injectNotification("com.slack", "Channel", "New message")

        val result = handler.execute("tc_1", JsonObject(emptyMap()), context)

        assertEquals(false, result.isError)
        val array = Json.parseToJsonElement(result.content).jsonArray
        assertEquals(2, array.size)
    }

    @Test
    fun `read_notifications with app_filter returns only matching`() = runTest {
        injectNotification("com.whatsapp", "Alice", "Hey!", key = "wa_1")
        injectNotification("com.slack", "Bot", "Alert", key = "slack_1")
        injectNotification("com.whatsapp", "Bob", "Yo!", key = "wa_2")

        val args = buildJsonObject {
            putJsonArray("app_filter") {
                add(kotlinx.serialization.json.JsonPrimitive("com.whatsapp"))
            }
        }

        val result = handler.execute("tc_2", args, context)

        assertEquals(false, result.isError)
        val array = Json.parseToJsonElement(result.content).jsonArray
        assertEquals(2, array.size)
        assertTrue(array.all { it.jsonObject["package"]?.jsonPrimitive?.content == "com.whatsapp" })
    }

    @Test
    fun `read_notifications with since_minutes filters old`() = runTest {
        val now = System.currentTimeMillis()
        injectNotification("com.app", "Recent", "new", postTime = now - 60_000, key = "recent")
        injectNotification("com.app", "Old", "old", postTime = now - 600_000, key = "old")

        val args = buildJsonObject { put("since_minutes", 5) }
        val result = handler.execute("tc_3", args, context)

        assertEquals(false, result.isError)
        val array = Json.parseToJsonElement(result.content).jsonArray
        assertEquals(1, array.size)
        assertEquals("Recent", array[0].jsonObject["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `service not enabled returns error`() = runTest {
        permissionChecker.notificationListenerEnabled = false

        val result = handler.execute("tc_4", JsonObject(emptyMap()), context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("NotificationListenerService not enabled"))
    }

    @Test
    fun `empty notification list returns empty array`() = runTest {
        val result = handler.execute("tc_5", JsonObject(emptyMap()), context)

        assertEquals(false, result.isError)
        assertEquals("[]", result.content)
    }

    @Test
    fun `get_notification_channels with package_name`() = runTest {
        val args = buildJsonObject { put("package_name", "com.example.app") }
        val result = handler.execute("tc_6", args, context)

        // Robolectric's NotificationManager returns empty channel list by default
        assertEquals(false, result.isError)
        assertEquals("[]", result.content)
    }
}
