package dev.ledgerforge.wallet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.domain.model.SyncRequester
import dev.ledgerforge.wallet.ui.ForgeWalletApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sync: SyncRequester

    private val localNetworkAccess =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // A sync may already be backing off after timing out, so don't wait for it.
            if (granted) lifecycleScope.launch { sync.syncNow() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestLocalNetworkAccessForDebugBuild()
        lifecycleScope.launch {
            // Coming to the foreground is a good moment to send what's queued and refresh balances.
            repeatOnLifecycle(Lifecycle.State.STARTED) { sync.requestSync() }
        }
        setContent {
            ForgeTheme {
                ForgeWalletApp()
            }
        }
    }

    /** See app/src/debug/AndroidManifest.xml. */
    private fun requestLocalNetworkAccessForDebugBuild() {
        if (!BuildConfig.DEBUG || Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_API) return
        val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            localNetworkAccess.launch(permission)
        }
    }

    private companion object {
        const val LOCAL_NETWORK_PERMISSION_API = 37
    }
}
