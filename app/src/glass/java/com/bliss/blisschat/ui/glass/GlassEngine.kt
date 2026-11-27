package com.bliss.blisschat.ui.glass

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.*

/**
 * Real liquid glass from the free Backdrop library by Kyant0 (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass
 * [glassSource] marks what the glass looks through, [glassPanel] draws a piece of glass over it.
 */
@Stable
class GlassBackdrop(val backdrop: LayerBackdrop)

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val b = rememberLayerBackdrop()
    return remember(b) { GlassBackdrop(b) }
}

fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this.layerBackdrop(backdrop.backdrop)

/** 0 = no effects, 1 = blur (Android 12+), 2 = blur + refraction (13+), 3 = + depth and colour fringes. */
private fun levelOf(q: GlassQuality): Int {
    val sdk = Build.VERSION.SDK_INT
    return when {
        q == GlassQuality.LOW || sdk < Build.VERSION_CODES.S -> 0
        q == GlassQuality.HIGH || sdk < Build.VERSION_CODES.TIRAMISU -> 1
        q == GlassQuality.ULTRA -> 2
        else -> 3
    }
}

fun Modifier.glassPanel(backdrop: GlassBackdrop, shape: Shape, quality: GlassQuality, tint: Color, corner: Dp = 28.dp): Modifier {
    val level = levelOf(quality)
    val canLens = shape is CornerBasedShape
    return this.drawBackdrop(
        backdrop = backdrop.backdrop,
        shape = { shape },
        effects = {
            if (level >= 1) {
                if (level >= 3) colorControls(saturation = 2.0f) else vibrancy()
                blur((if (level == 1) 10f else if (level == 2) 4f else 2f).dp.toPx())
            }
            if (level >= 2 && canLens) {
                val height = minOf((if (level == 2) 18f else 30f).dp.toPx(), corner.toPx())
                val amount = minOf((if (level == 2) 36f else 64f).dp.toPx(), size.minDimension)
                lens(height, amount, level >= 3, level >= 3)
            }
        },
        onDrawSurface = { drawRect(tint) }
    )
}
