package com.informationplant.fdmn

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

private class CampItemDef(val key: String, val title: String, val icon: ImageVector, val tone: Tone, val hint: String)

private val CAMP_ITEMS = listOf(
    CampItemDef("location", "Camp Location", Icons.Filled.LocationOn, ToneTeal, "Google Map link"),
    CampItemDef("office", "Office Structure", Icons.Filled.AccountBox, TonePurple, "Designation, name and phone"),
    CampItemDef("info", "Camp Information", Icons.Filled.Info, ToneBlue, "Land area, projects, sectors, more rows"),
    CampItemDef("working", "Organization Working", Icons.Filled.Handshake, ToneOrange, "Organizations and what they work on"),
    CampItemDef("services", "Provided Services", Icons.Filled.Assignment, ToneGreen, "Services and numbers"),
    CampItemDef("orglist", "Organization List", Icons.AutoMirrored.Filled.List, TonePink, "Names of organizations"),
)

private val NavyLabel = Color(0xFF1E3A6E)

// ======================= Camp Profile (six buttons) =======================

@Composable
fun CampProfileScreen(app: AppCtl) {
    Scaffold(
        topBar = { BrandTopBar("Camp Profile", "About the Camp, FDMN and Activities", onBack = if (app.stack.size > 1) ({ app.back() }) else null) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(R.drawable.logo_full), contentDescription = "Office of the Camp-in-Charge, Camp-8 West",
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
            CAMP_ITEMS.forEach { d -> CampButton(d) { app.push(Screen.CampItem(d.key, false)) } }
            val editContacts: (@Composable () -> Unit)? = if (app.isAdmin) ({ EditChip { app.push(Screen.Contacts) } }) else null
            Panel("Emergency Response", Icons.Filled.NotificationImportant, iconTint = Color(0xFFD32F2F), titleColor = Color(0xFFD32F2F), action = editContacts) {
                ContactList(app.camp.contacts)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CampButton(d: CampItemDef, sub: String? = null, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp), color = d.tone.light.copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(d.icon, d.tone, 46.dp, 26.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF12302A))
                if (sub != null) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = d.tone.dark)
        }
    }
}

// ======================= one Camp Profile item: view, or edit (Admins) =======================

private fun fieldsFor(key: String): List<Pair<String, String>> = when (key) {
    "office" -> listOf("designation" to "Designation", "name" to "Name", "phone" to "Phone")
    "info" -> listOf("label" to "Title", "value" to "Value")
    "working" -> listOf("name" to "Organization name", "work" to "Working on (sector / activity)")
    "orglist" -> listOf("name" to "Organization name")
    "services" -> listOf("name" to "Service", "count" to "Number")
    else -> emptyList()
}

@Composable
fun CampItemScreen(app: AppCtl, key: String, startEdit: Boolean) {
    val d = CAMP_ITEMS.firstOrNull { it.key == key } ?: return
    var editing by remember { mutableStateOf(startEdit && app.isAdmin) }
    Scaffold(
        topBar = {
            BrandTopBar(d.title, if (editing) "Editing" else null, onBack = { app.back() }) {
                if (app.isAdmin && !editing) TextButton(onClick = { editing = true }) { Text("Edit", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (editing) CampItemEditor(app, key) { editing = false } else CampItemView(app, key)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CampItemView(app: AppCtl, key: String) {
    val c = app.camp
    val st = app.stats
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (key) {
            "location" -> {
                if (c.mapUrl.isBlank()) EmptyBox("No map yet", "An Admin can add the Google Maps link.")
                else {
                    Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(420.dp).border(1.dp, Color(0xFFDCE5E1), RoundedCornerShape(16.dp))) {
                        AndroidView(factory = { cx ->
                            WebView(cx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                val html = "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>html,body{margin:0;height:100%}iframe{border:0;width:100%;height:100%}</style></head>" +
                                    "<body><iframe src=\"${c.mapUrl.replace("\"", "%22")}\" allowfullscreen loading=\"lazy\" referrerpolicy=\"strict-origin-when-cross-origin\"></iframe></body></html>"
                                loadDataWithBaseURL("https://app.informationplant.com/", html, "text/html", "utf-8", null)
                            }
                        }, modifier = Modifier.fillMaxSize())
                    }
                    OutlinedButton(onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=Camp+8W+CiC+Office"))) } catch (_: Exception) { }
                    }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.LocationOn, null); Spacer(Modifier.width(6.dp)); Text("Open in Google Maps") }
                }
            }
            "office" -> {
                if (c.office.isEmpty()) EmptyBox("No officers yet", "")
                c.office.forEachIndexed { i, o ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            o["designation"].orEmpty(), Modifier.fillMaxWidth().background(if (i == 0) Color(0xFF7E3AD8) else NavyLabel, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).padding(12.dp),
                            color = Color.White, fontWeight = FontWeight.Bold,
                        )
                        Surface(shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp), color = Color(0xFFF1F3F2), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(o["name"].orEmpty(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                    Text(o["phone"].orEmpty(), fontSize = 15.sp)
                                }
                                if (!o["phone"].isNullOrBlank()) CallButton(o["phone"].orEmpty())
                            }
                        }
                    }
                }
            }
            "info" -> {
                val rows = listOf("Land Area" to c.area, "Total Population" to fmt(st.total), "Total Family" to fmt(st.families), "Total Blocks" to fmt(st.mainBlocks),
                    "Sub-Blocks" to fmt(st.subBlocks), "Total Organizations" to fmt(c.orgs.size), "Total Projects" to c.projects, "Sectors" to c.sectors) +
                    c.extra.map { it["label"].orEmpty() to it["value"].orEmpty() }
                rows.forEach { (l, v) -> LabelValue(l, v) }
            }
            "working" -> {
                c.orgs.forEachIndexed { i, o ->
                    Surface(shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DDDA)), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${i + 1}. ${o["name"].orEmpty()}", fontWeight = FontWeight.Bold)
                            Text(o["work"].orEmpty().ifBlank { "Working on: —" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LastUpdated(c.orgsUpdated, "${c.orgs.size} organizations")
            }
            "orglist" -> {
                c.orgs.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { j, o ->
                            Surface(shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DDDA)), modifier = Modifier.weight(1f)) {
                                Text("${r * 2 + j + 1}. ${o["name"].orEmpty()}", Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                LastUpdated(c.orgsUpdated, "${c.orgs.size} organizations")
            }
            "services" -> {
                c.services.forEachIndexed { i, s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DDDA)), modifier = Modifier.weight(1f)) {
                            Text("${i + 1}. ${s["name"].orEmpty()}", Modifier.padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Surface(shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DDDA)), modifier = Modifier.width(80.dp)) {
                            Text(s["count"].orEmpty(), Modifier.padding(10.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                LastUpdated(c.servicesUpdated, null)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f).background(NavyLabel, RoundedCornerShape(10.dp)).padding(12.dp), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
        Surface(shape = RoundedCornerShape(10.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5DDDA)), modifier = Modifier.weight(1f)) {
            Text(value.ifBlank { "—" }, Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LastUpdated(updated: String, extra: String?) {
    Text(
        "Last Updated: ${updatedText(updated)}" + (if (extra != null) " · $extra" else ""),
        Modifier.fillMaxWidth().padding(top = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End,
    )
}

/** Editable list of rows with "+ Add New" and delete buttons. */
@Composable
private fun RowsEditor(rows: MutableList<SnapshotStateMap<String, String>>, fields: List<Pair<String, String>>) {
    rows.forEachIndexed { i, r ->
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF6FAF8), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3ECE8)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fields.forEach { (k, label) ->
                        OutlinedTextField(r[k].orEmpty(), { r[k] = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
                IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
    OutlinedButton(onClick = { rows.add(fields.map { it.first to "" }.toMutableStateMap()) }) {
        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add New")
    }
}

@Composable
private fun CampItemEditor(app: AppCtl, key: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = app.camp
    val initial: List<DataRow> = when (key) {
        "office" -> c.office
        "info" -> c.extra
        "working", "orglist" -> c.orgs
        "services" -> c.services
        else -> emptyList()
    }
    val rows = remember { mutableStateListOf<SnapshotStateMap<String, String>>().apply { initial.forEach { add(it.toList().toMutableStateMap()) } } }
    var area by remember { mutableStateOf(c.area) }
    var projects by remember { mutableStateOf(c.projects) }
    var sectors by remember { mutableStateOf(c.sectors) }
    var mapUrl by remember { mutableStateOf(c.mapUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        val list = rows.map { r -> r.toMap() }
        val (skey, value) = when (key) {
            "location" -> "map" to jo("url" to mapUrl.trim())
            "office" -> "office" to rowsJson(list)
            "info" -> "camp_info2" to jo("area" to area, "projects" to projects, "sectors" to sectors, "extra" to rowsJson(list))
            "services" -> "services" to jo("items" to rowsJson(list))
            else -> "orgs" to jo("items" to rowsJson(list.map { r -> mapOf("name" to r["name"].orEmpty(), "work" to r["work"].orEmpty()) }))
        }
        busy = true; error = null
        scope.launch {
            try {
                Api.call("settings_save", jo("key" to skey, "value" to value))
                app.refreshCamp()
                app.toast("Saved.")
                onDone()
            } catch (e: Exception) { error = e.message } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (key) {
            "location" -> {
                Text("Paste the Google Maps \"Embed a map\" link. You can paste the whole <iframe> code.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(mapUrl, { mapUrl = it }, label = { Text("Google Maps link") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            }
            "info" -> {
                OutlinedTextField(area, { area = it }, label = { Text("Land Area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(projects, { projects = it }, label = { Text("Total Projects") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sectors, { sectors = it }, label = { Text("Sectors") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Total Population, Total Family, Total Blocks, Sub-Blocks and Total Organizations are counted automatically.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("More rows", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                RowsEditor(rows, fieldsFor(key))
            }
            else -> RowsEditor(rows, fieldsFor(key))
        }
        if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = { save() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("Save")
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

// ======================= Emergency contacts editor =======================

private val CONTACT_TYPES = listOf("fire" to "Fire", "police" to "Police", "hospital" to "Hospital", "site" to "Site / Office", "other" to "Other")

@Composable
fun ContactsEditScreen(app: AppCtl) {
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<SnapshotStateMap<String, String>>().apply { app.camp.contacts.forEach { add(it.toList().toMutableStateMap()) } } }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { BrandTopBar("Emergency Contacts", "Editing", onBack = { app.back() }) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEachIndexed { i, r ->
                var open by remember { mutableStateOf(false) }
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF6FAF8), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3ECE8)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box {
                                OutlinedButton(onClick = { open = true }) { Text("Type: " + (CONTACT_TYPES.firstOrNull { it.first == r["type"] }?.second ?: "Other")) }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    CONTACT_TYPES.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { r["type"] = v; open = false }) }
                                }
                            }
                            OutlinedTextField(r["name"].orEmpty(), { r["name"] = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(r["phone"].orEmpty(), { r["phone"] = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            OutlinedButton(onClick = { rows.add(listOf("type" to "other", "name" to "", "phone" to "").toMutableStateMap()) }) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Add New")
            }
            if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        Api.call("settings_save", jo("key" to "contacts", "value" to rowsJson(rows.map { it.toMap() })))
                        app.refreshCamp(); app.toast("Emergency contacts saved."); app.back()
                    } catch (e: Exception) { error = e.message } finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save contacts") }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ======================= Site Update (Admins) =======================

@Composable
fun SiteUpdateScreen(app: AppCtl) {
    Scaffold(topBar = { BrandTopBar("Site Update", "Manage users and update the Camp Profile", onBack = { app.back() }) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(Icons.Filled.PeopleOutline, "Users & data")
            CampButton(CampItemDef("", "Manage Users", Icons.Filled.PeopleOutline, ToneGreen, ""),
                sub = "Master Admin, Admins and Users" + if (app.stats.pendingUsers > 0) " · ${app.stats.pendingUsers} waiting" else "") { app.push(Screen.Users) }
            CampButton(CampItemDef("", "Import from Excel", Icons.Filled.UploadFile, ToneBlue, ""), sub = "Upload the beneficiary list (.xlsx or .csv)") { app.push(Screen.Import) }
            SectionTitle(Icons.Filled.AccountBox, "Camp Profile")
            CAMP_ITEMS.filter { it.key != "orglist" }.forEach { d -> CampButton(d, sub = d.hint) { app.push(Screen.CampItem(d.key, true)) } }
            CampButton(CampItemDef("", "Emergency Contacts", Icons.Filled.Phone, ToneRed, ""), sub = "Fire, police, hospital, site management") { app.push(Screen.Contacts) }
            Spacer(Modifier.height(20.dp))
        }
    }
}
