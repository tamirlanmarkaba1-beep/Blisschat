package com.bliss.blisschat.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bliss.blisschat.BuildConfig

/** Strength of the real liquid-glass effect (glass app only; the regular app ignores it). */
enum class GlassQuality(val title: String, val hint: String) {
    LOW("Low", "Только полупрозрачность, без размытия. Для слабых телефонов и Android 11 и ниже"),
    HIGH("High", "Размытие фона под панелями и насыщенные цвета (Android 12+)"),
    ULTRA("Ultra", "Размытие + преломление света на краях стекла (Android 13+)"),
    EXTREME("Extreme", "Сильное преломление, цветовые дисперсии на краях и блик. Самая тяжёлая (Android 13+)");

    companion object {
        fun of(name: String): GlassQuality = entries.firstOrNull { it.name == name } ?: HIGH
    }
}

val LocalGlassQuality = staticCompositionLocalOf { GlassQuality.HIGH }

/** Backdrop sampled by bars/buttons of the current screen (set in MainNav; null = no glass). */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** True when bars and buttons of this screen should be drawn as glass (glass app only). */
@Composable
fun glassActive(): Boolean = BuildConfig.GLASS && LocalGlassBackdrop.current != null

/** Makes this element a glass panel over the screen backdrop. Does nothing in the regular app. */
@Composable
fun Modifier.glassBar(shape: Shape, tint: Color, corner: Dp = 28.dp): Modifier {
    if (!BuildConfig.GLASS) return this
    val b = LocalGlassBackdrop.current ?: return this
    return this.glassPanel(b, shape, LocalGlassQuality.current, tint.copy(alpha = 0.45f), corner)
}

/** Source modifier for the screen wallpaper (blobs / doodles) that bars refract. */
@Composable
fun Modifier.glassWallpaper(): Modifier {
    if (!BuildConfig.GLASS) return this
    val b = LocalGlassBackdrop.current ?: return this
    return this.glassSource(b)
}
