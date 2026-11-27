package com.bliss.blisschat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Size
import com.bliss.blisschat.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.bliss.blisschat.ui.glass.glassActive
import com.bliss.blisschat.ui.glass.glassBar
import com.bliss.blisschat.ui.glass.glassWallpaper
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bliss.blisschat.ui.theme.AvatarShape
import com.bliss.blisschat.ui.theme.ButtonBlob
import com.bliss.blisschat.ui.theme.scallopPath
import kotlin.math.PI
import kotlin.math.sin

val VerifiedBlue = Color(0xFF1D9BF0)
val OnlineGreen = Color(0xFF2EE62E)

/* ------------------------------------------------------------------ spring physics */

/** Squishes the element with a bouncy spring while it is pressed. */
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.92f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clickable with a springy press animation. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this.pressScale(source, 0.96f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/* ------------------------------------------------------------------ backdrop */

private class Blob(val cx: Float, val cy: Float, val r: Float, val lobes: Int, val depth: Float, val color: Int, val alpha: Float, val spin: Float)

private val backdrops = listOf(
    // 0: sign-in screens
    listOf(Blob(-0.15f, 0.62f, 0.95f, 7, .035f, 0, 1f, 1f), Blob(1.05f, 0.08f, 0.42f, 9, .05f, 1, .5f, -1f)),
    // 1: chat list
    listOf(
        Blob(-0.02f, 0.5f, 0.45f, 9, .05f, 0, 1f, 1f),
        Blob(1.02f, 0.1f, 0.42f, 8, .05f, 2, .75f, -1f),
        Blob(1.0f, 0.86f, 0.55f, 7, .045f, 1, .7f, 1f)
    ),
    // 2: profile / settings / admin
    listOf(Blob(-0.1f, 0.95f, 0.5f, 8, .04f, 0, 1f, -1f), Blob(1.1f, 0.5f, 0.4f, 9, .05f, 1, .45f, 1f))
)

/** Decorative wavy blobs + a soft glow, all coloured from the current accent. */
@Composable
fun BlissBackdrop(variant: Int = 0, modifier: Modifier = Modifier.fillMaxSize()) {
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer, scheme.surfaceContainerHighest)
    val trans = rememberInfiniteTransition(label = "backdrop-anim")
    val spin by trans.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val floatX by trans.animateFloat(-0.06f, 0.06f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "floatX")
    val floatY by trans.animateFloat(-0.06f, 0.06f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "floatY")
    val blobs = backdrops[variant.coerceIn(0, backdrops.lastIndex)]
    Canvas(modifier.glassWallpaper().background(scheme.background)) {
        drawRect(
            Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f
            )
        )
        blobs.forEachIndexed { i, b ->
            val r = b.r * size.width
            val ox = if (i % 2 == 0) floatX * size.width else -floatX * size.width
            val oy = if (i % 2 == 0) floatY * size.height else -floatY * size.height
            val path = scallopPath((b.cx * size.width) + ox, (b.cy * size.height) + oy, r, r, b.lobes, b.depth, spin * b.spin)
            drawPath(path, colors[b.color % colors.size].copy(alpha = b.alpha * 1.3f))
        }
    }
}

/**
 * WhatsApp-style chat wallpaper: a grid of small doodle icons (chat, music, photo, heart...) with the app icon
 * drawn bigger and brighter among them. Pure white on dark themes, pure black on light themes.
 */
@Composable
fun ChatBackground(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.red * 0.299f + scheme.background.green * 0.587f + scheme.background.blue * 0.114f < 0.5f
    val ink = if (isDark) Color.White else Color.Black
    val doodles = listOf(
        rememberVectorPainter(Icons.Filled.ChatBubble), rememberVectorPainter(Icons.Filled.MusicNote),
        rememberVectorPainter(Icons.Filled.Image), rememberVectorPainter(Icons.Filled.Favorite),
        rememberVectorPainter(Icons.Filled.Star), rememberVectorPainter(Icons.Filled.Call),
        rememberVectorPainter(Icons.Filled.Cloud), rememberVectorPainter(Icons.Filled.EmojiEmotions)
    )
    val logo = painterResource(R.drawable.ic_launcher_monochrome)

    Canvas(modifier.fillMaxSize().glassWallpaper().background(scheme.background)) {
        val cell = 76.dp.toPx()
        val small = 24.dp.toPx()
        val big = 66.dp.toPx()
        val rows = (size.height / cell).toInt() + 2
        val cols = (size.width / cell).toInt() + 2
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = c * cell + (if (r % 2 == 0) 0f else cell / 2f)
                val cy = r * cell
                if ((c + 2 * r) % 5 == 0) {
                    // the app icon: bigger and brighter than the doodles
                    translate(left = cx - big / 2f, top = cy - big / 2f) {
                        with(logo) { draw(Size(big, big), alpha = 0.16f, colorFilter = ColorFilter.tint(ink)) }
                    }
                } else {
                    val painter = doodles[(r * 3 + c) % doodles.size]
                    rotate(((r * 37 + c * 53) % 50 - 25).toFloat(), Offset(cx, cy)) {
                        translate(left = cx - small / 2f, top = cy - small / 2f) {
                            with(painter) { draw(Size(small, small), alpha = 0.07f, colorFilter = ColorFilter.tint(ink)) }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ headers */

/** Coloured bar with big rounded bottom corners (chat header, settings header). */
@Composable
fun BlissHeader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: @Composable () -> Unit
) {
    // Floating pill (as in the new design) instead of a full-width bar glued to the top edge.
    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val g = glassActive()
        Surface(
            color = if (g) Color.Transparent else color,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(32.dp),
            shadowElevation = if (g) 0.dp else 4.dp,
            modifier = Modifier.fillMaxWidth().height(64.dp).glassBar(RoundedCornerShape(32.dp), color, 32.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content() }
        }
    }
}

/* ------------------------------------------------------------------ buttons */

@Composable
fun BouncyButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null, tonal: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val g = glassActive()
    val base = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()
    val tint = if (tonal) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary
    Button(
        onClick = onClick, enabled = enabled && !loading, interactionSource = source, shape = CircleShape,
        colors = if (g) base.copy(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent) else base,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.height(54.dp).pressScale(source).glassBar(CircleShape, if (enabled) tint else tint.copy(alpha = 0.4f), 27.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
        } else {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }
    }
}

@Composable
fun OutlinedPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    val source = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick, enabled = !loading, interactionSource = source, shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.height(54.dp).pressScale(source).glassBar(CircleShape, MaterialTheme.colorScheme.surfaceContainerHigh, 27.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Round button with a wavy blob outline (call buttons, "+" button, send button). */
@Composable
fun BlobButton(
    icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = 52.dp, container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary, enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (enabled) container else container.copy(alpha = 0.35f), label = "blob-bg")
    val g = glassActive()
    Box(
        modifier.size(size).pressScale(source, 0.85f).glassBar(CircleShape, bg, size / 2)
            .then(if (g) Modifier.clip(CircleShape) else Modifier.clip(ButtonBlob).background(bg))
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, description, tint = content, modifier = Modifier.size(size * 0.46f)) }
}

/* ------------------------------------------------------------------ text field */

@Composable
fun PillTextField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    leading: ImageVector? = null, trailing: @Composable (() -> Unit)? = null,
    keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, maxLines: Int = 1,
    isError: Boolean = false, supporting: String? = null, shape: Shape = RoundedCornerShape(28.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    containerColor: Color = Color.Unspecified, glass: Boolean = false
) {
    val solid = if (containerColor == Color.Unspecified) MaterialTheme.colorScheme.surfaceContainerHigh else containerColor
    val useGlass = glass && glassActive()
    val container = if (useGlass) Color.Transparent else solid
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(if (useGlass) Modifier.glassBar(shape, solid, 28.dp) else Modifier),
        placeholder = { Text(placeholder) },
        leadingIcon = leading?.let { { Icon(it, null) } },
        trailingIcon = trailing,
        supportingText = supporting?.let { { Text(it) } },
        isError = isError, singleLine = singleLine, maxLines = maxLines, shape = shape,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            errorContainerColor = container,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = MaterialTheme.colorScheme.error
        )
    )
}

/* ------------------------------------------------------------------ avatars & badges */

private val dataUriCache = object : android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64) {}

/** Decodes "data:image/...;base64,..." avatars (cached, so lists stay smooth). */
internal fun decodeDataUri(uri: String): androidx.compose.ui.graphics.ImageBitmap? {
    dataUriCache.get(uri)?.let { return it }
    val bmp = runCatching {
        val bytes = android.util.Base64.decode(uri.substringAfter("base64,"), android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
    if (bmp != null) dataUriCache.put(uri, bmp)
    return bmp
}

@Composable
fun Avatar(url: String, name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(AvatarShape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val inline = remember(url) { if (url.startsWith("data:")) decodeDataUri(url) else null }
        if (inline != null) {
            androidx.compose.foundation.Image(inline, name, Modifier.size(size), contentScale = ContentScale.Crop)
        } else if (url.isNotBlank() && !url.startsWith("data:")) {
            AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val initials = name.trim().split(" ", "_").filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            Text(initials, color = scheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
    }
}

@Composable
fun OnlineDot(online: Boolean, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(ButtonBlob).background(if (online) OnlineGreen else MaterialTheme.colorScheme.outline)
    )
}

@Composable
fun VerifiedBadge(size: Dp = 18.dp) {
    Icon(Icons.Filled.Verified, "Подтверждён", tint = VerifiedBlue, modifier = Modifier.size(size))
}

@Composable
fun SupportBadge(size: Dp = 18.dp) {
    Icon(Icons.Filled.Security, "blisschat Support", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(size))
}

/** Name followed by the verified / support badges. Used everywhere a name is shown. */
@Composable
fun NameWithBadges(
    name: String, verified: Boolean, support: Boolean, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium, color: Color = Color.Unspecified, badgeSize: Dp = 18.dp
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (verified) { Spacer(Modifier.width(4.dp)); VerifiedBadge(badgeSize) }
        if (support) { Spacer(Modifier.width(3.dp)); SupportBadge(badgeSize) }
    }
}

/** The wavy separator line used on the profile screen. */
@Composable
fun WavyDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val path = Path()
        val amp = size.height * 0.28f
        val period = 26.dp.toPx()
        var x = 0f
        path.moveTo(0f, size.height / 2)
        while (x <= size.width) {
            path.lineTo(x, size.height / 2 + amp * sin(2 * PI.toFloat() * x / period))
            x += 3f
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
    }
}

/** Dark teal card with a big radius, used for banners / info boxes. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainer, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = color, modifier = modifier.fillMaxWidth()) { content() }
}

@Composable
fun Modifier.outlinedBlob(color: Color = MaterialTheme.colorScheme.outlineVariant): Modifier =
    this.border(1.dp, color, RoundedCornerShape(22.dp))
