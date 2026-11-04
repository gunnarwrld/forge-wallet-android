package dev.ledgerforge.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
}
