package com.bliss.blisschat.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.graphics.RenderEffect as AndroidRenderEffect

/**
 * Own liquid-glass engine (no third-party library: the free "Backdrop" library by Kyant0 needs Kotlin 2.3+/Compose 1.10+,
 * this project is on Kotlin 2.0 / Compose 1.7). It uses the same Android building blocks:
 *  - the picture behind the glass is recorded once into a [GraphicsLayer] ([glassSource]);
 *  - every panel ([glassPanel]) draws its own piece of that picture, blurred (RenderEffect, Android 12+) and
 *    bent at the edges by an AGSL shader that imitates a thick glass lens (Android 13+).
 */
@Stable
class GlassBackdrop(val layer: GraphicsLayer) {
    var origin: Offset by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks the content that panels should look through. Panels themselves must NOT be inside this content. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier =
    this.onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            backdrop.layer.record(this, layoutDirection, IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                this@drawWithContent.drawContent()
            }
            drawLayer(backdrop.layer)
        }

private class Preset(val blurDp: Float, val saturation: Float, val refractHeightDp: Float, val refractAmountDp: Float, val chroma: Boolean, val rim: Float)

private val PRESETS = listOf(
    Preset(0f, 1f, 0f, 0f, false, 0.35f),          // LOW  (not used for blur)
    Preset(18f, 1.35f, 0f, 0f, false, 0.45f),      // HIGH
    Preset(10f, 1.5f, 14f, 16f, false, 0.55f),     // ULTRA
    Preset(7f, 1.7f, 26f, 34f, true, 0.7f)         // EXTREME
)

private const val LENS = """
uniform shader content;
uniform float2 size;
uniform float corner;
uniform float height;
uniform float amount;
uniform float chroma;

float sdf(float2 p) {
  float2 q = abs(p) - size * 0.5 + corner;
  return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - corner;
}

half4 main(float2 coord) {
  float2 p = coord - size * 0.5;
  float d = -sdf(p);
  if (d >= height || d < 0.0) { return content.eval(coord); }
  float t = 1.0 - d / height;
  float k = t * t * t;
  float2 g = float2(sdf(p + float2(1.0, 0.0)) - sdf(p - float2(1.0, 0.0)),
                    sdf(p + float2(0.0, 1.0)) - sdf(p - float2(0.0, 1.0)));
  float2 n = g / max(length(g), 0.0001);
  float2 hi = size - float2(1.0);
  half4 col = content.eval(clamp(coord - n * amount * k, float2(0.0), hi));
  if (chroma > 0.5) {
    float r = content.eval(clamp(coord - n * amount * k * 1.35, float2(0.0), hi)).r;
    float b = content.eval(clamp(coord - n * amount * k * 0.65, float2(0.0), hi)).b;
    col = half4(half(r), col.g, half(b), col.a);
  }
  col.rgb += half3(half(0.08 * k));
  return col;
}
"""

private fun outlinePath(o: Outline): Path = when (o) {
    is Outline.Rectangle -> Path().apply { addRect(o.rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
    is Outline.Generic -> o.path
}

private fun levelOf(q: GlassQuality): Int {
    val sdk = Build.VERSION.SDK_INT
    return when {
        q == GlassQuality.LOW || sdk < Build.VERSION_CODES.S -> 0
        q == GlassQuality.HIGH || sdk < Build.VERSION_CODES.TIRAMISU -> 1
        q == GlassQuality.ULTRA -> 2
        else -> 3
    }
}

private fun effectFor(level: Int, density: Float, w: Float, h: Float, cornerPx: Float): androidx.compose.ui.graphics.RenderEffect {
    val p = PRESETS[level]
    val cm = ColorMatrix().apply { setSaturation(p.saturation) }
    var e = AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm))
    e = AndroidRenderEffect.createChainEffect(
        AndroidRenderEffect.createBlurEffect(p.blurDp * density, p.blurDp * density, Shader.TileMode.CLAMP), e
    )
    if (level >= 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val s = RuntimeShader(LENS)
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("corner", cornerPx)
        s.setFloatUniform("height", minOf(p.refractHeightDp * density, cornerPx.coerceAtLeast(1f)))
        s.setFloatUniform("amount", p.refractAmountDp * density)
        s.setFloatUniform("chroma", if (p.chroma) 1f else 0f)
        e = AndroidRenderEffect.createChainEffect(AndroidRenderEffect.createRuntimeShaderEffect(s, "content"), e)
    }
    return e.asComposeRenderEffect()
}

/**
 * A piece of glass: blurs / bends what [glassSource] shows behind it, then paints [tint] and a bright rim.
 * [corner] is the radius of the lens edge (use a big value for pills and circles).
 */
fun Modifier.glassPanel(backdrop: GlassBackdrop, shape: Shape, quality: GlassQuality, tint: Color, corner: Dp = 28.dp): Modifier = composed {
    val layer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val level = levelOf(quality)
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawWithContent {
            val w = size.width
            val h = size.height
            val outline = shape.createOutline(size, layoutDirection, this)
            if (level > 0 && w >= 1f && h >= 1f) {
                val cornerPx = minOf(corner.toPx(), minOf(w, h) / 2f)
                layer.renderEffect = effectFor(level, density, w, h, cornerPx)
                val dx = pos.x - backdrop.origin.x
                val dy = pos.y - backdrop.origin.y
                layer.record(this, layoutDirection, IntSize(w.toInt(), h.toInt())) {
                    translate(-dx, -dy) { drawLayer(backdrop.layer) }
                }
                val path = outlinePath(outline)
                clipPath(path) { drawLayer(layer) }
            }
            val shapePath = outlinePath(outline)
            drawPath(shapePath, color = tint)
            drawContent()
            val rim = PRESETS[level].rim
            drawPath(
                shapePath,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = rim), Color.White.copy(alpha = 0.05f), Color.White.copy(alpha = rim * 0.6f)),
                    start = Offset.Zero, end = Offset(w, h)
                ),
                style = Stroke(width = 1.3f * density)
            )
        }
}
