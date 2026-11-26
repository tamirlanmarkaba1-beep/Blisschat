package com.bliss.blisschat.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Diagnostic preview: a scene with a grid of lines and moving circles, and three glass panels (High, Ultra, Extreme)
 * so that blur, bending and colour fringes can be told apart at a glance. [quality] only changes the label.
 */
@Composable
fun GlassPreview(quality: GlassQuality) {
    val backdrop = rememberGlassBackdrop()
    val t = rememberInfiniteTransition(label = "glass-preview")
    val a by t.animateFloat(-70f, 70f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(70f, -70f, infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(24.dp))) {
        Box(Modifier.matchParentSize().glassSource(backdrop).background(Color(0xFF101820))) {
            Canvas(Modifier.matchParentSize()) {
                val step = 24.dp.toPx()
                var x = 0f
                while (x < size.width) { drawLine(Color(0x66FFFFFF), Offset(x, 0f), Offset(x, size.height), 2f); x += step }
                var y = 0f
                while (y < size.height) { drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(size.width, y), 2f); y += step }
            }
            Box(Modifier.align(Alignment.CenterStart).offset(x = (40 + a).dp).size(100.dp).clip(CircleShape).background(Color(0xFFFF4D6D)))
            Box(Modifier.align(Alignment.CenterEnd).offset(x = (-40 + b).dp).size(120.dp).clip(CircleShape).background(Color(0xFF4DA3FF)))
            Box(Modifier.align(Alignment.TopCenter).offset(x = (a / 2).dp, y = 20.dp).size(80.dp).clip(CircleShape).background(Color(0xFFFFD84D)))
        }
        listOf(GlassQuality.HIGH to Alignment.TopCenter, GlassQuality.ULTRA to Alignment.Center, GlassQuality.EXTREME to Alignment.BottomCenter)
            .forEach { (q, pos) ->
                Box(
                    Modifier.align(pos).padding(vertical = 14.dp).size(220.dp, 84.dp)
                        .glassPanel(backdrop, RoundedCornerShape(42.dp), q, Color.White.copy(alpha = 0.06f), 42.dp),
                    contentAlignment = Alignment.Center
                ) { Text(q.title, style = MaterialTheme.typography.titleLarge, color = Color.White) }
            }
    }
    val sdk = android.os.Build.VERSION.SDK_INT
    Text(
        "Android ${android.os.Build.VERSION.RELEASE} (API $sdk). Сверху High: размытие. Середина Ultra и низ Extreme: линии сетки должны гнуться у краёв панели.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}
