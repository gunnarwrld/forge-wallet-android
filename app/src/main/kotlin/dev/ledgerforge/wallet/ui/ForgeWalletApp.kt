package dev.ledgerforge.wallet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.ledgerforge.wallet.feature.home.HomeScreen
import dev.ledgerforge.wallet.feature.send.SendScreen
import kotlinx.serialization.Serializable

@Serializable
private data object HomeRoute : NavKey

@Serializable
private data object SendRoute : NavKey

/** App root. The back stack is a plain list this composable owns, and features never see it. */
@Composable
fun ForgeWalletApp(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeRoute)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> {
                    HomeScreen(onSendClick = { backStack.add(SendRoute) })
                }
                entry<SendRoute> {
                    SendScreen(
                        onQueued = { backStack.removeLastOrNull() },
                        onCancel = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
