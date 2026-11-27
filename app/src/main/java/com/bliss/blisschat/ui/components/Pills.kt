package com.bliss.blisschat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.bliss.blisschat.ui.glass.glassActive
import com.bliss.blisschat.ui.glass.glassBar

/*
 * "Split pill" design language from the mockups: a bar is cut into segments (back button | title, search icon | field,
 * call | video | menu ...). The outer ends are fully round, the cuts between segments have a small radius.
 */

val SegStart = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 6.dp, bottomEnd = 6.dp)
val SegEnd = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 32.dp, bottomEnd = 32.dp)
val SegMid = RoundedCornerShape(6.dp)

/** Header of a settings page: [ < ][ title ]. */
@Composable
fun SplitHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val seg = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        val g = glassActive()
        val sc = if (g) Color.Transparent else seg
        Surface(onClick = onBack, shape = SegStart, color = sc, modifier = Modifier.size(width = 64.dp, height = 56.dp).glassBar(SegStart, seg, 6.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Назад", Modifier.size(32.dp)) }
        }
        Surface(shape = SegEnd, color = sc, modifier = Modifier.widthIn(min = 220.dp).height(56.dp).glassBar(SegEnd, seg, 6.dp)) {
            Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** [ search icon ][ text field ] */
@Composable
fun SplitSearchBar(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null
) {
    val seg = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val g = glassActive()
        Surface(shape = SegStart, color = if (g) Color.Transparent else seg, modifier = Modifier.size(width = 64.dp, height = 56.dp).glassBar(SegStart, seg, 6.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, "Поиск", Modifier.size(28.dp)) }
        }
        PillTextField(
            value = value, onValueChange = onValueChange, placeholder = placeholder,
            modifier = Modifier.weight(1f).then(fieldModifier), shape = SegEnd, containerColor = seg, trailing = trailing, glass = true
        )
    }
}
