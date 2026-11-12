package dev.ledgerforge.wallet.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.ledgerforge.wallet.core.designsystem.theme.ForgeTheme
import dev.ledgerforge.wallet.core.money.CurrencyCode
import dev.ledgerforge.wallet.core.money.Money

private val MonogramSize = 40.dp

/**
 * One line of money moving: who, an optional status line, and a signed amount. Money coming in
 * is tinted; money going out isn't, so the eye goes to arrivals. [actions] sit under the row.
 */
@Composable
fun TransactionRow(
    title: String,
    amount: Money,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    supportingColor: Color = Color.Unspecified,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val spacing = ForgeTheme.spacing
    Column(modifier = modifier.fillMaxWidth().padding(vertical = spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Monogram(title)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (supportingText != null) {
                    val color = if (supportingColor == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else supportingColor
                    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
            AmountText(
                money = amount,
                style = ForgeTheme.amounts.row,
                color = if (amount.isPositive) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = MonogramSize + spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
                content = actions,
            )
        }
    }
}

/** Decorative: the name next to it already says who, so screen readers skip it. */
@Composable
private fun Monogram(title: String) {
    Box(
        modifier =
            Modifier
                .size(MonogramSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransactionRowPreview() {
    val sek = CurrencyCode.of("SEK")
    ForgeTheme {
        Column {
            TransactionRow(title = "Bob", amount = Money(700, sek))
            TransactionRow(title = "Alice", amount = Money(-1_250, sek), supportingText = "Pending")
            TransactionRow(
                title = "Alice",
                amount = Money(-400, sek),
                supportingText = "Failed: account not found",
                supportingColor = MaterialTheme.colorScheme.error,
                actions = { TextButton(onClick = {}) { Text("Retry") } },
            )
        }
    }
}
