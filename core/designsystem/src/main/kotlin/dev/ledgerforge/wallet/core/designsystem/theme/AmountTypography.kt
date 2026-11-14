package dev.ledgerforge.wallet.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Text styles for money. Material's type scale is for words; these are for numbers. */
@Immutable
data class AmountTypography(
    /** A wallet's balance: the biggest number on screen. */
    val balance: TextStyle = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium),
    /** An amount at the end of a list row. */
    val row: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
)

internal val LocalAmountTypography = staticCompositionLocalOf { AmountTypography() }
