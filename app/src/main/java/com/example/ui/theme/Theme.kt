package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
  primary = PrimaryBlueVariant,
  onPrimary = SurfaceLight,
  primaryContainer = PrimaryBlue,
  onPrimaryContainer = SurfaceLight,
  background = BackgroundDark,
  onBackground = TextPrimaryDark,
  surface = SurfaceDark,
  onSurface = TextPrimaryDark,
  surfaceVariant = SurfaceDarkElevated,
  onSurfaceVariant = TextSecondaryDark,
  outline = BorderDark,
  outlineVariant = BorderDark,
  error = WarningOrange
)

private val LightColorScheme = lightColorScheme(
  primary = PrimaryBlue,
  onPrimary = SurfaceLight,
  primaryContainer = PrimaryBlueVariant,
  onPrimaryContainer = SurfaceLight,
  background = BackgroundLight,
  onBackground = RawTextPrimaryLight,
  surface = SurfaceLight,
  onSurface = RawTextPrimaryLight,
  surfaceVariant = BackgroundLight,
  onSurfaceVariant = RawTextSecondaryLight,
  outline = RawBorderLight,
  outlineVariant = RawBorderLight,
  error = WarningOrange
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
