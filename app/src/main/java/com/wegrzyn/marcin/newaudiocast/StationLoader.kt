package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage

/**
 * Plays [station] on the Cast receiver. Shared by the UI and the schedule, which runs without it.
 *
 * [onSwitching] is true while the previous stream is being stopped first; [onLoadResult] gets the
 * load result, or null when nothing had to be loaded (that station is already on).
 */
fun RemoteMediaClient.loadStation(
    station: RadioStation,
    onSwitching: (Boolean) -> Unit = {},
    onLoadResult: (RemoteMediaClient.MediaChannelResult?) -> Unit = {}
) {
    // The station that is already on: don't restart the stream, just resume it.
    if (mediaInfo?.contentId == station.uri) {
        when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_BUFFERING -> {
                onLoadResult(null)
                return
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                play()
                onLoadResult(null)
                return
            }
        }
    }
    // Replacing a live stream that is still playing can leave the Default Media Receiver
    // stuck in BUFFERING, so stop the current media first and load once the receiver is idle.
    if (playerState != MediaStatus.PLAYER_STATE_IDLE &&
        playerState != MediaStatus.PLAYER_STATE_UNKNOWN
    ) {
        onSwitching(true)
        stop().setResultCallback {
            onSwitching(false)
            load(station, onLoadResult)
        }
    } else {
        load(station, onLoadResult)
    }
}

private fun RemoteMediaClient.load(
    station: RadioStation,
    onLoadResult: (RemoteMediaClient.MediaChannelResult?) -> Unit
) {
    val mediaMetaData = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
    mediaMetaData.putString(MediaMetadata.KEY_TITLE, station.name)
    mediaMetaData.addImage(WebImage(Uri.parse(station.img)))

    val mediaInfo = MediaInfo.Builder(station.uri)
        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        .setContentType(station.contentType)
        .setMetadata(mediaMetaData)
        .apply { station.hlsSegmentFormat?.let { setHlsSegmentFormat(it) } }
        .build()

    val mediaLoadRequestData = MediaLoadRequestData.Builder()
        .setMediaInfo(mediaInfo)
        .setAutoplay(true)
        .build()

    load(mediaLoadRequestData).setResultCallback { onLoadResult(it) }
}

/** The last played station, offered as quick play when connected but idle. */
object LastStation {

    private const val PREFS_NAME = "player"
    private const val KEY_LAST_STATION = "last_station"

    fun get(context: Context): RadioStation? =
        prefs(context).getString(KEY_LAST_STATION, null)?.let { Stations.byName(it) }

    fun set(context: Context, station: RadioStation) {
        prefs(context).edit { putString(KEY_LAST_STATION, station.name) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
