package dev.ledgerforge.wallet.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ledgerforge.wallet.core.designsystem.component.BalanceCard
import dev.ledgerforge.wallet.core.designsystem.component.EmptyState
import dev.ledgerforge.wallet.core.designsystem.component.PrimaryButton
import dev.ledgerforge.wallet.core.designsystem.component.SectionHeader
import dev.ledgerforge.wallet.core.designsystem.component.formatAmount
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.ui.ActivityItemRow
import java.time.Instant

private const val MIN_WALLETS_TO_SEND = 2
private const val RECENT_ACTIVITY = 5

@Composable
fun HomeScreen(
    onSendClick: () -> Unit,
    onSeeAllActivity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onIntent = viewModel::onIntent,
        onSendClick = onSendClick,
        onSeeAllActivity = onSeeAllActivity,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onSendClick: () -> Unit,
    onSeeAllActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    TextButton(onClick = { onIntent(HomeIntent.Refresh) }) {
                        Text(stringResource(R.string.home_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.wallets.size >= MIN_WALLETS_TO_SEND) {
                ExtendedFloatingActionButton(onClick = onSendClick) {
                    Text(stringResource(R.string.home_send))
                }
            }
        },
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        if (!state.isLoading) {
            if (state.wallets.isEmpty()) {
                NoWallets(state.demoWallets, onCreate = { onIntent(HomeIntent.CreateDemoWallets) }, contentModifier)
            } else {
                WalletsAndActivity(state, onSeeAllActivity, contentModifier)
            }
        }
    }
}

@Composable
private fun NoWallets(
    demoWallets: DemoWalletsState,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(message = stringResource(R.string.home_no_wallets), modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ForgeTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(
                text = stringResource(R.string.home_create_demo_wallets),
                onClick = onCreate,
                loading = demoWallets == DemoWalletsState.Creating,
            )
            if (demoWallets == DemoWalletsState.Failed) {
                Text(
                    text = stringResource(R.string.home_create_failed),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WalletsAndActivity(
    state: HomeUiState,
    onSeeAllActivity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ForgeTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The bottom padding keeps the last row clear of the floating button.
        contentPadding = PaddingValues(start = spacing.md, top = spacing.sm, end = spacing.md, bottom = spacing.xl * 3),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(state.wallets, key = { it.id.value }) { wallet ->
            BalanceCard(name = wallet.name, balance = wallet.availableBalance, supportingText = pendingText(wallet))
        }
        item {
            SectionHeader(
                title = stringResource(R.string.home_activity),
                modifier = Modifier.padding(top = spacing.md),
                actionLabel = if (state.activity.isNotEmpty()) stringResource(R.string.home_see_all) else null,
                onAction = onSeeAllActivity,
            )
        }
        if (state.activity.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_no_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.activity.take(RECENT_ACTIVITY), key = { it.id }) { item -> ActivityItemRow(item) }
    }
}

/** The posted balance minus what's spendable is money on its way out. */
@Composable
private fun pendingText(wallet: Wallet): String? {
    val pending = wallet.postedBalance - wallet.availableBalance
    return if (pending.isPositive) stringResource(R.string.home_pending, formatAmount(pending)) else null
}

@Preview
@Composable
private fun HomeContentPreview() {
    val sek = CurrencyCode.of("SEK")
    ForgeTheme {
        HomeContent(
            state =
                HomeUiState(
                    isLoading = false,
                    wallets =
                        listOf(
                            Wallet(AccountId("alice"), "Alice", Money(100_000, sek), Money(98_750, sek)),
                            Wallet(AccountId("bob"), "Bob", Money(25_000, sek), Money(25_000, sek)),
                        ),
                    activity =
                        listOf(
                            ActivityItem("1", "Alice", Money(-1_250, sek), Instant.EPOCH, ActivityStatus.Pending),
                            ActivityItem("2", "Bob", Money(700, sek), Instant.EPOCH, ActivityStatus.Posted),
                        ),
                ),
            onIntent = {},
            onSendClick = {},
            onSeeAllActivity = {},
        )
    }
}
