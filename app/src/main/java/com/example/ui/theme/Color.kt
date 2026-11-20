package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Meher Basan Bhandar Brand & Theme Tokens (harmonized with Royal Navy & Cobalt logo)
val RoyalNavyLight = Color(0xFF0A3B99)
val CobaltAccentLight = Color(0xFF145AE6)
val CobaltPrimaryDark = Color(0xFF366EF5)
val RoyalNavyContainerDark = Color(0xFF0A2E7A)

val BackgroundLight = Color(0xFFF5F5F2)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFECECE7)
val RawTextPrimaryLight = Color(0xFF171717)
val RawTextSecondaryLight = Color(0xFF5F6673)
val RawBorderLight = Color(0xFFE2E4E9)

// Dark theme surface & typography tokens
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1E1E24)
val SurfaceDarkElevated = Color(0xFF282832)
val TextPrimaryDark = Color(0xFFF9FAFB)
val TextSecondaryDark = Color(0xFFCBD5E1)
val BorderDark = Color(0xFF3B3B48)

// Theme-adaptive semantic financial colors
val SuccessGreenLight = Color(0xFF1E7E34)
val SuccessGreenDark = Color(0xFF2B9348)

val WarningOrangeLight = Color(0xFFD9481C)
val WarningOrangeDark = Color(0xFFE86338)

val UdharColorLight = Color(0xFFC62828)
val UdharColorDark = Color(0xFFEF5350)

val UpiColorLight = Color(0xFF00796B)
val UpiColorDark = Color(0xFF26A69A)

val CashColorLight = Color(0xFF1565C0)
val CashColorDark = Color(0xFF42A5F5)

// Dynamic theme-aware accessors so all composables adapt cleanly between Light & Dark modes
val PrimaryBlue: Color
  @Composable
  get() = MaterialTheme.colorScheme.primary

val PrimaryNavyHero: Color
  @Composable
  get() = if (isSystemInDarkTheme()) RoyalNavyContainerDark else RoyalNavyLight

val BrandCobaltAccent: Color
  @Composable
  get() = if (isSystemInDarkTheme()) CobaltPrimaryDark else CobaltAccentLight

val SuccessGreen: Color
  @Composable
  get() = if (isSystemInDarkTheme()) SuccessGreenDark else SuccessGreenLight

val WarningOrange: Color
  @Composable
  get() = MaterialTheme.colorScheme.error

val UdharColor: Color
  @Composable
  get() = if (isSystemInDarkTheme()) UdharColorDark else UdharColorLight

val UpiColor: Color
  @Composable
  get() = if (isSystemInDarkTheme()) UpiColorDark else UpiColorLight

val CashColor: Color
  @Composable
  get() = if (isSystemInDarkTheme()) CashColorDark else CashColorLight

val TextPrimary: Color
  @Composable
  get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
  @Composable
  get() = MaterialTheme.colorScheme.onSurfaceVariant

val BorderLight: Color
  @Composable
  get() = MaterialTheme.colorScheme.outline
