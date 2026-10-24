package com.wegrzyn.marcin.newaudiocast

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors


class MainViewModel(application: Application) : AndroidViewModel(application) {



    companion object {
        val TAG = "TAGTEST"

        private const val PREFS_NAME = "player"
        private const val KEY_LAST_STATION = "last_station"

        private val STOPPED_IDLE_REASONS = setOf(
            MediaStatus.IDLE_REASON_FINISHED,
            MediaStatus.IDLE_REASON_CANCELED,
            MediaStatus.IDLE_REASON_ERROR
        )

        // Load results caused by the user switching stations, not by a broken stream.
        private val IGNORED_LOAD_CODES = setOf(
            CastStatusCodes.REPLACED,
            CastStatusCodes.CANCELED,
            CommonStatusCodes.INTERRUPTED
        )

        private const val ERROR_DEDUP_MS = 3_000L
    }

    private var remoteMediaClient: RemoteMediaClient? = null

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            remoteMediaClient?.let { checkState(it) }
        }
    }

    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            updateVolume()
        }
    }

    val isCasting: Boolean
        get() = mCastSession?.isConnected == true

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            lastStation = prefs.getString(KEY_LAST_STATION, null)
                ?.let { name -> Stations.stationsList.find { it.name == name } }
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)
    val events: Flow<PlayerEvent> = _events.receiveAsFlow()

    private var lastErrorAt = 0L

    // True while the previous stream is being stopped before loading a new station.
    private var isSwitchingStation = false


    private var mCastSession: CastSession? = null
    private var mSessionManager: SessionManager? = null
    private var castContext: CastContext? = null

    // Station picked before any Cast device was connected; played once a session starts.
    private var pendingStation: RadioStation? = null

    val routeSelector: MediaRouteSelector?
        get() = castContext?.mergedSelector

    val mCastContextExecutor =
        CastContext.getSharedInstance(application, Executors.newSingleThreadExecutor())

    private val mSessionManagerListener: SessionManagerListener<CastSession> =
        SessionManagerListenerImpl()

    inner class SessionManagerListenerImpl : SessionManagerListener<CastSession> {
        override fun onSessionEnded(p0: CastSession, p1: Int) {
            Log.d(TAG, "session ended")
            markDisconnected()
            p0.remoteMediaClient?.stop()
            detachSession()
        }

        override fun onSessionEnding(p0: CastSession) {
            Log.d(TAG, "session ending ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionResumeFailed(p0: CastSession, p1: Int) {
            Log.d(TAG, "session resume ${p0.castDevice?.friendlyName}")

            markDisconnected()
        }

        override fun onSessionResumed(p0: CastSession, p1: Boolean) {
            Log.d(TAG, "session resumed ${p0.castDevice?.friendlyName} ${p0.applicationMetadata.toString()}")
            attachSession(p0)
        }

        override fun onSessionResuming(p0: CastSession, p1: String) {
            Log.d(TAG, "session Resuming ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionStartFailed(p0: CastSession, p1: Int) {
            Log.d(TAG, "session start failed ${p0.castDevice?.friendlyName}")
            pendingStation = null

            markDisconnected()
        }


        override fun onSessionStarted(p0: CastSession, p1: String) {

            Log.d(TAG, "session started ${p0.castDevice?.friendlyName}")
            attachSession(p0)
            pendingStation?.let { station ->
                pendingStation = null
                p0.remoteMediaClient?.let { loadStation(it, station) }
            }
        }

        override fun onSessionStarting(p0: CastSession) {
            Log.d(TAG, "session starting ${p0.castDevice?.friendlyName}")
        }

        override fun onSessionSuspended(p0: CastSession, p1: Int) {
            Log.d(TAG, "session suspended ${p0.castDevice?.friendlyName}")
            markDisconnected()
        }

    }

    init {
        mCastContextExecutor
            .addOnSuccessListener { castContext ->

                this.castContext = castContext
                mSessionManager = castContext.sessionManager
                mSessionManager?.currentCastSession?.let { attachSession(it) }
                mSessionManager?.addSessionManagerListener(
                    mSessionManagerListener,
                    CastSession::class.java
                )
            }
            .addOnFailureListener {
                Log.d(TAG, "session manager: $it")
            }

        Log.d(TAG, "ViewModel Start --> init")
    }

    override fun onCleared() {
        super.onCleared()

        mSessionManager?.removeSessionManagerListener(
            mSessionManagerListener,
            CastSession::class.java
        )
        detachSession()

        Log.d(TAG, "onCleared")
    }

    private fun attachSession(session: CastSession) {
        if (session !== mCastSession) {
            mCastSession?.removeCastListener(castListener)
            session.addCastListener(castListener)
            mCastSession = session
        }
        _uiState.update {
            it.copy(isConnected = true, castDeviceName = session.castDevice?.friendlyName ?: "")
        }
        updateVolume()
        val client = session.remoteMediaClient ?: return
        if (client !== remoteMediaClient) {
            remoteMediaClient?.unregisterCallback(mediaCallback)
            client.registerCallback(mediaCallback)
            remoteMediaClient = client
        }
        checkState(client)
    }

    private fun markDisconnected() {
        _uiState.update { it.copy(isBeltShowing = false, isConnected = false) }
    }

    private fun detachSession() {
        mCastSession?.removeCastListener(castListener)
        remoteMediaClient?.unregisterCallback(mediaCallback)
        remoteMediaClient = null
        mCastSession = null
    }

    /** Casts [radioStation], or remembers it and calls [onNoSession] so the UI can offer a device. */
    fun radioCast(radioStation: RadioStation, onNoSession: () -> Unit) {
        val session = mCastSession
        val client = session?.remoteMediaClient
        if (client != null) {
            attachSession(session)
            loadStation(client, radioStation)
        } else {
            Log.d(TAG, "remote media client is null")
            pendingStation = radioStation
            onNoSession()
        }
    }

    fun clearPendingStation() {
        pendingStation = null
    }

    private fun loadStation(client: RemoteMediaClient, radioStation: RadioStation) {
        // Tapping the station that is already on: don't restart the stream, just resume it.
        if (client.mediaInfo?.contentId == radioStation.uri) {
            when (client.playerState) {
                MediaStatus.PLAYER_STATE_PLAYING, MediaStatus.PLAYER_STATE_BUFFERING -> return
                MediaStatus.PLAYER_STATE_PAUSED -> {
                    client.play()
                    return
                }
            }
        }
        // Replacing a live stream that is still playing can leave the Default Media Receiver
        // stuck in BUFFERING, so stop the current media first and load once the receiver is idle.
        if (client.playerState != MediaStatus.PLAYER_STATE_IDLE &&
            client.playerState != MediaStatus.PLAYER_STATE_UNKNOWN
        ) {
            isSwitchingStation = true
            client.stop().setResultCallback {
                isSwitchingStation = false
                load(client, radioStation)
            }
        } else {
            load(client, radioStation)
        }
    }

    private fun load(client: RemoteMediaClient, radioStation: RadioStation) {
        val mediaMetaData = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC)
        mediaMetaData.putString(MediaMetadata.KEY_TITLE, radioStation.name)
        mediaMetaData.addImage(WebImage(Uri.parse(radioStation.img)))

        val mediaInfo = MediaInfo.Builder(radioStation.uri)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(radioStation.contentType)
            .setMetadata(mediaMetaData)
            .apply { radioStation.hlsSegmentFormat?.let { setHlsSegmentFormat(it) } }
            .build()

        val mediaLoadRequestData = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        client.load(mediaLoadRequestData).setResultCallback {
            Log.d(TAG, "wynik data: ${it.mediaError?.type}")
            Log.d(TAG, "wynik error: ${it.mediaError?.detailedErrorCode}")
            if (!it.status.isSuccess && it.status.statusCode !in IGNORED_LOAD_CODES) {
                reportPlaybackFailed(radioStation.name)
            }
        }

        prefs.edit { putString(KEY_LAST_STATION, radioStation.name) }
        _uiState.update { it.copy(lastStation = radioStation) }
    }

    private fun checkState(remoteMediaClient: RemoteMediaClient) {
        // Keep the belt visible during the intermediate stop of a station switch.
        if (isSwitchingStation) return

        if (remoteMediaClient.playerState == MediaStatus.PLAYER_STATE_IDLE &&
            remoteMediaClient.idleReason in STOPPED_IDLE_REASONS
        ) {
            val previous = _uiState.value
            if (remoteMediaClient.idleReason == MediaStatus.IDLE_REASON_ERROR &&
                previous.status != PlaybackStatus.IDLE
            ) {
                reportPlaybackFailed(previous.stationName)
            }
            _uiState.update { it.copy(status = PlaybackStatus.IDLE, isBeltShowing = false) }
            return
        }

        val status = when {
            remoteMediaClient.isPlaying -> PlaybackStatus.PLAYING
            remoteMediaClient.isPaused -> PlaybackStatus.PAUSED
            remoteMediaClient.isBuffering -> PlaybackStatus.BUFFERING
            else -> null
        }
        val metadata = remoteMediaClient.mediaInfo?.metadata

        _uiState.update { state ->
            state.copy(
                status = status ?: state.status,
                isBeltShowing = status != null || state.isBeltShowing,
                castDeviceName = mCastSession?.castDevice?.friendlyName ?: "",
                stationName = metadata?.getString(MediaMetadata.KEY_TITLE) ?: state.stationName,
                imageUri = metadata?.images?.firstOrNull()?.url ?: state.imageUri
            )
        }
    }

    fun playPause() {
        val client = remoteMediaClient ?: return
        when {
            client.isPaused -> client.play()
            client.isPlaying -> client.pause()

        }
    }

    // A failed load can be reported both by the load result and the idle status; show it once.
    private fun reportPlaybackFailed(stationName: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastErrorAt < ERROR_DEDUP_MS) return
        lastErrorAt = now
        _events.trySend(PlayerEvent.PlaybackFailed(stationName))
    }

    fun stop() {
        remoteMediaClient?.stop()
    }

    fun setVolume(volume: Float) {
        val session = mCastSession ?: return
        val value = volume.coerceIn(0f, 1f)
        try {
            session.volume = value.toDouble()
            _uiState.update { it.copy(volume = value) }
        } catch (e: Exception) {
            Log.d(TAG, "set volume failed: $e")
        }
    }

    fun adjustVolume(delta: Float) {
        setVolume(_uiState.value.volume + delta)
    }

    fun toggleMute() {
        val session = mCastSession ?: return
        try {
            session.isMute = !_uiState.value.isMuted
            _uiState.update { it.copy(isMuted = !it.isMuted) }
        } catch (e: Exception) {
            Log.d(TAG, "set mute failed: $e")
        }
    }

    private fun updateVolume() {
        val session = mCastSession ?: return
        try {
            _uiState.update {
                it.copy(volume = session.volume.toFloat(), isMuted = session.isMute)
            }
        } catch (e: IllegalStateException) {
            Log.d(TAG, "read volume failed: $e")
        }
    }
}