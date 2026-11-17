package com.informationplant.fdmn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF0B5D4B)
val BrandDark = Color(0xFF07402F)
val Accent = Color(0xFFE09B2D)
val Female = Color(0xFFC2185B)
val Male = Color(0xFF1565C0)

private val Colors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBE1),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6C2),
    onSecondaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF3F6F5),
    onBackground = Color(0xFF17201D),
    surface = Color.White,
    onSurface = Color(0xFF17201D),
    surfaceVariant = Color(0xFFE3EBE8),
    onSurfaceVariant = Color(0xFF47554F),
    surfaceContainer = Color(0xFFEAF1EE),
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainerHigh = Color(0xFFE6EEEB),
    outline = Color(0xFF8A9893),
    outlineVariant = Color(0xFFC9D6D1),
    error = Color(0xFFB3261E),
)

@Composable
fun FdmnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}

@Composable
fun BrandTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Box(Modifier.background(Brush.horizontalGradient(listOf(BrandDark, Brand, Color(0xFF138A6E))))) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.85f))
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            ),
        )
    }
}

// ---------- colourful card helpers ----------

/** Two-tone colours used for icons, tiles and blocks. */
data class Tone(val light: Color, val dark: Color) {
    val brush get() = Brush.linearGradient(listOf(light, dark))
}

val ToneGreen = Tone(Color(0xFF22B573), Color(0xFF0B7A45))
val ToneBlue = Tone(Color(0xFF3D8BFD), Color(0xFF1456C8))
val TonePurple = Tone(Color(0xFFA66CFF), Color(0xFF6D28D9))
val ToneOrange = Tone(Color(0xFFFFB547), Color(0xFFF07C12))
val TonePink = Tone(Color(0xFFFF5C9A), Color(0xFFC2185B))
val ToneTeal = Tone(Color(0xFF2DD4BF), Color(0xFF0F766E))
val ToneRed = Tone(Color(0xFFFF7A6B), Color(0xFFD32F2F))
val ToneIndigo = Tone(Color(0xFF7C8CFF), Color(0xFF3949AB))

val BlockTones = listOf(ToneGreen, ToneBlue, TonePurple, ToneOrange, TonePink, ToneTeal, ToneRed, ToneIndigo)
fun blockTone(index: Int) = BlockTones[((index % BlockTones.size) + BlockTones.size) % BlockTones.size]

/** Soft double wave in the bottom-right corner of a card. */
fun Modifier.cornerWave(color: Color): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    val back = Path().apply {
        moveTo(w * 0.42f, h)
        cubicTo(w * 0.68f, h * 1.0f, w * 0.78f, h * 0.55f, w, h * 0.42f)
        lineTo(w, h); close()
    }
    drawPath(back, Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.22f)), startX = w * 0.42f, endX = w))
    val front = Path().apply {
        moveTo(w * 0.58f, h)
        cubicTo(w * 0.8f, h, w * 0.88f, h * 0.76f, w, h * 0.66f)
        lineTo(w, h); close()
    }
    drawPath(front, Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.55f)), startX = w * 0.58f, endX = w))
}

@Composable
fun IconTile(icon: ImageVector, tone: Tone, size: Dp = 52.dp, iconSize: Dp = 28.dp) {
    Box(
        Modifier.size(size).shadow(4.dp, RoundedCornerShape(16.dp)).background(tone.brush, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize)) }
}

@Composable
fun LetterTile(letter: String, tone: Tone, size: Dp = 52.dp) {
    Box(
        Modifier.size(size).shadow(4.dp, RoundedCornerShape(16.dp)).background(tone.brush, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

/** White card with a light tint and a wave in the corner. */
@Composable
fun WaveCard(tone: Tone, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color.White, tone.light.copy(alpha = 0.10f))))
                .cornerWave(tone.light)
        ) { content() }
    }
}

@Composable
fun SectionTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon, null, tint = Brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BrandDark)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.5.dp).background(Brand.copy(alpha = 0.6f)))
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.padding(8.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun EmptyBox(title: String, text: String) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.padding(4.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Tag(text: String, color: Color = Brand, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), maxLines = 1,
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String = "Yes", danger: Boolean = false,
    onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else Brand, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A dialog that lets the user pick one value from a (possibly long) list, with search. */
@Composable
fun PickerDialog(
    title: String, options: List<String>, selected: String,
    allLabel: String? = "All", onDismiss: () -> Unit, onPick: (String) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val shown = remember(search, options) {
        if (search.isBlank()) options else options.filter { it.contains(search.trim(), ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (options.size > 12) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, singleLine = true,
                        placeholder = { Text("Search") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (allLabel != null && search.isBlank()) {
                        item { PickRow(allLabel, selected.isBlank()) { onPick("") } }
                    }
                    items(shown) { opt -> PickRow(opt, opt == selected) { onPick(opt) } }
                    if (shown.isEmpty()) item { Text("Nothing found", Modifier.padding(12.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun PickRow(text: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal)
        if (checked) Icon(Icons.Filled.Check, null, tint = Brand)
    }
}

/** Text field with a drop-down list of suggestions. Typing a new value is allowed. */
@Composable
fun ComboField(
    label: String, value: String, options: List<String>, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, upperCase: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(if (upperCase) it.uppercase() else it) },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (options.isNotEmpty()) {
                    IconButton(onClick = { open = true }) { Icon(Icons.Filled.ArrowDropDown, "Choose") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            val filtered = options.filter { value.isBlank() || it.contains(value, true) }.ifEmpty { options }
            filtered.take(200).forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onChange(opt); open = false })
            }
        }
    }
}

@Composable
fun SegmentChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            if (on) {
                Button(onClick = { onSelect(value) }) { Text(label) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) { Text(label) }
            }
        }
    }
}

@Composable
fun OldStatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp, 36.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Clear") }
        },
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    )
}
