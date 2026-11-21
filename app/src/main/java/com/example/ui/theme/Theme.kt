package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val VaultDarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF032219),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = AmberGold,
    onSecondary = Color(0xFF2B1700),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnContainer,
    tertiary = SapphireAccent,
    onTertiary = Color(0xFF042536),
    tertiaryContainer = SapphireContainer,
    onTertiaryContainer = Color(0xFFBAE6FD),
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonOnContainer,
    background = VaultObsidian,
    onBackground = SlateTextPrimary,
    surface = VaultSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = VaultSurfaceElevated,
    onSurfaceVariant = SlateTextSecondary,
    surfaceContainerLowest = VaultObsidian,
    surfaceContainerLow = VaultSurface,
    surfaceContainer = VaultSurfaceElevated,
    surfaceContainerHigh = VaultSurfaceHighest,
    outline = VaultBorder,
    outlineVariant = Color(0xFF1E293B),
  )

private val VaultLightColorScheme =
  lightColorScheme(
    primary = LightEmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = LightEmeraldContainer,
    onPrimaryContainer = LightEmeraldOnContainer,
    secondary = LightAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightAmberContainer,
    onSecondaryContainer = LightAmberOnContainer,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF881337),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFDCE5E2),
  )

val NoteBankShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun NoteBankTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> VaultDarkColorScheme
      else -> VaultLightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = NoteBankTypography,
    shapes = NoteBankShapes,
    content = content,
  )
}
