package com.informationplant.fdmn

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header(onRefresh = { app.refreshStats(); app.refreshMeta() }) }
        item { CampProfileCard(Modifier.padding(horizontal = 14.dp)) { app.push(Screen.CampProfile) } }
        item {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Filled.Groups, ToneGreen, fmt(st.total), "People", Modifier.weight(1f)) { app.openRecords(Filters()) }
                    StatCard(Icons.Filled.FamilyRestroom, ToneOrange, fmt(st.families), "Families", Modifier.weight(1f), null)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Icons.Filled.Man, ToneBlue, fmt(st.male), "Male", Modifier.weight(1f)) { app.openRecords(Filters(sex = "M")) }
                    StatCard(Icons.Filled.Woman, TonePink, fmt(st.female), "Female", Modifier.weight(1f)) { app.openRecords(Filters(sex = "F")) }
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
            item { AlertCard("${st.pendingUsers} new user(s) waiting for your approval") { app.tab(Screen.More); app.push(Screen.Users) } }
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
                EmptyBox(
                    "No records yet",
                    if (user.isAdmin) "Go to More → Import from Excel to upload your beneficiary list." else "The Admin has not added any records yet.",
                )
            }
        }
        itemsIndexed(meta.blocks, key = { _, b -> "m_" + b.name }) { i, mb ->
            MainBlockCard(app, mb, blockTone(i), Modifier.padding(horizontal = 14.dp))
        }
    }
}

@Composable
private fun Header(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(270.dp).clip(HeaderWave)) {
        Image(
            painterResource(R.drawable.header_bg), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.40f),
                    0.35f to Color.Black.copy(alpha = 0.08f),
                    1f to Color.Transparent,
                )
            )
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(end = 6.dp, top = 2.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White, modifier = Modifier.size(28.dp)) }
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = 34.dp), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.88f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(0.86f),
                ) {
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
fun WelcomeCard(user: User, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 6.dp, color = Color.Transparent, modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF0A6B4D), Color(0xFF0B5D3B), Color(0xFF138A4E))))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w * 0.35f, h); cubicTo(w * 0.6f, h * 0.95f, w * 0.75f, h * 0.55f, w, h * 0.35f); lineTo(w, h); close()
                    }
                    drawPath(p, Brush.horizontalGradient(listOf(Color(0x0049D17D), Color(0x9949D17D)), startX = w * 0.35f, endX = w))
                }
                .border(1.5.dp, Color(0xFF49D17D).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.padding(bottom = 12.dp).size(64.dp).background(Color(0xFF7FE0C0), CircleShape)
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(42.dp))
                    }
                    Surface(shape = RoundedCornerShape(50), color = Color(0xFF2E3B36).copy(alpha = 0.85f), modifier = Modifier.offset(y = 4.dp)) {
                        Text(
                            if (user.isAdmin) "👑 Admin" else "👤 Viewer",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.5.dp).height(64.dp).background(Color.White.copy(alpha = 0.7f)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Welcome, ${user.name}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (user.isAdmin) "Admin · You can add, edit and delete records." else "Viewer · You can search and view records.",
                        color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(icon: ImageVector, tone: Tone, value: String, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    WaveCard(tone, modifier, onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tone, 52.dp, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(value, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = tone.dark, maxLines = 1)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
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
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LetterTile(mb.name.ifBlank { "?" }.take(2), tone, 54.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(1.5.dp).height(50.dp).background(tone.light.copy(alpha = 0.6f)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Main Block ${mb.name.ifBlank { "(none)" }}", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, color = Color(0xFF14201C))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiniStat(Icons.Filled.Groups, "${fmt(mb.count)} people")
                        Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MiniStat(Icons.Filled.FamilyRestroom, "${fmt(mb.families)} families")
                    }
                    MiniStat(Icons.Filled.Layers, "${mb.subs.size} sub-blocks")
                }
                Box(
                    Modifier.size(40.dp).background(tone.light.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = tone.dark)
                }
            }
            if (open) {
                HorizontalDivider(color = tone.light.copy(alpha = 0.3f))
                SubRow("All of Main Block ${mb.name}", mb.count, mb.families, tone) { app.openRecords(Filters(main = mb.name)) }
                mb.subs.forEach { sb ->
                    SubRow("Sub-Block ${sb.name.ifBlank { "(none)" }}", sb.count, sb.families, tone) {
                        app.openRecords(Filters(main = mb.name, sub = sb.name))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun SubRow(title: String, count: Int, families: Int, tone: Tone, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    val p = Path().apply {
                        moveTo(w * 0.35f, h); cubicTo(w * 0.6f, h * 0.95f, w * 0.75f, h * 0.55f, w, h * 0.35f); lineTo(w, h); close()
                    }
                    drawPath(p, Brush.horizontalGradient(listOf(Color(0x008FB0FF), Color(0x998FB0FF)), startX = w * 0.35f, endX = w))
                }
                .border(1.5.dp, Color(0xFF8FB0FF).copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).background(Color.White, CircleShape).border(3.dp, Color(0xFFBFD0FF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
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

/** Welcome card floating in the middle of the screen; closes after 5 seconds or with the X. */
@Composable
fun WelcomePopup(user: User, onClose: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(5000)
        onClose()
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            WelcomeCard(user, Modifier.padding(top = 14.dp, end = 10.dp))
            Box(
                Modifier.align(Alignment.TopEnd).size(36.dp).shadow(4.dp, CircleShape)
                    .background(Color.White, CircleShape).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Close", tint = BrandDark, modifier = Modifier.size(22.dp))
            }
        }
    }
}
