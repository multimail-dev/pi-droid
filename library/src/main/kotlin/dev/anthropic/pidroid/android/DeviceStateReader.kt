package dev.anthropic.pidroid.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads device state (battery, connectivity, installed apps).
 */
class DeviceStateReader(private val context: Context) {

    /**
     * Get battery level, charging state, and source.
     */
    fun getBatteryState(): JsonObject {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        // Get charging source from sticky broadcast
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        return buildJsonObject {
            put("level", level)
            put("charging", isCharging)
            put("source", source)
            put("temperature_celsius", temperature / 10.0)
        }
    }

    /**
     * Get network connectivity state.
     */
    fun getConnectivityState(): JsonObject {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val hasCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val hasVpn = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false

        return buildJsonObject {
            put("wifi", hasWifi)
            put("cellular", hasCellular)
            put("vpn", hasVpn)
            put("connected", network != null)
        }
    }

    /**
     * Get user-installed (launchable) applications.
     */
    fun getInstalledApps(): JsonArray {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = packageManager.queryIntentActivities(mainIntent, 0)

        val apps = activities.map { resolveInfo ->
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val packageName = appInfo.packageName
            val appName = packageManager.getApplicationLabel(appInfo).toString()

            buildJsonObject {
                put("package_name", packageName)
                put("name", appName)
            }
        }.distinctBy { it["package_name"]?.toString() }

        return JsonArray(apps)
    }
}
