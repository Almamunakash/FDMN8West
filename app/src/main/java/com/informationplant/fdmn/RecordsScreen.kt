package com.informationplant.fdmn

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun RecordsScreen(app: AppCtl) {
    val rs = app.records
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<String?>(null) }
    var exportOpen by remember { mutableStateOf(false) }

    // search box -> filters, after a short pause in typing
    LaunchedEffect(rs.query) {
        if (rs.query.trim() != rs.filters.q) {
            delay(450)
            rs.filters = rs.filters.copy(q = rs.query.trim())
        }
    }
    val key = Pair(rs.filters, app.version)
    LaunchedEffect(key) {
        if (rs.loadedKey != key) rs.reload(key)
    }
    val needMore by remember {
        derivedStateOf {
            val last = rs.listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            rs.items.isNotEmpty() && last >= rs.items.size - 15
        }
    }
    LaunchedEffect(needMore, rs.items.size) {
        if (needMore && !rs.endReached && !rs.loading && rs.error == null) rs.loadMore()
    }

    Scaffold(
        topBar = {
            BrandTopBar("Records", "${fmt(rs.total)} found · ${rs.filters.describe()}") {
                IconButton(onClick = { if (rs.total > 0) exportOpen = true else app.toast("Nothing to export.") }) {
                    Icon(Icons.Filled.Share, "Export")
                }
            }
        },
        floatingActionButton = {
            if (app.isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { app.push(Screen.Edit(Person(mainBlock = rs.filters.main, subBlock = rs.filters.sub))) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add") },
                    containerColor = Brand, contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Column(Modifier.padding(horizontal = 12.dp).padding(top = 10.dp)) {
                SearchBox(rs.query, { rs.query = it }, "Search name, FCN or Majhi")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val f = rs.filters
                    FilterPill("Main", f.main) { picker = "main" }
                    FilterPill("Sub-Block", f.sub) { picker = "sub" }
                    FilterPill("Sex", when (f.sex) { "M" -> "Male"; "F" -> "Female"; else -> f.sex }) { picker = "sex" }
                    FilterPill("Age group", if (f.age.isBlank()) "" else ageLabel(f.age)) { picker = "age" }
                    FilterPill("Relation", f.relation) { picker = "relation" }
                    FilterPill("Majhi", f.majhi) { picker = "majhi" }
                    if (f.active) {
                        TextButton(onClick = { rs.query = ""; rs.filters = Filters() }) {
                            Icon(Icons.Filled.Clear, null)
                            Text("Clear")
                        }
                    }
                }
            }
            if (rs.loading && rs.items.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            when {
                rs.error != null && rs.items.isEmpty() -> ErrorBox(rs.error ?: "") { scope.launch { rs.reload(key) } }
                !rs.loading && rs.items.isEmpty() && rs.loadedKey == key ->
                    EmptyBox("No records found", if (rs.filters.active) "Try a different search or clear the filters." else "No records have been added yet.")
                else -> LazyColumn(
                    state = rs.listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(rs.items, key = { _, p -> p.id }) { i, p ->
                        PersonCard(i + 1, p) { app.push(Screen.Detail(p.id)) }
                    }
                    if (!rs.endReached || rs.loading) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                if (rs.error != null) {
                                    OutlinedButton(onClick = { rs.error = null; scope.launch { rs.loadMore() } }) { Text("Load more") }
                                } else {
                                    CircularProgressIndicator(Modifier.width(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val meta = app.meta
    when (picker) {
        "main" -> PickerDialog("Main Block", meta.mainNames, rs.filters.main, onDismiss = { picker = null }) { v ->
            val subs = meta.subNames(v)
            rs.filters = rs.filters.copy(main = v, sub = if (rs.filters.sub in subs) rs.filters.sub else ""); picker = null
        }
        "sub" -> PickerDialog("Sub-Block", meta.subNames(rs.filters.main), rs.filters.sub, onDismiss = { picker = null }) { v ->
            rs.filters = rs.filters.copy(sub = v); picker = null
        }
        "sex" -> PickerDialog("Sex", listOf("M", "F"), rs.filters.sex, onDismiss = { picker = null }) { v ->
            rs.filters = rs.filters.copy(sex = v); picker = null
        }
        "age" -> PickerDialog("Age group", AGE_GROUPS.map { it.second }, ageLabel(rs.filters.age), onDismiss = { picker = null }) { v ->
            rs.filters = rs.filters.copy(age = AGE_GROUPS.firstOrNull { it.second == v }?.first ?: ""); picker = null
        }
        "relation" -> PickerDialog("Relation", meta.relations, rs.filters.relation, onDismiss = { picker = null }) { v ->
            rs.filters = rs.filters.copy(relation = v); picker = null
        }
        "majhi" -> PickerDialog("Majhi", meta.majhis, rs.filters.majhi, onDismiss = { picker = null }) { v ->
            rs.filters = rs.filters.copy(majhi = v); picker = null
        }
    }
    if (exportOpen) ExportDialog(app, rs.filters, rs.total) { exportOpen = false }
}

@Composable
private fun FilterPill(label: String, value: String, onClick: () -> Unit) {
    FilterChip(
        selected = value.isNotBlank(),
        onClick = onClick,
        label = { Text(if (value.isBlank()) label else "$label: $value", maxLines = 1) },
        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}

@Composable
fun PersonCard(sl: Int, p: Person, onClick: () -> Unit) {
    val tone = when (p.sex) { "F" -> TonePink; "M" -> ToneBlue; else -> ToneGreen }
    WaveCard(tone, Modifier.fillMaxWidth(), onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).shadow(3.dp, RoundedCornerShape(14.dp)).background(tone.brush, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SL", fontSize = 9.sp, color = Color.White.copy(alpha = 0.85f))
                    Text(fmt(sl), fontSize = if (sl > 9999) 11.sp else 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    if (p.relation.isNotBlank()) Tag(p.relation, Accent)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(p.sexText, p.ageText, p.dobText).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tone.dark,
                )
                Text(
                    "FCN ${p.fcn.ifBlank { "—" }} · ${p.blockText.ifBlank { "No block" }}" + (if (p.majhi.isNotBlank()) " · ${p.majhi}" else ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------- export ----------------

class ExportResult(val file: File, val mime: String)

/** total < 0 means "not known yet". */
@Composable
fun ExportDialog(app: AppCtl, filters: Filters, total: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ExportResult?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val r = result
        if (uri != null && r != null) {
            try { Exporter.copyTo(ctx, r.file, uri); app.toast("Saved ${r.file.name}") } catch (e: Exception) { app.toast("Could not save: ${e.message}") }
        }
    }

    fun run(pdf: Boolean) {
        busy = true; error = null
        scope.launch {
            try {
                val all = ArrayList<Person>(maxOf(total, 16))
                while (true) {
                    progress = if (total >= 0) "Downloading records… ${fmt(all.size)} / ${fmt(total)}" else "Downloading records… ${fmt(all.size)}"
                    val r = Api.call("list", filters.toJson().put("per", 2000).put("offset", all.size))
                    val rows = r.optJSONArray("rows").mapObjects { Person.from(it) }
                    all.addAll(rows)
                    if (rows.isEmpty() || all.size >= r.optInt("total")) break
                }
                if (all.isEmpty()) { error = "No records match this choice."; return@launch }
                progress = if (pdf) "Creating PDF… (${fmt(all.size)} records)" else "Creating Excel file… (${fmt(all.size)} records)"
                result = withContext(Dispatchers.Default) { makeFile(ctx, all, filters, pdf) }
            } catch (e: Exception) {
                error = e.message ?: "Export failed."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Export records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val r = result
                when {
                    busy -> {
                        Text(progress)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    r != null -> {
                        Text("Ready: ${r.file.name}", fontWeight = FontWeight.SemiBold)
                        Text("Save it to your phone, or share it (WhatsApp, email, Drive…).")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { saver.launch(r.file.name) }) { Text("Save") }
                            OutlinedButton(onClick = { Exporter.share(ctx, r.file, r.mime) }) { Text("Share") }
                            OutlinedButton(onClick = {
                                try { Exporter.open(ctx, r.file, r.mime) } catch (e: Exception) { app.toast("No app found to open this file.") }
                            }) { Text("Open") }
                        }
                    }
                    else -> {
                        Text(if (total >= 0) "${fmt(total)} records · ${filters.describe()}" else filters.describe())
                        if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        if (total > 10000) Text("This is a large list. The PDF may take a minute.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { run(true) }) { Text("PDF") }
                            OutlinedButton(onClick = { run(false) }) { Text("Excel (.xlsx)") }
                        }
                    }
                }
            }
        },
        confirmButton = { if (!busy) TextButton(onClick = onClose) { Text("Close") } },
    )
}

private fun makeFile(ctx: Context, rows: List<Person>, filters: Filters, pdf: Boolean): ExportResult {
    val dir = Exporter.exportDir(ctx)
    return if (pdf) {
        val f = File(dir, Exporter.fileName("pdf"))
        Exporter.writePdf(ctx, f, rows, filters.describe())
        ExportResult(f, Exporter.PDF)
    } else {
        val f = File(dir, Exporter.fileName("xlsx"))
        Exporter.writeXlsx(f, rows)
        ExportResult(f, Exporter.XLSX)
    }
}
