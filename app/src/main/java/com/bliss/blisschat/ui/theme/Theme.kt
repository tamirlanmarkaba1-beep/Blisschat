package com.bliss.blisschat.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat

private fun hsl(h: Float, s: Float, l: Float): Color =
    Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/** Builds a full M3 colour scheme from ONE hue, so the user can pick any colour of the spectrum. */
fun schemeFor(hue: Float, dark: Boolean): ColorScheme {
    val h2 = hue + 40f   // slate-blue companion (headers, input bar)
    val h3 = hue + 110f
    return if (dark) darkColorScheme(
        primary = hsl(hue, 0.62f, 0.70f), onPrimary = hsl(hue, 0.85f, 0.10f),
        primaryContainer = hsl(hue, 0.90f, 0.17f), onPrimaryContainer = hsl(hue, 0.80f, 0.88f),
        inversePrimary = hsl(hue, 0.65f, 0.38f),
        secondary = hsl(h2, 0.40f, 0.74f), onSecondary = hsl(h2, 0.45f, 0.13f),
        secondaryContainer = hsl(h2, 0.36f, 0.27f), onSecondaryContainer = hsl(h2, 0.45f, 0.90f),
        tertiary = hsl(h3, 0.48f, 0.76f), onTertiary = hsl(h3, 0.60f, 0.14f),
        tertiaryContainer = hsl(h3, 0.36f, 0.27f), onTertiaryContainer = hsl(h3, 0.60f, 0.90f),
        background = hsl(hue, 0.20f, 0.065f), onBackground = hsl(hue, 0.10f, 0.91f),
        surface = hsl(hue, 0.20f, 0.065f), onSurface = hsl(hue, 0.10f, 0.91f),
        surfaceVariant = hsl(hue, 0.12f, 0.21f), onSurfaceVariant = hsl(hue, 0.12f, 0.78f),
        surfaceTint = hsl(hue, 0.62f, 0.70f),
        inverseSurface = hsl(hue, 0.08f, 0.90f), inverseOnSurface = hsl(hue, 0.14f, 0.14f),
        outline = hsl(hue, 0.08f, 0.58f), outlineVariant = hsl(hue, 0.10f, 0.27f),
        surfaceDim = hsl(hue, 0.18f, 0.05f), surfaceBright = hsl(hue, 0.12f, 0.22f),
        surfaceContainerLowest = hsl(hue, 0.18f, 0.045f), surfaceContainerLow = hsl(hue, 0.15f, 0.09f),
        surfaceContainer = hsl(hue, 0.14f, 0.125f), surfaceContainerHigh = hsl(hue, 0.12f, 0.18f),
        surfaceContainerHighest = hsl(hue, 0.10f, 0.22f)
    ) else lightColorScheme(
        primary = hsl(hue, 0.70f, 0.30f), onPrimary = Color.White,
        primaryContainer = hsl(hue, 0.75f, 0.84f), onPrimaryContainer = hsl(hue, 0.85f, 0.10f),
        inversePrimary = hsl(hue, 0.62f, 0.70f),
        secondary = hsl(h2, 0.30f, 0.34f), onSecondary = Color.White,
        secondaryContainer = hsl(h2, 0.45f, 0.86f), onSecondaryContainer = hsl(h2, 0.50f, 0.11f),
        tertiary = hsl(h3, 0.35f, 0.34f), onTertiary = Color.White,
        tertiaryContainer = hsl(h3, 0.55f, 0.88f), onTertiaryContainer = hsl(h3, 0.60f, 0.12f),
        background = hsl(hue, 0.30f, 0.975f), onBackground = hsl(hue, 0.15f, 0.10f),
        surface = hsl(hue, 0.30f, 0.975f), onSurface = hsl(hue, 0.15f, 0.10f),
        surfaceVariant = hsl(hue, 0.25f, 0.89f), onSurfaceVariant = hsl(hue, 0.15f, 0.28f),
        surfaceTint = hsl(hue, 0.70f, 0.30f),
        inverseSurface = hsl(hue, 0.10f, 0.19f), inverseOnSurface = hsl(hue, 0.20f, 0.94f),
        outline = hsl(hue, 0.08f, 0.45f), outlineVariant = hsl(hue, 0.15f, 0.78f),
        surfaceDim = hsl(hue, 0.20f, 0.86f), surfaceBright = hsl(hue, 0.30f, 0.975f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(hue, 0.30f, 0.95f),
        surfaceContainer = hsl(hue, 0.28f, 0.93f), surfaceContainerHigh = hsl(hue, 0.26f, 0.91f),
        surfaceContainerHighest = hsl(hue, 0.24f, 0.89f)
    )
}

@Composable
private fun anim(target: Color): Color = animateColorAsState(target, tween(250), label = "scheme").value

/** Smoothly morphs every colour when the accent hue or light/dark mode changes. */
@Composable
private fun animated(t: ColorScheme): ColorScheme = t.copy(
    primary = anim(t.primary), onPrimary = anim(t.onPrimary),
    primaryContainer = anim(t.primaryContainer), onPrimaryContainer = anim(t.onPrimaryContainer),
    inversePrimary = anim(t.inversePrimary),
    secondary = anim(t.secondary), onSecondary = anim(t.onSecondary),
    secondaryContainer = anim(t.secondaryContainer), onSecondaryContainer = anim(t.onSecondaryContainer),
    tertiary = anim(t.tertiary), onTertiary = anim(t.onTertiary),
    tertiaryContainer = anim(t.tertiaryContainer), onTertiaryContainer = anim(t.onTertiaryContainer),
    background = anim(t.background), onBackground = anim(t.onBackground),
    surface = anim(t.surface), onSurface = anim(t.onSurface),
    surfaceVariant = anim(t.surfaceVariant), onSurfaceVariant = anim(t.onSurfaceVariant),
    surfaceTint = anim(t.surfaceTint),
    inverseSurface = anim(t.inverseSurface), inverseOnSurface = anim(t.inverseOnSurface),
    error = anim(t.error), onError = anim(t.onError),
    errorContainer = anim(t.errorContainer), onErrorContainer = anim(t.onErrorContainer),
    outline = anim(t.outline), outlineVariant = anim(t.outlineVariant),
    surfaceDim = anim(t.surfaceDim), surfaceBright = anim(t.surfaceBright),
    surfaceContainerLowest = anim(t.surfaceContainerLowest), surfaceContainerLow = anim(t.surfaceContainerLow),
    surfaceContainer = anim(t.surfaceContainer), surfaceContainerHigh = anim(t.surfaceContainerHigh),
    surfaceContainerHighest = anim(t.surfaceContainerHighest)
)

@Composable
fun BlissTheme(darkTheme: Boolean, hue: Float, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = !darkTheme
            c.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    val typography = remember { blissTypography(NunitoFamily.load(context)) }
    MaterialTheme(
        colorScheme = animated(schemeFor(hue, darkTheme)),
        typography = typography,
        shapes = BlissShapes,
        content = content
    )
}
