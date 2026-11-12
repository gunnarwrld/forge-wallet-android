package dev.ledgerforge.wallet.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import dev.ledgerforge.wallet.core.designsystem.component.TransactionRow
import dev.ledgerforge.wallet.core.domain.model.ActivityItem
import dev.ledgerforge.wallet.core.domain.model.ActivityStatus
import dev.ledgerforge.wallet.core.domain.model.TransferFailure

/** An [ActivityItem] as a row, with its status in words. Posted lines need no status. */
@Composable
fun ActivityItemRow(
    item: ActivityItem,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val label =
        when (val status = item.status) {
            ActivityStatus.Pending -> R.string.activity_pending
            ActivityStatus.Settled -> R.string.activity_settled
            ActivityStatus.Posted -> null
            is ActivityStatus.Failed -> status.reason.label()
        }
    TransactionRow(
        title = item.accountName,
        amount = item.amount,
        modifier = modifier,
        supportingText = label?.let { stringResource(it) },
        supportingColor = if (item.status is ActivityStatus.Failed) MaterialTheme.colorScheme.error else Color.Unspecified,
        actions = actions,
    )
}

private fun TransferFailure.label(): Int =
    when (this) {
        TransferFailure.InsufficientFunds -> R.string.activity_failed_funds
        TransferFailure.AccountNotFound -> R.string.activity_failed_account
        TransferFailure.Rejected -> R.string.activity_failed_rejected
        TransferFailure.Conflict -> R.string.activity_failed_conflict
    }
