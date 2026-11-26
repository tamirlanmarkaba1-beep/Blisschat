package com.bliss.blisschat.ui.glass

import androidx.compose.runtime.staticCompositionLocalOf

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
