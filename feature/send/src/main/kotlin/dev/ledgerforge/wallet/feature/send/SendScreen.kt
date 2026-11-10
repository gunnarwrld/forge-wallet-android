package dev.ledgerforge.wallet.feature.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ledgerforge.wallet.core.designsystem.component.AmountText
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.domain.model.AccountId
import dev.ledgerforge.wallet.core.domain.model.Wallet
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money

@Composable
fun SendScreen(
    onQueued: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnQueued by rememberUpdatedState(onQueued)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { currentOnQueued() }
    }
    SendContent(state = state, onIntent = viewModel::onIntent, onCancel = onCancel, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SendContent(
    state: SendUiState,
    onIntent: (SendIntent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ForgeTheme.spacing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.send_title)) },
                actions = {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.send_cancel)) }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            WalletPicker(
                label = stringResource(R.string.send_from),
                wallets = state.wallets,
                selected = state.source,
                onSelect = { onIntent(SendIntent.SelectSource(it)) },
            )
            state.source?.let { source ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(stringResource(R.string.send_available), style = MaterialTheme.typography.bodyMedium)
                    AmountText(source.availableBalance, style = MaterialTheme.typography.bodyMedium)
                }
            }
            WalletPicker(
                label = stringResource(R.string.send_to),
                wallets = state.wallets,
                selected = state.destination,
                onSelect = { onIntent(SendIntent.SelectDestination(it)) },
            )
            OutlinedTextField(
                value = state.amountText,
                onValueChange = { onIntent(SendIntent.EditAmount(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.send_amount)) },
                suffix = { state.source?.let { Text(it.availableBalance.currency.code) } },
                isError = state.error != null,
                supportingText = { state.error?.let { Text(stringResource(it.message())) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Button(
                onClick = { onIntent(SendIntent.Submit) },
                enabled = state.source != null && state.destination != null && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.send_submit))
            }
            Text(
                text = stringResource(R.string.send_works_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WalletPicker(
    label: String,
    wallets: List<Wallet>,
    selected: Wallet?,
    onSelect: (AccountId) -> Unit,
) {
    val spacing = ForgeTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            wallets.forEach { wallet ->
                FilterChip(
                    selected = wallet == selected,
                    onClick = { onSelect(wallet.id) },
                    label = { Text(wallet.name) },
                )
            }
        }
    }
}

private fun SendError.message(): Int =
    when (this) {
        SendError.AmountEmpty -> R.string.send_error_empty
        SendError.AmountInvalid -> R.string.send_error_invalid
        SendError.TooManyDecimals -> R.string.send_error_decimals
        SendError.AmountTooLarge -> R.string.send_error_too_large
        SendError.AmountNotPositive -> R.string.send_error_not_positive
        SendError.InsufficientFunds -> R.string.send_error_funds
        SendError.SameWallet -> R.string.send_error_same_wallet
        SendError.CurrencyMismatch -> R.string.send_error_currency
    }

@Preview
@Composable
private fun SendContentPreview() {
    val sek = CurrencyCode.of("SEK")
    val alice = Wallet(AccountId("alice"), "Alice", Money(100_000, sek), Money(98_750, sek))
    val bob = Wallet(AccountId("bob"), "Bob", Money(25_000, sek), Money(25_000, sek))
    ForgeTheme {
        SendContent(
            state = SendUiState(listOf(alice, bob), alice, bob, amountText = "12.50"),
            onIntent = {},
            onCancel = {},
        )
    }
}
