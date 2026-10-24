package com.wegrzyn.marcin.newaudiocast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * "At [hour]:[minute] switch to [stationName]". [days] holds Calendar.DAY_OF_WEEK values;
 * empty means one-off, which turns itself off after firing.
 */
data class ScheduleEntry(
    val id: Long,
    val stationName: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>,
    val enabled: Boolean = true
)

/** Stores the schedule entries and keeps one wall-clock alarm armed per enabled entry. */
object Schedule {

    private const val PREFS_NAME = "schedule"
    private const val KEY_ENTRIES = "entries"
    const val EXTRA_ENTRY_ID = "entry_id"

    private var state: MutableStateFlow<List<ScheduleEntry>>? = null

    fun entries(context: Context): StateFlow<List<ScheduleEntry>> = state(context).asStateFlow()

    fun find(context: Context, id: Long): ScheduleEntry? = state(context).value.find { it.id == id }

    /** Adds the entry or replaces the one with the same id, and (re)arms its alarm. */
    fun save(context: Context, entry: ScheduleEntry) {
        update(context) { entries ->
            if (entries.any { it.id == entry.id }) {
                entries.map { if (it.id == entry.id) entry else it }
            } else {
                entries + entry
            }
        }
        arm(context, entry)
    }

    fun remove(context: Context, id: Long) {
        update(context) { entries -> entries.filterNot { it.id == id } }
        Alarms.cancel(context, pendingIntent(context, id))
    }

    fun setEnabled(context: Context, id: Long, enabled: Boolean) {
        find(context, id)?.let { save(context, it.copy(enabled = enabled)) }
    }

    /** Called when an entry's alarm went off: arm the next occurrence or turn a one-off off. */
    fun onFired(context: Context, entry: ScheduleEntry) {
        if (entry.days.isEmpty()) {
            save(context, entry.copy(enabled = false))
        } else {
            arm(context, entry)
        }
    }

    /** Re-arms every enabled entry; alarms are lost on reboot and app update. */
    fun rescheduleAll(context: Context) {
        state(context).value.forEach { arm(context, it) }
    }

    /** Next moment (epoch millis) the entry fires, strictly after [now]. */
    fun nextTrigger(entry: ScheduleEntry, now: Long = System.currentTimeMillis()): Long {
        val candidate = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, entry.hour)
            set(Calendar.MINUTE, entry.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Today plus the next 7 days always contains a matching weekday.
        repeat(8) {
            if (candidate.timeInMillis > now &&
                (entry.days.isEmpty() || candidate.get(Calendar.DAY_OF_WEEK) in entry.days)
            ) {
                return candidate.timeInMillis
            }
            candidate.add(Calendar.DAY_OF_MONTH, 1)
        }
        error("No trigger found for $entry")
    }

    private fun arm(context: Context, entry: ScheduleEntry) {
        val operation = pendingIntent(context, entry.id)
        if (entry.enabled) {
            Alarms.set(context, AlarmManager.RTC_WAKEUP, nextTrigger(entry), operation)
        } else {
            Alarms.cancel(context, operation)
        }
    }

    // One PendingIntent per entry; the data URI keeps them distinct.
    private fun pendingIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScheduleReceiver::class.java)
            .setData("audiocast://schedule/$id".toUri())
            .putExtra(EXTRA_ENTRY_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun update(context: Context, transform: (List<ScheduleEntry>) -> List<ScheduleEntry>) {
        val flow = state(context)
        flow.update(transform)
        prefs(context).edit { putString(KEY_ENTRIES, toJson(flow.value)) }
    }

    @Synchronized
    private fun state(context: Context): MutableStateFlow<List<ScheduleEntry>> =
        state ?: MutableStateFlow(fromJson(prefs(context).getString(KEY_ENTRIES, null)))
            .also { state = it }

    private fun toJson(entries: List<ScheduleEntry>): String = JSONArray().apply {
        entries.forEach { entry ->
            put(JSONObject().apply {
                put("id", entry.id)
                put("station", entry.stationName)
                put("hour", entry.hour)
                put("minute", entry.minute)
                put("days", JSONArray(entry.days.sorted()))
                put("enabled", entry.enabled)
            })
        }
    }.toString()

    private fun fromJson(json: String?): List<ScheduleEntry> {
        if (json == null) return emptyList()
        val array = JSONArray(json)
        return List(array.length()) { i ->
            val item = array.getJSONObject(i)
            val days = item.getJSONArray("days")
            ScheduleEntry(
                id = item.getLong("id"),
                stationName = item.getString("station"),
                hour = item.getInt("hour"),
                minute = item.getInt("minute"),
                days = List(days.length()) { days.getInt(it) }.toSet(),
                enabled = item.getBoolean("enabled")
            )
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
