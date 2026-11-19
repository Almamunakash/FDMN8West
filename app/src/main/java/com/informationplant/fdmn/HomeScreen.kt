package com.informationplant.fdmn

import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
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

private val HeaderWave = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * 0.90f)
    cubicTo(size.width * 0.72f, size.height * 1.02f, size.width * 0.36f, size.height * 0.80f, 0f, size.height * 0.93f)
    close()
}

@Composable
fun HomeScreen(app: AppCtl) {
    val user = app.user ?: return
    LaunchedEffect(app.version) { app.refreshStats() }
    val st = app.stats
    val meta = app.meta
    var bell by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    val pct = { n: Int -> if (st.total > 0) String.format(Locale.US, "%.1f%%", n * 100.0 / st.total) else "0%" }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Header(app, onBell = { bell = true }, onRefresh = { app.refreshStats(); app.refreshMeta(); app.refreshCamp() })
                Text(
                    "Welcome to Camp- 8 West Information Management System",
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    color = WelcomeBlue, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp,
                )
            }
        }
        item { CampProfileCard(Modifier.padding(horizontal = 14.dp)) { app.push(Screen.CampProfile) } }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Filled.Groups, ToneGreen, fmt(st.total), "People", null, Modifier.weight(1f)) { app.openRecords(Filters()) }
                    StatCard(Icons.Filled.FamilyRestroom, ToneOrange, fmt(st.families), "Families", null, Modifier.weight(1f), null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Filled.Man, ToneBlue, fmt(st.male), "Male", pct(st.male), Modifier.weight(1f)) { app.openRecords(Filters(sex = "M")) }
                    StatCard(Icons.Filled.Woman, TonePink, fmt(st.female), "Female", pct(st.female), Modifier.weight(1f)) { app.openRecords(Filters(sex = "F")) }
                }
            }
        }
        item { Box(Modifier.padding(horizontal = 14.dp)) { SectionTitle(Icons.Filled.Groups, "Age groups") } }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val w = Modifier.width(118.dp)
                ChildCard(Icons.Filled.ChildFriendly, TonePurple, fmt(st.under5), "0–5 yrs", w) { app.openRecords(Filters(age = "u5")) }
                ChildCard(Icons.Filled.ChildCare, ToneTeal, fmt(st.age6to12), "6–12 yrs", w) { app.openRecords(Filters(age = "c6")) }
                ChildCard(Icons.Filled.EmojiPeople, ToneIndigo, fmt(st.age13to17), "13–17 yrs", w) { app.openRecords(Filters(age = "c13")) }
                ChildCard(Icons.Filled.Elderly, ToneRed, fmt(st.elderly), "Elderly 60+", w) { app.openRecords(Filters(age = "e60")) }
            }
        }
        if (user.isAdmin && st.pendingUsers > 0) {
            item { AlertCard("${st.pendingUsers} new user(s) waiting for your approval") { app.push(Screen.Users) } }
        }
        if (user.isAdmin && st.openMessages > 0) {
            item { AlertCard("${st.openMessages} open message request(s)") { app.tab(Screen.Messages) } }
        }
        if (!user.isAdmin && st.unreadReplies > 0) {
            item { AlertCard("The Admin replied to ${st.unreadReplies} of your request(s)") { app.tab(Screen.Messages) } }
        }
        item { Box(Modifier.padding(horizontal = 14.dp)) { SectionTitle(Icons.Filled.Layers, "Blocks") } }
        if (meta.blocks.isEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 14.dp)) {
                    EmptyBox("No records yet", if (user.isAdmin) "Go to More → Site Update → Import from Excel." else "The Admin has not added any records yet.")
                }
            }
        }
        itemsIndexed(meta.blocks, key = { _, b -> "m_" + b.name }) { i, mb ->
            MainBlockCard(app, mb, blockTone(i), Modifier.padding(horizontal = 14.dp))
        }
        item { Box(Modifier.padding(horizontal = 14.dp)) { SectionTitle(Icons.Filled.Info, "Camp Information") } }
        item {
            val c = app.camp
            val tiles = mutableListOf(
                InfoItem(Icons.Filled.Terrain, "Land Area", c.area, ToneGreen),
                InfoItem(Icons.Filled.Groups, "Organizations", c.orgs.size.toString(), ToneBlue),
                InfoItem(Icons.Filled.Layers, "Main Blocks", fmt(st.mainBlocks), TonePurple),
                InfoItem(Icons.Filled.GridView, "Sub-Blocks", fmt(st.subBlocks), ToneTeal),
                InfoItem(Icons.Filled.Settings, "Projects", c.projects, TonePink),
                InfoItem(Icons.Filled.Category, "Sectors", c.sectors, ToneOrange),
            )
            c.extra.forEach { tiles += InfoItem(Icons.Filled.Info, it["label"].orEmpty(), it["value"].orEmpty(), ToneIndigo) }
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { t -> InfoTile(t, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (app.isAdmin) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { EditChip { app.push(Screen.CampItem("info", true)) } }
            }
        }
        item {
            val editContacts: (@Composable () -> Unit)? = if (app.isAdmin) ({ EditChip { app.push(Screen.Contacts) } }) else null
            Panel(
                "Emergency Response", Icons.Filled.NotificationImportant, Modifier.padding(horizontal = 14.dp),
                iconTint = Color(0xFFD32F2F), titleColor = Color(0xFFD32F2F), action = editContacts,
            ) { ContactList(app.camp.contacts) }
        }
        item { Box(Modifier.padding(horizontal = 14.dp)) { SectionTitle(Icons.Filled.Bolt, "Quick Actions") } }
        item {
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (app.isAdmin) QuickBtn(Icons.Filled.AddCircle, "Add Record", ToneGreen, Modifier.weight(1f)) { app.push(Screen.Edit(Person())) }
                QuickBtn(Icons.Filled.BarChart, "Reports", ToneBlue, Modifier.weight(1f)) { app.tab(Screen.Reports) }
                QuickBtn(Icons.Filled.Download, "Export", TonePurple, Modifier.weight(1f)) { export = true }
            }
        }
    }
    if (bell) NotificationsDialog(app) { bell = false }
    if (export) ExportChooser(app) { export = false }
}

@Composable
private fun Header(app: AppCtl, onBell: () -> Unit, onRefresh: () -> Unit) {
    val today = LocalDate.now()
    val count = if (app.isAdmin) app.stats.pendingUsers + app.stats.openMessages else app.stats.unreadReplies
    Box(Modifier.fillMaxWidth().height(280.dp).clip(HeaderWave)) {
        Image(painterResource(R.drawable.header_bg), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.35f to Color.Black.copy(alpha = 0.08f), 1f to Color.Transparent)))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.28f)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            today.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)),
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = onBell) { Icon(Icons.Filled.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    if (count > 0) {
                        Box(
                            Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp).size(18.dp).background(Color(0xFFE53935), CircleShape).border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 34.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.88f), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.86f)) {
                    Image(
                        painterResource(R.drawable.logo_full), contentDescription = "Office of the Camp-in-Charge, Camp-8 West",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, tone: Tone, value: String, label: String, sub: String?, modifier: Modifier, onClick: (() -> Unit)?) {
    WaveCard(tone, modifier, onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tone, 52.dp, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = tone.dark, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    if (sub != null) Text("  $sub", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tone.dark)
                }
            }
        }
    }
}

@Composable
private fun ChildCard(icon: ImageVector, tone: Tone, value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    WaveCard(tone, modifier, onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(icon, tone, 42.dp, 24.dp)
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = tone.dark, maxLines = 1)
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun AlertCard(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(horizontal = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Text(text, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF3A4743), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, fontSize = 14.sp, color = Color(0xFF26322E), maxLines = 1)
    }
}

@Composable
private fun MainBlockCard(app: AppCtl, mb: MainBlock, tone: Tone, modifier: Modifier) {
    var open by rememberSaveable(mb.name) { mutableStateOf(false) }
    WaveCard(tone, modifier) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                LetterTile(mb.name.ifBlank { "?" }.take(2), tone, 54.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.5.dp).height(50.dp).background(tone.light.copy(alpha = 0.6f)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Main Block ${mb.name.ifBlank { "(none)" }}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color(0xFF14201C))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniStat(Icons.Filled.Groups, "${fmt(mb.count)} people")
                        Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MiniStat(Icons.Filled.FamilyRestroom, fmt(mb.families))
                    }
                    MiniStat(Icons.Filled.Layers, "${mb.subs.size} sub-blocks")
                }
                Box(Modifier.size(40.dp).background(tone.light.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = tone.dark)
                }
            }
            if (open) {
                HorizontalDivider(color = tone.light.copy(alpha = 0.3f))
                SubRow("All of Main Block ${mb.name}", mb.count, mb.families, tone) { app.openRecords(Filters(main = mb.name)) }
                mb.subs.forEach { sb ->
                    SubRow("Sub-Block ${sb.name.ifBlank { "(none)" }}", sb.count, sb.families, tone) { app.openRecords(Filters(main = mb.name, sub = sb.name)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SubRow(title: String, count: Int, families: Int, tone: Tone, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).shadow(2.dp, CircleShape).background(tone.brush, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("${fmt(count)} people · ${fmt(families)} families", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tone.dark)
    }
}

@Composable
private fun CampProfileCard(modifier: Modifier, onClick: () -> Unit) {
    val blue = Color(0xFF3354BD)
    Surface(
        shape = RoundedCornerShape(22.dp), shadowElevation = 6.dp, color = Color.Transparent,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF2A47A8), blue, Color(0xFF4F74E0))))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply { moveTo(w * 0.35f, h); cubicTo(w * 0.6f, h * 0.95f, w * 0.75f, h * 0.55f, w, h * 0.35f); lineTo(w, h); close() }
                    drawPath(p, Brush.horizontalGradient(listOf(Color(0x008FB0FF), Color(0x998FB0FF)), startX = w * 0.35f, endX = w))
                }
                .border(1.5.dp, Color(0xFF8FB0FF).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(Color.White, CircleShape).border(3.dp, Color(0xFFBFD0FF), CircleShape), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.emblem), contentDescription = null, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.5.dp).height(56.dp).background(Color.White.copy(alpha = 0.7f)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Camp Profile", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("About the Camp, FDMN and Activities", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun InfoTile(t: InfoItem, modifier: Modifier) {
    WaveCard(t.tone, modifier) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(t.icon, t.tone, 40.dp, 22.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(t.value.ifBlank { "—" }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = t.tone.dark, maxLines = 2, lineHeight = 20.sp)
                Text(t.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun QuickBtn(icon: ImageVector, label: String, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    WaveCard(tone, modifier, onClick) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(icon, tone, 40.dp, 22.dp)
            Spacer(Modifier.height(6.dp))
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
