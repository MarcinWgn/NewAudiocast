package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.LaunchOptions
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions


class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {


        // Play/pause only: Android 13+ media controls put the default "stop casting" action
        // in a stray bottom-row slot. Disconnecting stays available via the output switcher.
        val notificationOptions = NotificationOptions.Builder()
            .setActions(listOf(MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK), intArrayOf(0))
            .setTargetActivityClassName(MainActivity::class.java.name)
            .build()

        val castMediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setMediaSessionEnabled(true)
            .build()
        val launchOptions = LaunchOptions.Builder()
            .build()


        return CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(castMediaOptions)
            .setLaunchOptions(launchOptions)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()


    }

    override fun getAdditionalSessionProviders(p0: Context): MutableList<SessionProvider>? {
        return null
    }

}