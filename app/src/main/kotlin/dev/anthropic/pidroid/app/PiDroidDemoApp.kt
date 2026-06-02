package dev.anthropic.pidroid.app

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dev.anthropic.pidroid.AndroidPermissionChecker
import dev.anthropic.pidroid.LlmProviderConfig
import dev.anthropic.pidroid.PiRuntime
import dev.anthropic.pidroid.PiRuntimeConfig
import dev.anthropic.pidroid.llm.registry.ModelRegistry
import dev.anthropic.pidroid.android.CalendarAccessor
import dev.anthropic.pidroid.android.ContactAccessor
import dev.anthropic.pidroid.android.DeviceStateReader
import dev.anthropic.pidroid.android.IntentDispatcher
import dev.anthropic.pidroid.android.NotificationAccessor
import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.handlers.CalendarToolHandler
import dev.anthropic.pidroid.tools.handlers.ContactToolHandler
import dev.anthropic.pidroid.tools.handlers.DeviceStateToolHandler
import dev.anthropic.pidroid.tools.handlers.IntentToolHandler
import dev.anthropic.pidroid.tools.handlers.NotificationToolHandler
import kotlinx.coroutines.runBlocking

/**
 * Application subclass for the Pi-Droid demo app.
 *
 * Initializes [PiRuntime] with the full handler map on process start
 * when an API key is available. The runtime singleton is exposed via
 * the companion object for Activity/ViewModel access.
 */
class PiDroidDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeRuntime()
    }

    private fun initializeRuntime() {
        val prefs = getSecurePrefs(this) ?: run {
            Log.w(TAG, "EncryptedSharedPreferences unavailable — runtime not initialized")
            return
        }

        val apiKey = prefs.getString(PREF_API_KEY, null)
        if (apiKey.isNullOrBlank()) {
            Log.i(TAG, "No API key configured — runtime not initialized")
            return
        }

        val provider = prefs.getString(PREF_PROVIDER, "anthropic") ?: "anthropic"
        val model = prefs.getString(PREF_MODEL, defaultModelFor(provider))!!
        val baseUrl = prefs.getString(PREF_BASE_URL, null)
        val systemPrompt = loadSystemPrompt()

        val handlers = buildHandlerMap(this)
        val config = PiRuntimeConfig(
            llmProvider = LlmProviderConfig(
                provider = provider,
                modelId = model,
                apiKey = apiKey,
                baseUrl = baseUrl,
            ),
            capabilities = DEMO_CAPABILITIES,
            systemPrompt = systemPrompt,
        )

        try {
            // runBlocking is safe here: Application.onCreate runs once at process start
            // before any Activity/Service, and initialize() does no I/O — pure object construction.
            _runtime = runBlocking { PiRuntime.initialize(this@PiDroidDemoApp, config, handlers) }
            Log.i(TAG, "PiRuntime initialized with ${handlers.size} handlers, provider=$provider")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PiRuntime", e)
        }
    }

    private fun loadSystemPrompt(): String {
        return try {
            resources.openRawResource(R.raw.demo_system_prompt)
                .bufferedReader()
                .use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load system prompt from raw resource", e)
            DEFAULT_SYSTEM_PROMPT
        }
    }

    companion object {
        private const val TAG = "PiDroidDemoApp"
        private const val PREFS_NAME = "pidroid_demo_prefs"
        const val PREF_API_KEY = "llm_api_key"
        const val PREF_PROVIDER = "llm_provider_type"
        const val PREF_MODEL = "llm_model"
        const val PREF_BASE_URL = "llm_base_url"

        private var instance: PiDroidDemoApp? = null
        private var _runtime: PiRuntime? = null

        /** The PiRuntime singleton, or null if no API key is configured. */
        val runtime: PiRuntime? get() = _runtime

        /** Whether the runtime has been initialized. */
        val isInitialized: Boolean get() = _runtime != null

        /**
         * Get the default model for a provider, preferring the registry.
         * Falls back to hardcoded defaults if the registry isn't initialized yet.
         */
        fun defaultModelFor(provider: String): String {
            return try {
                ModelRegistry.getModels(provider).firstOrNull()?.id
            } catch (_: IllegalStateException) {
                null // Registry not initialized yet
            } ?: when (provider) {
                "anthropic" -> "claude-sonnet-4-20250514"
                "openai" -> "gpt-4o"
                else -> "claude-sonnet-4-20250514"
            }
        }

        /**
         * Get EncryptedSharedPreferences with fallback to regular SharedPreferences.
         * security-crypto:1.1.0-alpha06 can crash on some Android 14+ devices.
         */
        fun getSecurePrefs(context: Context): android.content.SharedPreferences? {
            return try {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences failed, falling back to regular prefs", e)
                try {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                } catch (e2: Exception) {
                    Log.e(TAG, "SharedPreferences also failed", e2)
                    null
                }
            }
        }

        val DEMO_CAPABILITIES = listOf(
            CapabilityGrant("android.permission.READ_CALENDAR"),
            CapabilityGrant("android.permission.WRITE_CALENDAR"),
            CapabilityGrant("android.permission.READ_CONTACTS"),
            CapabilityGrant(CapabilityGrant.CAPABILITY_DEVICE_STATE),
            CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER),
            CapabilityGrant(CapabilityGrant.CAPABILITY_ALARM),
        )

        private fun buildHandlerMap(context: Context): Map<String, ToolHandler> {
            val permissionChecker = AndroidPermissionChecker(context)
            val calendarAccessor = CalendarAccessor(context)
            val contactAccessor = ContactAccessor(context)
            val deviceStateReader = DeviceStateReader(context)
            val intentDispatcher = IntentDispatcher(context)
            val notificationAccessor = NotificationAccessor(context)

            val calendarHandler = CalendarToolHandler(context, calendarAccessor, permissionChecker)
            val contactHandler = ContactToolHandler(contactAccessor, permissionChecker)
            val notificationHandler = NotificationToolHandler(notificationAccessor, permissionChecker)

            return mapOf(
                "read_calendar_events" to calendarHandler,
                "create_calendar_event" to calendarHandler,
                "search_contacts" to contactHandler,
                "get_contact_details" to contactHandler,
                "get_battery_state" to DeviceStateToolHandler(deviceStateReader, "get_battery_state"),
                "get_connectivity_state" to DeviceStateToolHandler(deviceStateReader, "get_connectivity_state"),
                "get_installed_apps" to DeviceStateToolHandler(deviceStateReader, "get_installed_apps"),
                "launch_app" to IntentToolHandler(intentDispatcher, "launch_app"),
                "open_url" to IntentToolHandler(intentDispatcher, "open_url"),
                "share_text" to IntentToolHandler(intentDispatcher, "share_text"),
                "send_intent" to IntentToolHandler(intentDispatcher, "send_intent"),
                "set_alarm" to IntentToolHandler(intentDispatcher, "set_alarm"),
                "read_notifications" to notificationHandler,
                "get_notification_channels" to notificationHandler,
            )
            // memory_store, memory_search, memory_delete: deferred (requires Room + ONNX)
            // schedule_action: deferred (no handler implementation exists)
        }

        private const val DEFAULT_SYSTEM_PROMPT = """You are a helpful personal assistant running on an Android phone. You have access to tools for reading the calendar, searching contacts, checking device state, launching apps, opening URLs, sharing text, and sending intents. When you need to perform an action that crosses app boundaries (send_intent, share_text), the user will see a confirmation dialog — explain what you're about to do before calling the tool."""
    }
}
