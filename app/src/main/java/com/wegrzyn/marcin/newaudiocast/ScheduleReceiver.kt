package com.wegrzyn.marcin.newaudiocast

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import java.util.concurrent.Executors

/**
 * A schedule entry went off: switch the running Cast session to its station, or, when nothing
 * is being cast, post a notification that opens the app with that station.
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "schedule"

        // goAsync() gives about 10 s; leave room for the load request after a session resume.
        private const val SESSION_RESUME_TIMEOUT_MS = 6_000L
        private const val TOTAL_TIMEOUT_MS = 9_000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entry = Schedule.find(context, intent.getLongExtra(Schedule.EXTRA_ENTRY_ID, -1)) ?: return
        Schedule.onFired(context, entry)
        val station = Stations.byName(entry.stationName) ?: return

        val app = context.applicationContext
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                pending.finish()
            }
        }
        handler.postDelayed({ finish() }, TOTAL_TIMEOUT_MS)

        CastContext.getSharedInstance(app, Executors.newSingleThreadExecutor())
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    notifyStart(app, station)
                    finish()
                    return@addOnCompleteListener
                }
                awaitSession(task.result.sessionManager, handler) { client ->
                    if (client == null) {
                        notifyStart(app, station)
                        finish()
                    } else {
                        LastStation.set(app, station)
                        client.loadStation(station) { finish() }
                    }
                }
            }
    }

    /**
     * The current session's media client. When the alarm started a fresh process the framework
     * is usually still resuming the session, so wait briefly for that before giving up.
     */
    private fun awaitSession(
        sessionManager: SessionManager,
        handler: Handler,
        onResult: (RemoteMediaClient?) -> Unit
    ) {
        sessionManager.currentCastSession?.remoteMediaClient?.let {
            onResult(it)
            return
        }
        var done = false
        lateinit var listener: SessionManagerListener<CastSession>
        val complete = { client: RemoteMediaClient? ->
            if (!done) {
                done = true
                sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
                onResult(client)
            }
        }
        listener = object : SessionManagerListener<CastSession> {
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
                complete(session.remoteMediaClient)

            override fun onSessionStarted(session: CastSession, sessionId: String) =
                complete(session.remoteMediaClient)

            override fun onSessionResumeFailed(session: CastSession, error: Int) = complete(null)
            override fun onSessionStartFailed(session: CastSession, error: Int) = complete(null)
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionEnded(session: CastSession, error: Int) {}
            override fun onSessionSuspended(session: CastSession, reason: Int) {}
        }
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        handler.postDelayed({ complete(null) }, SESSION_RESUME_TIMEOUT_MS)
    }

    private fun notifyStart(context: Context, station: RadioStation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.schedule_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val open = PendingIntent.getActivity(
            context,
            station.name.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_STATION, station.name)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.baseline_radio_24)
            .setContentTitle(context.getString(R.string.schedule_notification_title, station.name))
            .setContentText(context.getString(R.string.schedule_notification_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(station.name.hashCode(), notification)
    }
}
