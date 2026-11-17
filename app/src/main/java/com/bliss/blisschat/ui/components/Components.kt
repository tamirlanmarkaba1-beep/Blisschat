package com.bliss.blisschat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bliss.blisschat.data.UserProfile

/* ------------------------------------------------------------------ spring physics */

/** Squishes the element with a bouncy spring while it is pressed. */
fun Modifier.pressScale(source: InteractionSource, pressed: Float = 0.92f): Modifier = composed {
    val isPressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Clickable with a springy press animation (list rows, cards). */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    this
        .pressScale(source, 0.97f)
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun BouncyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    tonal: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = source,
        shape = CircleShape,
        colors = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        modifier = modifier
            .height(54.dp)
            .pressScale(source)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
        } else {
            if (icon != null) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }
    }
}

@Composable
fun BouncyIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(),
        modifier = modifier
            .size(52.dp)
            .pressScale(source, 0.85f)
    ) {
        Icon(icon, description)
    }
}

/* ------------------------------------------------------------------ text field */

@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    isError: Boolean = false,
    prefix: String? = null,
    supporting: String? = null,
    shape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = leading?.let { { Icon(it, null) } },
        prefix = prefix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = MaterialTheme.colorScheme.error
        )
    )
}

/* ------------------------------------------------------------------ avatars & badges */

@Composable
fun Avatar(url: String, name: String, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val palette = remember(scheme) {
        listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer
        )
    }
    val (bg, fg) = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            val initials = name.trim().split(" ", "_").filter { it.isNotEmpty() }
                .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            Text(
                initials,
                color = fg,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }
}

@Composable
fun VerifiedBadge(size: Dp = 18.dp) {
    Icon(
        Icons.Filled.Verified,
        contentDescription = "Verified",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(size)
    )
}

@Composable
fun SupportBadge(size: Dp = 18.dp) {
    Icon(
        Icons.Filled.Security,
        contentDescription = "blisschat Support",
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.size(size)
    )
}

/** Name followed by the verified / support badges. Used everywhere a username is shown. */
@Composable
fun NameWithBadges(
    name: String,
    verified: Boolean,
    support: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    badgeSize: Dp = 18.dp
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (verified) {
            Spacer(Modifier.width(4.dp))
            VerifiedBadge(badgeSize)
        }
        if (support) {
            Spacer(Modifier.width(3.dp))
            SupportBadge(badgeSize)
        }
    }
}

/* ------------------------------------------------------------------ OTP input */

@Composable
fun OtpInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    BasicTextField(
        value = value,
        onValueChange = { v -> if (v.length <= length && v.all { it.isDigit() }) onValueChange(v) },
        modifier = modifier.focusRequester(focus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(length) { i ->
                    OtpBox(
                        char = value.getOrNull(i)?.toString(),
                        active = i == value.length,
                        isError = isError,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    )
}

@Composable
private fun OtpBox(char: String?, active: Boolean, isError: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val border by animateColorAsState(
        when {
            isError -> scheme.error
            active -> scheme.primary
            char != null -> scheme.primary.copy(alpha = 0.45f)
            else -> scheme.outlineVariant
        },
        label = "otp-border"
    )
    val scale by animateFloatAsState(
        if (char != null) 1f else 0.94f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "otp-scale"
    )
    Box(
        modifier = modifier
            .width(48.dp)
            .height(60.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainerHigh)
            .border(if (active) 2.dp else 1.5.dp, border, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            char ?: "",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
    }
}

/* ------------------------------------------------------------------ profile sheet */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(user: UserProfile, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(user.avatarUrl, user.displayName.ifBlank { user.username }, size = 96.dp)
            Spacer(Modifier.height(14.dp))
            NameWithBadges(
                name = user.displayName.ifBlank { user.username },
                verified = user.verified,
                support = user.support,
                style = MaterialTheme.typography.headlineSmall,
                badgeSize = 24.dp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (user.verified || user.support) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (user.verified) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Verified") },
                            leadingIcon = { Icon(Icons.Filled.Verified, null, Modifier.size(18.dp)) },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = null
                        )
                    }
                    if (user.support) {
                        AssistChip(
                            onClick = {},
                            label = { Text("blisschat Support") },
                            leadingIcon = { Icon(Icons.Filled.Security, null, Modifier.size(18.dp)) },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.tertiary
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}
