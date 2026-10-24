package com.wegrzyn.marcin.newaudiocast

import android.net.Uri

enum class PlaybackStatus { IDLE, PLAYING, PAUSED, BUFFERING }

data class PlayerUiState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val stationName: String = "",
    val imageUri: Uri? = null,
    val castDeviceName: String = "",
    val isBeltShowing: Boolean = false,
    val volume: Float = 0f,
    val isMuted: Boolean = false,
    val isConnected: Boolean = false,
    val lastStation: RadioStation? = null
)
