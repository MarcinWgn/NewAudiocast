package com.wegrzyn.marcin.newaudiocast

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms the sleep timer and the station schedule whenever the system dropped or misplaced
 * their alarms: reboot, app update, exact-alarm permission granted, clock or time zone change.
 */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                SleepTimer.restore(context)
                Schedule.rescheduleAll(context)
            }
        }
    }
}
