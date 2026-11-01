package com.m3browser.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.m3browser.AppSettings
import com.m3browser.ThemeMode

private fun Color.mix(other: Color, amount: Float): Color = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
)

private fun schemeFromSeed(seed: Color, dark: Boolean) = if (!dark) {
    lightColorScheme(
        primary = seed,
        onPrimary = Color.White,
        primaryContainer = seed.mix(Color.White, 0.78f),
        onPrimaryContainer = seed.mix(Color.Black, 0.62f),
        secondary = seed.mix(Color(0xFF5E5E5E), 0.45f),
        onSecondary = Color.White,
        secondaryContainer = seed.mix(Color.White, 0.88f),
        onSecondaryContainer = seed.mix(Color.Black, 0.72f),
        tertiary = Color(
            red = (seed.blue * 0.55f + 0.25f).coerceIn(0f, 1f),
            green = (seed.green * 0.45f).coerceIn(0f, 1f),
            blue = (seed.red * 0.65f + 0.1f).coerceIn(0f, 1f),
        ),
        onTertiary = Color.White,
        tertiaryContainer = seed.mix(Color(0xFFFFE4EE), 0.55f),
        onTertiaryContainer = seed.mix(Color.Black, 0.7f),
        background = Color(0xFFFFFBFF),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = seed.mix(Color(0xFFE7E0EC), 0.7f),
        onSurfaceVariant = Color(0xFF49454F),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = seed.mix(Color.White, 0.94f),
        surfaceContainer = seed.mix(Color.White, 0.90f),
        surfaceContainerHigh = seed.mix(Color.White, 0.86f),
        surfaceContainerHighest = seed.mix(Color.White, 0.82f),
        outline = seed.mix(Color(0xFF79747E), 0.4f),
    )
} else {
    val primary = seed.mix(Color.White, 0.42f)
    darkColorScheme(
        primary = primary,
        onPrimary = seed.mix(Color.Black, 0.55f),
        primaryContainer = seed.mix(Color.Black, 0.45f),
        onPrimaryContainer = seed.mix(Color.White, 0.78f),
        secondary = primary.mix(Color(0xFFCCC4D6), 0.55f),
        onSecondary = Color(0xFF332D41),
        secondaryContainer = seed.mix(Color.Black, 0.62f),
        onSecondaryContainer = Color(0xFFE8DEF8),
        tertiary = primary.mix(Color(0xFFFFB0CB), 0.4f),
        onTertiary = Color(0xFF5E1138),
        tertiaryContainer = seed.mix(Color.Black, 0.5f),
        onTertiaryContainer = Color(0xFFFFD8E4),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF141218),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        surfaceContainerLowest = Color(0xFF0F0D13),
        surfaceContainerLow = Color(0xFF1D1B20),
        surfaceContainer = Color(0xFF211F26),
        surfaceContainerHigh = Color(0xFF2B2930),
        surfaceContainerHighest = Color(0xFF36343B),
        outline = Color(0xFF948F99),
    )
}

private fun androidx.compose.material3.ColorScheme.amoled(): androidx.compose.material3.ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3BrowserTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val ctx = LocalContext.current
    val monetAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.monet
    var scheme = if (monetAvailable) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        schemeFromSeed(Color(settings.seed.toInt()), dark)
    }
    if (dark && settings.amoled) scheme = scheme.amoled()
    val view = LocalView.current
    if (!view.isInEditMode) {
        val lightIcons = scheme.surface.luminance() > 0.5f
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}
