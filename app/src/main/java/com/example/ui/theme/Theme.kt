package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = CobaltPrimaryDark,
  onPrimary = TextPrimaryDark,
  primaryContainer = RoyalNavyContainerDark,
  onPrimaryContainer = TextPrimaryDark,
  secondary = CashColorDark,
  onSecondary = BackgroundDark,
  background = BackgroundDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceDarkElevated,
  onSurfaceVariant = TextSecondaryDark,
  outline = BorderDark,
  outlineVariant = BorderDark,
  error = WarningOrangeDark,
  onError = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
  primary = RoyalNavyLight,
  onPrimary = SurfaceLight,
  primaryContainer = CobaltAccentLight,
  onPrimaryContainer = SurfaceLight,
  secondary = CashColorLight,
  onSecondary = SurfaceLight,
  background = BackgroundLight,
  onBackground = RawTextPrimaryLight,
  surface = SurfaceLight,
  onSurface = RawTextPrimaryLight,
  surfaceVariant = SurfaceVariantLight,
  onSurfaceVariant = RawTextSecondaryLight,
  outline = RawBorderLight,
  outlineVariant = RawBorderLight,
  error = WarningOrangeLight,
  onError = SurfaceLight
)

@Composable
fun BillingAppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    content = content
  )
}

@Composable
fun MeherBasanTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) = BillingAppTheme(darkTheme = darkTheme, content = content)
