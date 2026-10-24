package com.wegrzyn.marcin.newaudiocast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.edit
import com.google.android.gms.cast.framework.CastContext
import java.util.concurrent.Executors

/**
 * Stops the Cast playback after a delay. Uses AlarmManager rather than a coroutine so it still
 * fires with the screen off (CPU asleep) or after the activity and its ViewModel are gone.
 */
object SleepTimer {

    private const val PREFS_NAME = "sleep_timer"
    private const val KEY_ENDS_AT = "ends_at"

    // Without the exact-alarm permission Android stretches shorter windows to about this anyway.
    private const val INEXACT_WINDOW_MS = 10 * 60_000L

    /** Elapsed-realtime moment the timer fires, or null when no timer is set. */
    fun endsAt(context: Context): Long? {
        val endsAt = prefs(context).getLong(KEY_ENDS_AT, 0L)
        // A reboot resets elapsed realtime and drops alarms, which makes a stale value look valid.
        return endsAt.takeIf { it > SystemClock.elapsedRealtime() }
    }

    fun start(context: Context, minutes: Int): Long {
        val endsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        schedule(context, endsAt)
        prefs(context).edit { putLong(KEY_ENDS_AT, endsAt) }
        return endsAt
    }

    /** Re-arms a saved timer: an app update cancels all of the app's alarms but keeps the prefs. */
    fun restore(context: Context) {
        endsAt(context)?.let { schedule(context, it) }
    }

    /** Exact alarms need the user-granted "Alarms & reminders" permission on API 31+. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun schedule(context: Context, endsAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val type = AlarmManager.ELAPSED_REALTIME_WAKEUP
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(type, endsAt, pendingIntent(context))
        } else {
            // A plain inexact alarm may be deferred by up to 75% of the delay; cap it instead.
            alarmManager.setWindow(type, endsAt, INEXACT_WINDOW_MS, pendingIntent(context))
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        clear(context)
    }

    internal fun clear(context: Context) {
        prefs(context).edit { remove(KEY_ENDS_AT) }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SleepTimerReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SleepTimer.clear(context)
        val result = goAsync()
        CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor())
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    task.result.sessionManager.currentCastSession?.remoteMediaClient?.stop()
                }
                result.finish()
            }
    }
}

/** Re-arms the timer after an app update and once the exact-alarm permission is granted. */
class SleepTimerRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> SleepTimer.restore(context)
        }
    }
}
