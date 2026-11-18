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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val colors = listOf(scheme.primaryContainer, scheme.secondaryContainer, scheme.surfaceContainerHighest)
    val spin by rememberInfiniteTransition(label = "backdrop").animateFloat(
        0f, (2 * PI).toFloat(), infiniteRepeatable(tween(120_000, easing = LinearEasing), RepeatMode.Restart), label = "spin"
    )
    val blobs = backdrops[variant.coerceIn(0, backdrops.lastIndex)]
    Canvas(modifier.background(scheme.background)) {
        drawRect(
            Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f
            )
        )
        blobs.forEach { b ->
            val r = b.r * size.width
            val path = scallopPath(b.cx * size.width, b.cy * size.height, r, r, b.lobes, b.depth, spin * b.spin)
            drawPath(path, colors[b.color].copy(alpha = b.alpha))
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
    Surface(
        color = color,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(Modifier.statusBarsPadding().height(64.dp), contentAlignment = Alignment.CenterStart) { content() }
    }
}

/* ------------------------------------------------------------------ buttons */

@Composable
fun BouncyButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false, icon: ImageVector? = null, tonal: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick, enabled = enabled && !loading, interactionSource = source, shape = CircleShape,
        colors = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier.height(54.dp).pressScale(source)
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
        modifier = modifier.height(54.dp).pressScale(source)
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
    Box(
        modifier.size(size).pressScale(source, 0.85f).clip(ButtonBlob).background(bg)
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
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = leading?.let { { Icon(it, null) } },
        trailingIcon = trailing,
        supportingText = supporting?.let { { Text(it) } },
        isError = isError, singleLine = singleLine, maxLines = maxLines, shape = shape,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = MaterialTheme.colorScheme.error
        )
    )
}

/* ------------------------------------------------------------------ avatars & badges */

@Composable
fun Avatar(url: String, name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.size(size).clip(AvatarShape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
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
