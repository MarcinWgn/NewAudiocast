package com.wegrzyn.marcin.newaudiocast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    private fun schedule(context: Context, endsAt: Long) {
        Alarms.set(context, AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, pendingIntent(context))
    }

    fun cancel(context: Context) {
        Alarms.cancel(context, pendingIntent(context))
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
