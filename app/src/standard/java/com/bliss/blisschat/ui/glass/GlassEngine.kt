package com.bliss.blisschat.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Regular blisschat: the glass engine is switched off; these stubs keep the shared UI code identical in both apps. */
class GlassBackdrop

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.glassPanel(backdrop: GlassBackdrop, shape: Shape, quality: GlassQuality, tint: Color, corner: Dp = 28.dp): Modifier = this
