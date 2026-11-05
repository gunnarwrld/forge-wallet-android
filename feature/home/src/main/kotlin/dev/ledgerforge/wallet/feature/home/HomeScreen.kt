package dev.ledgerforge.wallet.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import dev.ledgerforge.wallet.core.designsystem.component.AmountText
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import java.time.Instant

private const val MIN_WALLETS_TO_SEND = 2

@Composable
fun HomeScreen(
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state = state, onIntent = viewModel::onIntent, onSendClick = onSendClick, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    onSendClick: () -> Unit,
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
                WalletsAndActivity(state, contentModifier)
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
    val spacing = ForgeTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_no_wallets), textAlign = TextAlign.Center)
        Button(onClick = onCreate, enabled = demoWallets != DemoWalletsState.Creating) {
            val label =
                if (demoWallets ==
                    DemoWalletsState.Creating
                ) {
                    R.string.home_creating_demo_wallets
                } else {
                    R.string.home_create_demo_wallets
                }
            Text(stringResource(label))
        }
        if (demoWallets == DemoWalletsState.Failed) {
            Text(
                text = stringResource(R.string.home_create_failed),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WalletsAndActivity(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val spacing = ForgeTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // The bottom padding keeps the last row clear of the floating button.
        contentPadding = PaddingValues(start = spacing.md, top = spacing.sm, end = spacing.md, bottom = spacing.xl * 3),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(state.wallets, key = { it.id.value }) { wallet -> WalletCard(wallet) }
        item {
            Text(
                text = stringResource(R.string.home_activity),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = spacing.md),
            )
        }
        items(state.activity, key = { it.id }) { item -> ActivityRow(item) }
    }
}

@Composable
private fun WalletCard(wallet: Wallet) {
    val spacing = ForgeTheme.spacing
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(wallet.name, style = MaterialTheme.typography.titleMedium)
            AmountText(wallet.availableBalance, style = MaterialTheme.typography.headlineMedium)
            if (wallet.availableBalance != wallet.postedBalance) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(stringResource(R.string.home_posted), style = MaterialTheme.typography.bodySmall)
                    AmountText(wallet.postedBalance, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem) {
    val spacing = ForgeTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.accountName, style = MaterialTheme.typography.bodyLarge)
            StatusLabel(item.status)
        }
        AmountText(item.amount, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusLabel(status: ActivityStatus) {
    val label =
        when (status) {
            ActivityStatus.Pending -> R.string.activity_pending
            ActivityStatus.Settled -> R.string.activity_settled
            ActivityStatus.Posted -> null
            is ActivityStatus.Failed -> status.reason.label()
        }
    if (label != null) {
        val color = if (status is ActivityStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private fun TransferFailure.label(): Int =
    when (this) {
        TransferFailure.InsufficientFunds -> R.string.activity_failed_funds
        TransferFailure.AccountNotFound -> R.string.activity_failed_account
        TransferFailure.Rejected -> R.string.activity_failed_rejected
        TransferFailure.Conflict -> R.string.activity_failed_conflict
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
                            ActivityItem("2", "Alice", Money(100_000, sek), Instant.EPOCH, ActivityStatus.Posted),
                        ),
                ),
            onIntent = {},
            onSendClick = {},
        )
    }
}
