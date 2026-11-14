package dev.ledgerforge.wallet.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's Material 3 theme. Dynamic colour is off on purpose: a wallet keeps its brand
 * colours on every device, so screenshots and screenshot tests look the same everywhere.
 */
@Composable
fun ForgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalAmountTypography provides AmountTypography(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColors else lightColors,
            shapes = forgeShapes,
            content = content,
        )
    }
}

/** Design tokens that Material 3 has no slot for. */
object ForgeTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val amounts: AmountTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAmountTypography.current
}
