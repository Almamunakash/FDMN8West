package com.informationplant.fdmn

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FireTruck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val WelcomeBlue = Color(0xFF1E40AF)
private val AgeColors = mapOf("u5" to Color(0xFFA855F7), "c6" to Color(0xFF2196F3), "c13" to Color(0xFF22C4C4), "e60" to Color(0xFFEC4899))

private class InfoItem(val icon: ImageVector, val label: String, val value: String, val tone: Tone)
private class Notice(val title: String, val sub: String, val toUsers: Boolean)

@Composable
fun HomeScreen(app: AppCtl) {
    app.user ?: return
    LaunchedEffect(app.version) { app.refreshStats() }
    val st = app.stats
    var bell by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { WelcomeBar(app) { bell = true } }
        item {
            val pct = { n: Int -> if (st.total > 0) String.format(Locale.US, "%.1f%%", n * 100.0 / st.total) else "0%" }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SumCard(Icons.Filled.Groups, ToneGreen, fmt(st.total), "Total Population", null, Modifier.weight(1f)) { app.openRecords(Filters()) }
                    SumCard(Icons.Filled.FamilyRestroom, ToneOrange, fmt(st.families), "Families", null, Modifier.weight(1f), null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SumCard(Icons.Filled.Man, ToneBlue, fmt(st.male), "Male", "(${pct(st.male)})", Modifier.weight(1f)) { app.openRecords(Filters(sex = "M")) }
                    SumCard(Icons.Filled.Woman, TonePink, fmt(st.female), "Female", "(${pct(st.female)})", Modifier.weight(1f)) { app.openRecords(Filters(sex = "F")) }
                }
            }
        }
        item {
            val c = app.camp
            val editInfo: (@Composable () -> Unit)? = if (app.isAdmin) ({ EditChip { app.push(Screen.CampItem("info", true)) } }) else null
            Panel("Camp Information", Icons.Filled.Info, Modifier.padding(horizontal = 14.dp), action = editInfo) {
                val tiles = mutableListOf(
                    InfoItem(Icons.Filled.Terrain, "Land Area", c.area, ToneGreen),
                    InfoItem(Icons.Filled.Groups, "Total Organizations", c.orgs.size.toString(), ToneBlue),
                    InfoItem(Icons.Filled.Settings, "Total Projects", c.projects, TonePink),
                    InfoItem(Icons.Filled.Layers, "Sectors", c.sectors, ToneTeal),
                )
                c.extra.take(2).forEach { tiles += InfoItem(Icons.Filled.Info, it["label"].orEmpty(), it["value"].orEmpty(), ToneIndigo) }
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        pair.forEach { t -> InfoTile(t, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            val ages = listOf(Triple("u5", "0 – 5 yrs", st.under5), Triple("c6", "6 – 12 yrs", st.age6to12), Triple("c13", "13 – 17 yrs", st.age13to17), Triple("e60", "Elderly 60+", st.elderly))
            val sum = ages.sumOf { it.third }
            Panel("Population by Age Group", Icons.Filled.People, Modifier.padding(horizontal = 14.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(180.dp)) {
                            val stroke = 34.dp.toPx()
                            val inset = stroke / 2
                            val arcSize = Size(size.width - stroke, size.height - stroke)
                            drawArc(Color(0xFFEEF3F1), 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                            var start = -90f
                            ages.forEach { (k, _, v) ->
                                if (sum > 0 && v > 0) {
                                    val sweep = 360f * v / sum
                                    drawArc(AgeColors[k] ?: Color.Gray, start, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                                    start += sweep
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(fmt(st.total), fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text("People", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ages.forEach { (k, label, v) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { app.openRecords(Filters(age = k)) }.padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).background(AgeColors[k] ?: Color.Gray, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Text(label, Modifier.weight(1f))
                        Text(fmt(v), fontWeight = FontWeight.ExtraBold)
                        Text(if (st.total > 0) String.format(Locale.US, "  (%.1f%%)", v * 100.0 / st.total) else "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        item {
            Panel("Key Summary", Icons.Filled.Assignment, Modifier.padding(horizontal = 14.dp)) {
                KeyRow(Icons.Filled.Groups, "Total Population", fmt(st.total))
                KeyRow(Icons.Filled.FamilyRestroom, "Total Families", fmt(st.families))
                KeyRow(Icons.Filled.Layers, "Total Blocks", fmt(st.mainBlocks))
                KeyRow(Icons.Filled.GridView, "Total Sub-blocks", fmt(st.subBlocks), last = true)
            }
        }
        item {
            Panel("Block-wise Summary", Icons.Filled.Layers, Modifier.padding(horizontal = 14.dp)) {
                if (app.meta.blocks.isEmpty()) {
                    EmptyBox("No records yet", if (app.isAdmin) "Go to More → Site Update → Import from Excel." else "The Admin has not added any records yet.")
                }
                app.meta.blocks.forEachIndexed { i, mb -> BlockCard(mb, blockTone(i)) { app.openRecords(Filters(main = mb.name)) } }
            }
        }
        item {
            val editContacts: (@Composable () -> Unit)? = if (app.isAdmin) ({ EditChip { app.push(Screen.Contacts) } }) else null
            Panel(
                "Emergency Response", Icons.Filled.NotificationImportant, Modifier.padding(horizontal = 14.dp),
                iconTint = Color(0xFFD32F2F), titleColor = Color(0xFFD32F2F), action = editContacts,
            ) { ContactList(app.camp.contacts) }
        }
        item {
            Panel("Quick Actions", Icons.Filled.Bolt, Modifier.padding(horizontal = 14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (app.isAdmin) QuickBtn(Icons.Filled.AddCircle, "Add Record", ToneGreen, Modifier.weight(1f)) { app.push(Screen.Edit(Person())) }
                    QuickBtn(Icons.Filled.BarChart, "Reports", ToneBlue, Modifier.weight(1f)) { app.tab(Screen.Reports) }
                    QuickBtn(Icons.Filled.Download, "Export", TonePurple, Modifier.weight(1f)) { export = true }
                }
            }
        }
    }
    if (bell) NotificationsDialog(app) { bell = false }
    if (export) ExportChooser(app) { export = false }
}

@Composable
private fun WelcomeBar(app: AppCtl, onBell: () -> Unit) {
    val today = LocalDate.now()
    val count = if (app.isAdmin) app.stats.pendingUsers + app.stats.openMessages else app.stats.unreadReplies
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 3.dp) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = BrandDark, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(today.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(today.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp, modifier = Modifier.size(50.dp).clip(CircleShape).clickable(onClick = onBell)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Notifications, "Notifications", tint = BrandDark, modifier = Modifier.size(28.dp)) }
                    }
                    if (count > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(20.dp).background(Color(0xFFE53935), CircleShape).border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Welcome to Camp- 8 West Information Management System", color = WelcomeBlue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 27.sp)
        }
    }
}

@Composable
private fun SumCard(icon: ImageVector, tone: Tone, value: String, label: String, sub: String?, modifier: Modifier, onClick: (() -> Unit)?) {
    WaveCard(tone, modifier, onClick) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            IconTile(icon, tone, 48.dp, 28.dp)
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Black, color = tone.dark, maxLines = 1)
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 13.sp)
        }
    }
}

@Composable
private fun InfoTile(t: InfoItem, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = t.tone.light.copy(alpha = 0.16f), modifier = modifier) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(t.icon, null, tint = t.tone.dark, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(t.label, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2)
                Text(t.value.ifBlank { "—" }, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 2)
            }
        }
    }
}

@Composable
private fun KeyRow(icon: ImageVector, label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Brand, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Text(value, fontSize = 19.sp, fontWeight = FontWeight.Black)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun BlockCard(mb: MainBlock, tone: Tone, onClick: () -> Unit) {
    WaveCard(tone, Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            LetterTile(mb.name.ifBlank { "?" }.take(2), tone, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Main Block ${mb.name.ifBlank { "(none)" }}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                MiniLine(Icons.Filled.Groups, "${fmt(mb.count)} people")
                MiniLine(Icons.Filled.FamilyRestroom, "${fmt(mb.families)} families")
                MiniLine(Icons.Filled.Layers, "${mb.subs.size} sub-blocks")
            }
            Icon(Icons.Filled.Terrain, null, tint = tone.light.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
        }
    }
}

@Composable
private fun MiniLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF3A4743), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, color = Color(0xFF26322E))
    }
}

@Composable
private fun QuickBtn(icon: ImageVector, label: String, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = tone.light.copy(alpha = 0.22f),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tone.dark, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, maxLines = 1)
        }
    }
}

// ---------------- shared pieces (also used by Camp Profile) ----------------

@Composable
fun Panel(
    title: String, icon: ImageVector, modifier: Modifier = Modifier, iconTint: Color = Brand, titleColor: Color = Color(0xFF12302A),
    action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = titleColor)
                if (action != null) action()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
fun EditChip(onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFEEF5F2), modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Edit, null, tint = BrandDark, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Edit", fontWeight = FontWeight.Bold, color = BrandDark, fontSize = 13.sp)
        }
    }
}

fun dial(ctx: android.content.Context, phone: String) {
    val n = phone.filter { it.isDigit() || it == '+' }
    if (n.isEmpty()) return
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n"))) } catch (_: Exception) { }
}

@Composable
fun CallButton(phone: String) {
    val ctx = LocalContext.current
    Box(
        Modifier.size(46.dp).shadow(4.dp, CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3DDC6F), Color(0xFF16A34A))), CircleShape)
            .clip(CircleShape).clickable { dial(ctx, phone) },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Phone, "Call", tint = Color.White, modifier = Modifier.size(24.dp)) }
}

private class ContactStyle(val icon: ImageVector, val fg: Color, val bg: Color)

@Composable
fun ContactList(contacts: List<DataRow>) {
    if (contacts.isEmpty()) { Text("No contacts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    contacts.forEach { c ->
        val cs = when (c["type"]) {
            "fire" -> ContactStyle(Icons.Filled.FireTruck, Color(0xFFD32F2F), Color(0xFFFDECEA))
            "police" -> ContactStyle(Icons.Filled.LocalPolice, Color(0xFF1E3A8A), Color(0xFFE8EEFC))
            "hospital" -> ContactStyle(Icons.Filled.LocalHospital, Color(0xFF1565C0), Color(0xFFE6F3FB))
            "site" -> ContactStyle(Icons.Filled.Engineering, Color(0xFFEF6C00), Color(0xFFFFF2DE))
            else -> ContactStyle(Icons.Filled.Phone, Brand, Color(0xFFEEF5F2))
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(cs.bg, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(cs.icon, null, tint = cs.fg, modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c["name"].orEmpty(), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(c["phone"].orEmpty(), fontSize = 15.sp)
                }
                if (!c["phone"].isNullOrBlank()) CallButton(c["phone"].orEmpty())
            }
        }
    }
}

@Composable
private fun NotificationsDialog(app: AppCtl, onClose: () -> Unit) {
    var items by remember { mutableStateOf<List<Notice>?>(null) }
    LaunchedEffect(Unit) {
        val out = mutableListOf<Notice>()
        try {
            if (app.isAdmin) {
                Api.call("users").optJSONArray("users").mapObjects { User.from(it) }.filter { it.status == "pending" }
                    .forEach { out += Notice("${it.name} wants to join", "${it.email} · waiting for approval", true) }
                Api.call("msg_all", jo("status" to "open")).optJSONArray("messages").mapObjects { Message.from(it) }
                    .forEach { out += Notice(it.subject.ifBlank { "Request" }, "${it.userName} · ${it.createdAt}", false) }
            } else {
                Api.call("msg_mine").optJSONArray("messages").mapObjects { Message.from(it) }.filter { !it.userSeen && it.reply.isNotBlank() }
                    .forEach { out += Notice("Admin replied: ${it.subject.ifBlank { "Request" }}", it.reply.take(80), false) }
                app.refreshStats()
            }
        } catch (_: Exception) { }
        items = out
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Notifications") },
        text = {
            val l = items
            when {
                l == null -> Text("Loading…")
                l.isEmpty() -> Text("No new notifications.")
                else -> LazyColumn(Modifier.height(360.dp)) {
                    items(l) { n ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                onClose()
                                if (n.toUsers) { app.tab(Screen.More); app.push(Screen.Site); app.push(Screen.Users) } else app.tab(Screen.Messages)
                            }.padding(vertical = 10.dp),
                        ) {
                            Text(n.title, fontWeight = FontWeight.Bold)
                            Text(n.sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
