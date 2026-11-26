package com.bliss.blisschat.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A coloured moving scene with one glass panel on top, to see what the selected quality looks like. */
@Composable
fun GlassPreview(quality: GlassQuality) {
    val backdrop = rememberGlassBackdrop()
    val t = rememberInfiniteTransition(label = "glass-preview")
    val a by t.animateFloat(-60f, 60f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(60f, -60f, infiniteRepeatable(tween(5500, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(24.dp))) {
        Box(Modifier.matchParentSize().glassSource(backdrop).background(Color(0xFF101820))) {
            Box(Modifier.align(Alignment.CenterStart).offset(x = (30 + a).dp).size(90.dp).clip(CircleShape).background(Color(0xFFFF4D6D)))
            Box(Modifier.align(Alignment.CenterEnd).offset(x = (-30 + b).dp, y = 20.dp).size(110.dp).clip(CircleShape).background(Color(0xFF4DA3FF)))
            Box(Modifier.align(Alignment.TopCenter).offset(x = (a / 2).dp, y = 8.dp).size(70.dp).clip(CircleShape).background(Color(0xFFFFD84D)))
            Text("blisschat glass  •  Liquid  •  Glass", color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(14.dp))
        }
        Box(
            Modifier.align(Alignment.Center).size(190.dp, 84.dp)
                .glassPanel(backdrop, RoundedCornerShape(42.dp), quality, Color.White.copy(alpha = 0.10f), 42.dp),
            contentAlignment = Alignment.Center
        ) { Text(quality.title, style = MaterialTheme.typography.titleLarge, color = Color.White) }
    }
}
