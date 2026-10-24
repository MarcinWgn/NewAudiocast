package com.wegrzyn.marcin.newaudiocast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/** The whole app screen; [onNoSession] opens the Cast device picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(model: MainViewModel, onNoSession: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val density = LocalDensity.current
    var beltHeight by remember { mutableStateOf(0.dp) }
    // Bar stays put; its color changes once the list scrolls under it.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        model.events.collect { event ->
            when (event) {
                is PlayerEvent.PlaybackFailed -> snackbarHostState.showSnackbar(
                    resources.getString(R.string.playback_failed, event.stationName)
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            scrollBehavior = scrollBehavior,
            expandedHeight = 48.dp,
            actions = {
                Box(modifier = Modifier.padding(end = 8.dp)) {
                    CastButton()
                }
            }
        )
        // In landscape the navigation bar and camera cutout sit on the sides.
        Box(
            modifier = Modifier
                .weight(1f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
        ) {
            StationGrid(
                stations = Stations.stationsList,
                bottomSpace = beltHeight,
                onStationClick = { model.radioCast(it, onNoSession) },
                modifier = Modifier.fillMaxHeight()
            )
            Column(
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.fillMaxHeight()
            ) {
                SnackbarHost(hostState = snackbarHostState)
                ControlBelt(
                    state = state,
                    model = model,
                    onNoSession = onNoSession,
                    modifier = Modifier.onSizeChanged {
                        beltHeight = with(density) { it.height.toDp() }
                    }
                )
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
            }
        }
    }
}

@Composable
private fun CastButton() {
    AndroidView(factory = { context ->
        MediaRouteButton(context).also { CastButtonFactory.setUpMediaRouteButton(context, it) }
    })
}
