package com.informationplant.fdmn

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(app: AppCtl) {
    val user = app.user ?: return
    LaunchedEffect(app.version) { app.refreshStats() }
    val st = app.stats
    val meta = app.meta

    Scaffold(
        topBar = {
            BrandTopBar("8 West FDMN Database", "Camp-8W · Ukhiya") {
                IconButton(onClick = { app.refreshStats(); app.refreshMeta() }) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Transparent) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Brand, Color(0xFF138A6E))), RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Text("Hello, ${user.name}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (user.isAdmin) "Admin · you can add, edit and delete records" else "Viewer · you can search and view records",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("People", fmt(st.total), Brand, Modifier.weight(1f)) { app.openRecords(Filters()) }
                        StatCard("Families", fmt(st.families), Accent, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Male", fmt(st.male), Male, Modifier.weight(1f)) { app.openRecords(Filters(sex = "M")) }
                        StatCard("Female", fmt(st.female), Female, Modifier.weight(1f)) { app.openRecords(Filters(sex = "F")) }
                    }
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
            item {
                Text("Blocks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
            if (meta.blocks.isEmpty()) {
                item {
                    EmptyBox(
                        "No records yet",
                        if (user.isAdmin) "Go to More → Import from Excel to upload your beneficiary list." else "The Admin has not added any records yet.",
                    )
                }
            }
            items(meta.blocks, key = { "m_" + it.name }) { mb -> MainBlockCard(app, mb) }
        }
    }
}

@Composable
private fun AlertCard(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
private fun MainBlockCard(app: AppCtl, mb: MainBlock) {
    var open by rememberSaveable(mb.name) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                    Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(mb.name.ifBlank { "?" }, fontWeight = FontWeight.Bold, color = Brand, fontSize = 16.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Main Block ${mb.name.ifBlank { "(none)" }}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${fmt(mb.count)} people · ${fmt(mb.families)} families · ${mb.subs.size} sub-blocks",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (open) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SubRow("All of Main Block ${mb.name}", mb.count, mb.families) { app.openRecords(Filters(main = mb.name)) }
                mb.subs.forEach { sb ->
                    SubRow("Sub-Block ${sb.name.ifBlank { "(none)" }}", sb.count, sb.families) {
                        app.openRecords(Filters(main = mb.name, sub = sb.name))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubRow(title: String, count: Int, families: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text("${fmt(count)} people · ${fmt(families)} families", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
