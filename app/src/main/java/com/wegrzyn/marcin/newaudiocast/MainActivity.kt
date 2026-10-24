package com.wegrzyn.marcin.newaudiocast

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.mediarouter.app.MediaRouteChooserDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private val vm: MainViewModel by viewModels()

    companion object {

        /** Station name to play on launch; set by the schedule notification. */
        const val EXTRA_STATION = "station"

        private const val VOLUME_STEP = 0.05f

        // How long a station from the notification waits for the Cast session to resume.
        private const val SESSION_RESUME_WAIT_MS = 3_000L

        // Runtime permission since Android 17; without it Cast device discovery finds nothing.
        private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        private const val LOCAL_NETWORK_PERMISSION_SDK = 37
    }

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocalNetworkPermission()
        if (savedInstanceState == null) playFromIntent(intent)

        setContent {
            AppTheme {
                MainScreen(model = vm, onNoSession = ::showDeviceChooser)
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        playFromIntent(intent)
    }

    private fun playFromIntent(intent: Intent) {
        val station = intent.getStringExtra(EXTRA_STATION)?.let { Stations.byName(it) } ?: return
        intent.removeExtra(EXTRA_STATION)
        lifecycleScope.launch {
            vm.castReady.first { it }
            // Right after launch the framework may still be resuming the session.
            withTimeoutOrNull(SESSION_RESUME_WAIT_MS) { vm.uiState.first { it.isConnected } }
            vm.radioCast(station, ::showDeviceChooser)
        }
    }

    private fun requestLocalNetworkPermission() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) return
        if (checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) return
        localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
    }

    // Opens the Cast device picker; falls back to a toast when Cast is unavailable.
    private fun showDeviceChooser() {
        val selector = vm.routeSelector
        if (selector == null) {
            val tst = Toast.makeText(this, R.string.cast_unavailable, Toast.LENGTH_SHORT)
            tst.setGravity(Gravity.CENTER, 0, 0)
            tst.show()
            return
        }
        MediaRouteChooserDialog(this).apply {
            routeSelector = selector
            // Cancel = closed without picking a device; picking one only dismisses.
            setOnCancelListener { vm.clearPendingStation() }
        }.show()
    }

    // Hardware volume keys control the Cast device while a session is connected.
    // Consuming the event here keeps the system from also adjusting the Cast media session volume.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val delta = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VOLUME_STEP
            KeyEvent.KEYCODE_VOLUME_DOWN -> -VOLUME_STEP
            else -> return super.dispatchKeyEvent(event)
        }
        if (!vm.isCasting) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) vm.adjustVolume(delta)
        return true
    }
}
