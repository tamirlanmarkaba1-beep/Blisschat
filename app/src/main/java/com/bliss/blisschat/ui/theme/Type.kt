package com.bliss.blisschat.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Nunito (400 / 500 / 600 / 700) from ONE variable font file at assets/fonts/Nunito.ttf.
 * The Gradle task `downloadNunito` fetches it at build time; or drop your own copy at
 * app/src/main/assets/fonts/Nunito.ttf. If missing, the system font is used.
 */
object NunitoFamily {
    private const val PATH = "fonts/Nunito.ttf"

    @OptIn(ExperimentalTextApi::class)
    fun load(context: Context): FontFamily = try {
        context.assets.open(PATH).close()
        FontFamily(listOf(400, 500, 600, 700, 800).map { w ->
            Font(
                path = PATH, assetManager = context.assets, weight = FontWeight(w), style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(w))
            )
        })
    } catch (e: Exception) {
        FontFamily.Default
    }
}

fun blissTypography(family: FontFamily): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displayMedium = d.displayMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        displaySmall = d.displaySmall.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineLarge = d.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineMedium = d.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        headlineSmall = d.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleLarge = d.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Bold),
        titleMedium = d.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal),
        bodyMedium = d.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
        bodySmall = d.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
        labelLarge = d.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
        labelMedium = d.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
        labelSmall = d.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium)
    )
}
