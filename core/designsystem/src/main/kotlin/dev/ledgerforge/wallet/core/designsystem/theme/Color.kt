package dev.ledgerforge.wallet.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// A trust blue for primary actions, slate for secondary, and an ember accent for the forge.
// Every on-colour pair clears WCAG AA (4.5:1) for body text.
private val blue10 = Color(0xFF00164F)
private val blue20 = Color(0xFF002479)
private val blue30 = Color(0xFF1435B8)
private val blue40 = Color(0xFF2A4BD7)
private val blue80 = Color(0xFFB8C4FF)
private val blue90 = Color(0xFFDDE1FF)
private val slate10 = Color(0xFF151B2C)
private val slate20 = Color(0xFF2A3042)
private val slate30 = Color(0xFF404659)
private val slate40 = Color(0xFF575E71)
private val slate80 = Color(0xFFBFC6DC)
private val slate90 = Color(0xFFDBE2F9)
private val ember10 = Color(0xFF380D00)
private val ember20 = Color(0xFF5B1A00)
private val ember30 = Color(0xFF802A00)
private val ember40 = Color(0xFFA63B0F)
private val ember80 = Color(0xFFFFB59A)
private val ember90 = Color(0xFFFFDBCE)

internal val lightColors =
    lightColorScheme(
        primary = blue40,
        onPrimary = Color.White,
        primaryContainer = blue90,
        onPrimaryContainer = blue10,
        secondary = slate40,
        onSecondary = Color.White,
        secondaryContainer = slate90,
        onSecondaryContainer = slate10,
        tertiary = ember40,
        onTertiary = Color.White,
        tertiaryContainer = ember90,
        onTertiaryContainer = ember10,
    )

internal val darkColors =
    darkColorScheme(
        primary = blue80,
        onPrimary = blue20,
        primaryContainer = blue30,
        onPrimaryContainer = blue90,
        secondary = slate80,
        onSecondary = slate20,
        secondaryContainer = slate30,
        onSecondaryContainer = slate90,
        tertiary = ember80,
        onTertiary = ember20,
        tertiaryContainer = ember30,
        onTertiaryContainer = ember90,
    )
