package com.wegrzyn.marcin.newaudiocast

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.delay

private val SLEEP_TIMER_MINUTES = listOf(5, 15, 30, 45, 60, 90)

@Composable
fun ControlBelt(
    state: PlayerUiState,
    model: MainViewModel,
    onNoSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Hidden until there is something to control: a playing station or a connected device.
    AnimatedVisibility(
        visible = state.isBeltShowing || state.isConnected,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            if (state.isBeltShowing && isLandscape) {
                // Low screen: player and volume side by side to keep the belt short.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerRow(state = state, model = model, modifier = Modifier.weight(1f))
                    VolumeRow(
                        volume = state.volume,
                        isMuted = state.isMuted,
                        onVolumeChange = { model.setVolume(it) },
                        onToggleMute = { model.toggleMute() },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 24.dp)
                    )
                }
            } else if (state.isBeltShowing) {
                PlayerRow(state = state, model = model)
                VolumeRow(
                    volume = state.volume,
                    isMuted = state.isMuted,
                    onVolumeChange = { model.setVolume(it) },
                    onToggleMute = { model.toggleMute() },
                    modifier = Modifier.padding(start = 8.dp, end = 24.dp, bottom = 8.dp)
                )
            } else {
                ConnectedRow(state = state, onPlay = { model.radioCast(it, onNoSession) })
            }
        }
    }
}

@Composable
private fun PlayerRow(state: PlayerUiState, model: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sleepMinutes = rememberRemainingMinutes(state.sleepTimerEndsAt)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data(data = state.imageUri)
                    .placeholder(R.drawable.baseline_radio_24)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(500)
                    .build()
            ),
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .padding(start = 16.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp)
        ) {
            Text(
                style = AppTypography.titleLarge,
                text = state.stationName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                style = AppTypography.titleSmall,
                text = state.castDeviceName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SleepTimerButton(remainingMinutes = sleepMinutes, onSelect = { minutes ->
            model.setSleepTimer(minutes)
            // Without exact alarms the stop can come up to ~10 min late; let the user allow them.
            if (minutes != null && !SleepTimer.canScheduleExact(context)) {
                openExactAlarmSettings(context)
            }
        })
        IconButton(onClick = { model.stop() }) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.stop_24),
                contentDescription = stringResource(R.string.stop)
            )
        }
        Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            IconButton(onClick = { model.playPause() }) {
                if (state.status == PlaybackStatus.PLAYING || state.status == PlaybackStatus.PAUSED) {
                    Icon(
                        imageVector =
                            when (state.status) {
                                PlaybackStatus.PLAYING -> ImageVector.vectorResource(id = R.drawable.pause_24)
                                else -> ImageVector.vectorResource(id = R.drawable.play_24)
                            }, contentDescription = null, modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .clip(CircleShape)
                    )
                }
            }
            if (state.status == PlaybackStatus.BUFFERING) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Moon icon with a menu of timer lengths, badged with the minutes left while running.
 * [onSelect] gets the minutes, or null to turn the timer off.
 */
@Composable
private fun SleepTimerButton(remainingMinutes: Int?, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = remainingMinutes != null
    Box {
        IconButton(onClick = { expanded = true }) {
            BadgedBox(badge = {
                if (remainingMinutes != null) Badge { Text(remainingMinutes.toString()) }
            }) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.bedtime_24),
                    contentDescription = stringResource(R.string.sleep_timer),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isActive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_timer_off)) },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
            }
            SLEEP_TIMER_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_timer_minutes, minutes)) },
                    onClick = {
                        expanded = false
                        onSelect(minutes)
                    }
                )
            }
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
    )
}

/** Whole minutes left until [endsAt] (rounded up), refreshed while counting; null when off or past. */
@Composable
private fun rememberRemainingMinutes(endsAt: Long?): Int? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            if (endsAt == null || now >= endsAt) break
            delay(10_000)
        }
    }
    if (endsAt == null || endsAt <= now) return null
    return ((endsAt - now + 59_999) / 60_000).toInt()
}

@Composable
private fun ConnectedRow(state: PlayerUiState, onPlay: (RadioStation) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(style = AppTypography.titleSmall, text = stringResource(R.string.connected_to))
            Text(
                style = AppTypography.titleLarge,
                text = state.castDeviceName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        state.lastStation?.let { station ->
            FilledTonalButton(onClick = { onPlay(station) }) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.play_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = station.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun VolumeRow(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Local value while dragging, so the Cast device gets one request per gesture.
    var dragVolume by remember { mutableStateOf<Float?>(null) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleMute) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    id = if (isMuted) R.drawable.volume_off_24 else R.drawable.volume_up_24
                ),
                contentDescription = stringResource(if (isMuted) R.string.unmute else R.string.mute)
            )
        }
        Slider(
            value = dragVolume ?: volume,
            onValueChange = { dragVolume = it },
            onValueChangeFinished = {
                dragVolume?.let(onVolumeChange)
                dragVolume = null
            },
            enabled = !isMuted,
            modifier = Modifier.weight(1f)
        )
    }
}
