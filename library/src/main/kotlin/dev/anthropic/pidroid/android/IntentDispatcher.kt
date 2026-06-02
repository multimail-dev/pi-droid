package dev.anthropic.pidroid.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock

/**
 * Constructs and dispatches Android Intents for the tool system.
 *
 * All intents are fire-and-forget (startActivity). If the host app registers
 * an ActivityResultLauncher via PiRuntimeConfig, that integration is a host-app concern.
 */
class IntentDispatcher(private val context: Context) {

    companion object {
        /** Schemes allowed for open_url. Rejects intent://, file://, content:// */
        private val ALLOWED_URL_SCHEMES = setOf("http", "https")
    }

    /**
     * Launch an app by package name.
     * @return null on success, error message on failure
     */
    fun launchApp(packageName: String): String? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "App not installed or not launchable: '$packageName'"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return null
    }

    /**
     * Open a URL. Only http/https schemes are allowed.
     * @return null on success, error message on failure
     */
    fun openUrl(url: String): String? {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return "Invalid URL: '$url'"
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme !in ALLOWED_URL_SCHEMES) {
            return "URL scheme not allowed: '$scheme'. Only http and https are permitted."
        }

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return null
    }

    /**
     * Share text via Android share sheet.
     */
    fun shareText(text: String, subject: String? = null) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (subject != null) {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * Fire a custom intent.
     */
    fun sendIntent(
        action: String,
        dataUri: String? = null,
        type: String? = null,
        extras: Map<String, String>? = null,
    ) {
        val intent = Intent(action).apply {
            if (dataUri != null) data = Uri.parse(dataUri)
            if (type != null) {
                if (dataUri != null) setDataAndType(Uri.parse(dataUri), type)
                else this.type = type
            }
            extras?.forEach { (key, value) -> putExtra(key, value) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Set an alarm via AlarmClock intent.
     */
    fun setAlarm(hour: Int, minute: Int, message: String? = null) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (message != null) {
                putExtra(AlarmClock.EXTRA_MESSAGE, message)
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
