package dev.anthropic.pidroid.android

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Read/write access to device calendar via ContentProvider.
 */
class CalendarAccessor(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    /**
     * Read calendar events in the given time range.
     *
     * @param startTimeIso ISO 8601 start time
     * @param endTimeIso ISO 8601 end time
     * @param calendarId Filter to specific calendar (null = all)
     * @return JSON array of event objects
     */
    fun readEvents(
        startTimeIso: String,
        endTimeIso: String,
        calendarId: String? = null,
    ): JsonArray {
        val startMs = parseIso8601(startTimeIso) ?: return JsonArray(emptyList())
        val endMs = parseIso8601(endTimeIso) ?: return JsonArray(emptyList())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.CALENDAR_ID,
        )

        var selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTEND} <= ?"
        val selectionArgs = mutableListOf(startMs.toString(), endMs.toString())

        if (calendarId != null) {
            selection += " AND ${CalendarContract.Events.CALENDAR_ID} = ?"
            selectionArgs.add(calendarId)
        }

        val events = mutableListOf<kotlinx.serialization.json.JsonObject>()

        contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(1) ?: ""
                val dtStart = cursor.getLong(2)
                val dtEnd = cursor.getLong(3)
                val location = cursor.getString(4) ?: ""
                val description = cursor.getString(5) ?: ""

                events.add(buildJsonObject {
                    put("title", title)
                    put("start_time", msToIso8601(dtStart))
                    put("end_time", msToIso8601(dtEnd))
                    put("location", location)
                    put("description", description)
                })
            }
        }

        return JsonArray(events)
    }

    /**
     * Build an Intent to create a calendar event via the system calendar app.
     *
     * This fires an intent that opens the system calendar's event creation UI.
     * The user confirms the event there.
     */
    fun buildCreateEventIntent(
        title: String,
        startTimeIso: String,
        endTimeIso: String,
        description: String? = null,
        location: String? = null,
    ): Intent {
        val startMs = parseIso8601(startTimeIso) ?: System.currentTimeMillis()
        val endMs = parseIso8601(endTimeIso) ?: (startMs + 3600_000)

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
            if (description != null) {
                putExtra(CalendarContract.Events.DESCRIPTION, description)
            }
            if (location != null) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun parseIso8601(iso: String): Long? {
        return try {
            Instant.parse(iso).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun msToIso8601(ms: Long): String {
        return Instant.ofEpochMilli(ms)
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
