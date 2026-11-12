package dev.ledgerforge.wallet.feature.activity

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ledgerforge.wallet.core.designsystem.component.EmptyState
import dev.ledgerforge.wallet.core.designsystem.icon.ForgeIcons
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.TransferFailure
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money
import dev.ledgerforge.wallet.core.ui.ActivityItemRow
import java.time.Instant

@Composable
fun ActivityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ActivityContent(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityContent(
    state: ActivityUiState,
    onIntent: (ActivityIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it.text()) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onIntent(ActivityIntent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(ForgeIcons.Back, contentDescription = stringResource(R.string.activity_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        if (!state.isLoading) {
            if (state.items.isEmpty()) {
                EmptyState(message = stringResource(R.string.activity_empty), modifier = Modifier.padding(innerPadding))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = ForgeTheme.spacing.md),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        ActivityItemRow(
                            item = item,
                            actions = if (item.status is ActivityStatus.Failed) failedActions(item.id, onIntent) else null,
                        )
                    }
                }
            }
        }
    }
}

private fun failedActions(
    id: String,
    onIntent: (ActivityIntent) -> Unit,
): @Composable RowScope.() -> Unit =
    {
        TextButton(onClick = { onIntent(ActivityIntent.Dismiss(id)) }) { Text(stringResource(R.string.activity_dismiss)) }
        TextButton(onClick = { onIntent(ActivityIntent.Retry(id)) }) { Text(stringResource(R.string.activity_retry)) }
    }

private fun ActivityMessage.text(): Int =
    when (this) {
        ActivityMessage.RetryQueued -> R.string.activity_retry_queued
        ActivityMessage.RetryNeedsFunds -> R.string.activity_retry_needs_funds
    }

@Preview
@Composable
private fun ActivityContentPreview() {
    val sek = CurrencyCode.of("SEK")
    ForgeTheme {
        ActivityContent(
            state =
                ActivityUiState(
                    isLoading = false,
                    items =
                        listOf(
                            ActivityItem(
                                "1",
                                "Alice",
                                Money(-400, sek),
                                Instant.EPOCH,
                                ActivityStatus.Failed(TransferFailure.AccountNotFound),
                            ),
                            ActivityItem("2", "Alice", Money(-1_250, sek), Instant.EPOCH, ActivityStatus.Pending),
                            ActivityItem("3", "Bob", Money(700, sek), Instant.EPOCH, ActivityStatus.Posted),
                        ),
                ),
            onIntent = {},
            onBack = {},
        )
    }
}
