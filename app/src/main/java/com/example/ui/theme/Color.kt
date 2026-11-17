package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Invox / Meher Basan Bhandar Design Tokens (DRD specifications)
val PrimaryBlue = Color(0xFF0625FA)
val PrimaryBlueVariant = Color(0xFF3855FF)
val BackgroundLight = Color(0xFFF5F5F2)
val SurfaceLight = Color(0xFFFFFFFF)
val RawTextPrimaryLight = Color(0xFF0A0505)
val RawTextSecondaryLight = Color(0xFF5A606D)
val SuccessGreen = Color(0xFF2E7D32)
val WarningOrange = Color(0xFFE1522C)
val UdharColor = Color(0xFFD32F2F) // High-contrast Red for Udhar / Credit Pay Later
val UpiColor = Color(0xFF00897B)   // High-contrast Teal for UPI
val CashColor = Color(0xFF1E88E5)  // Blue for Cash
val RawBorderLight = Color(0xFFE2E4E9)

// Dark theme complementary tokens
val BackgroundDark = Color(0xFF121214)
val SurfaceDark = Color(0xFF1E1E24)
val SurfaceDarkElevated = Color(0xFF282832)
val TextPrimaryDark = Color(0xFFF9FAFB)
val TextSecondaryDark = Color(0xFFCBD5E1)
val BorderDark = Color(0xFF3B3B48)

// Dynamic theme-aware accessors so all composables automatically adapt to light & dark themes
val TextPrimary: Color
  @Composable
  get() = MaterialTheme.colorScheme.onSurface

val TextSecondary: Color
  @Composable
  get() = MaterialTheme.colorScheme.onSurfaceVariant

val BorderLight: Color
  @Composable
  get() = MaterialTheme.colorScheme.outline
