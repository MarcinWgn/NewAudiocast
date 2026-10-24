package com.wegrzyn.marcin.newaudiocast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/** Alarm helpers shared by the sleep timer and the station schedule. */
object Alarms {

    // Without the exact-alarm permission Android stretches shorter windows to about this anyway.
    private const val INEXACT_WINDOW_MS = 10 * 60_000L

    /** Exact alarms need the user-granted "Alarms & reminders" permission on API 31+. */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Fires [operation] at [triggerAt] (clock of [type]): exactly if allowed, else within 10 min. */
    fun set(context: Context, type: Int, triggerAt: Long, operation: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(type, triggerAt, operation)
        } else {
            // A plain inexact alarm may be deferred by up to 75% of the delay; cap it instead.
            alarmManager.setWindow(type, triggerAt, INEXACT_WINDOW_MS, operation)
        }
    }

    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java).cancel(operation)
    }

    /** Opens the system "Alarms & reminders" screen for this app. */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        )
    }
}
