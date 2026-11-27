package com.bliss.blisschat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Expressive, generously rounded Material 3 shapes. */
val BlissShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

/** Wavy "cookie" outline: a circle (or ellipse) whose edge ripples [lobes] times. */
fun scallopPath(cx: Float, cy: Float, rx: Float, ry: Float, lobes: Int, depth: Float, rotation: Float = 0f): Path {
    val path = Path()
    val steps = 240
    for (i in 0..steps) {
        val t = (2.0 * PI * i / steps).toFloat()
        val k = 1f - depth + depth * cos(lobes * t + rotation)
        val x = cx + rx * k * cos(t)
        val y = cy + ry * k * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Blob / cookie shape used for avatars, floating buttons and decorations. */
class ScallopShape(private val lobes: Int = 9, private val depth: Float = 0.05f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(scallopPath(size.width / 2, size.height / 2, size.width / 2, size.height / 2, lobes, depth))
}

val AvatarShape = ScallopShape(9, 0.05f)
val ButtonBlob = ScallopShape(8, 0.06f)
val SoftBlob = ScallopShape(6, 0.035f)

/** Smooth organic blob (low-frequency wobble) for backdrops. */
fun organicBlob(cx: Float, cy: Float, r: Float, seed: Int, wobble: Float = 0.12f): Path {
    val path = Path()
    val steps = 200
    val p1 = seed * 1.7f
    val p2 = seed * 2.9f
    val p3 = seed * 0.6f
    for (i in 0..steps) {
        val t = (2.0 * PI * i / steps).toFloat()
        val rad = r * (1f + wobble * sin(2 * t + p1) + wobble * 0.7f * sin(3 * t + p2) + wobble * 0.4f * sin(5 * t + p3))
        val x = cx + rad * cos(t)
        val y = cy + rad * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

fun minSide(size: Size) = min(size.width, size.height)
