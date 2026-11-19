package com.informationplant.fdmn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ======================= choose what to export (Main Block, Sub-Block, Age group, Family) =======================

@Composable
fun ExportChooser(app: AppCtl, onClose: () -> Unit) {
    var main by remember { mutableStateOf("") }
    var sub by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var fcn by remember { mutableStateOf("") }
    var picker by remember { mutableStateOf<String?>(null) }
    var go by remember { mutableStateOf<Filters?>(null) }
    val meta = app.meta

    val g = go
    if (g != null) {
        ExportDialog(app, g, -1, onClose)
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Export records") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose which records to export.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChoiceField("Main Block", main.ifBlank { "All Main Blocks" }) { picker = "main" }
                ChoiceField("Sub-Block", sub.ifBlank { "All Sub-Blocks" }) { picker = "sub" }
                ChoiceField("Age group", if (age.isBlank()) "All ages" else ageLabel(age)) { picker = "age" }
                OutlinedTextField(
                    fcn, { fcn = it.trim() }, label = { Text("Family (FCN) — optional") }, singleLine = true,
                    placeholder = { Text("e.g. 117969") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { go = Filters(main = main, sub = sub, age = age, fcn = fcn) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
    when (picker) {
        "main" -> PickerDialog("Main Block", meta.mainNames, main, "All Main Blocks", { picker = null }) { v ->
            main = v; if (sub.isNotBlank() && sub !in meta.subNames(v)) sub = ""; picker = null
        }
        "sub" -> PickerDialog("Sub-Block", meta.subNames(main), sub, "All Sub-Blocks", { picker = null }) { v -> sub = v; picker = null }
        "age" -> PickerDialog("Age group", AGE_GROUPS.map { it.second }, ageLabel(age), "All ages", { picker = null }) { v ->
            age = AGE_GROUPS.firstOrNull { it.second == v }?.first ?: ""; picker = null
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Icon(Icons.Filled.ArrowDropDown, null)
    }
}

/** Save / Share / Open a finished file. */
@Composable
fun FileReadyDialog(app: AppCtl, result: ExportResult, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) {
            try { Exporter.copyTo(ctx, result.file, uri); app.toast("Saved ${result.file.name}") } catch (e: Exception) { app.toast("Could not save: ${e.message}") }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("File ready") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(result.file.name, fontWeight = FontWeight.SemiBold)
                Text("Save it to your phone, or share it (WhatsApp, email, Drive…).")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { saver.launch(result.file.name) }) { Text("Save") }
                    OutlinedButton(onClick = { Exporter.share(ctx, result.file, result.mime) }) { Text("Share") }
                    OutlinedButton(onClick = {
                        try { Exporter.open(ctx, result.file, result.mime) } catch (e: Exception) { app.toast("No app found to open this file.") }
                    }) { Text("Open") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

// ======================= Reports (block-wise summary) =======================

private class RepRow(val a: String, val b: String, val v: Map<String, Int>, val style: String)

private val REP_COLS = listOf("families" to "Families", "male" to "Male", "female" to "Female", "total" to "Total", "u5" to "0–5", "c6" to "6–12", "c13" to "13–17", "a18" to "18–59", "e60" to "60+")

private fun buildLines(r: JSONObject): List<RepRow> {
    val rows = r.optJSONArray("rows").mapObjects { o -> o }
    val famBy = r.optJSONObject("families_by_main") ?: JSONObject()
    val keys = REP_COLS.map { it.first }
    val out = mutableListOf<RepRow>()
    val grand = mutableMapOf<String, Int>()
    var cur: String? = null
    var acc = mutableMapOf<String, Int>()
    fun flush() {
        val c = cur ?: return
        acc["families"] = famBy.optInt(c)
        out += RepRow("Main Block ${c.ifBlank { "(none)" }}", "Total", acc.toMap(), "sub")
    }
    rows.forEach { o ->
        val main = o.str("main")
        if (main != cur) { flush(); cur = main; acc = mutableMapOf() }
        val v = keys.associateWith { o.optInt(it) }
        out += RepRow(main, o.str("sub").ifBlank { "(none)" }, v, "")
        keys.filter { it != "families" }.forEach { k -> acc[k] = (acc[k] ?: 0) + (v[k] ?: 0); grand[k] = (grand[k] ?: 0) + (v[k] ?: 0) }
    }
    flush()
    if (rows.isNotEmpty()) { grand["families"] = r.optInt("families_total"); out += RepRow("Grand Total", "", grand, "grand") }
    return out
}

@Composable
fun ReportsScreen(app: AppCtl) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var main by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<RepRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExportResult?>(null) }

    LaunchedEffect(main, reload, app.version) {
        error = null; lines = null
        try { lines = buildLines(Api.call("report", if (main.isBlank()) JSONObject() else jo("main" to main))) } catch (e: Exception) { error = e.message }
    }

    fun make(pdf: Boolean) {
        val l = lines ?: return
        if (l.isEmpty()) { app.toast("Nothing to export."); return }
        busy = true
        scope.launch {
            try {
                result = withContext(Dispatchers.Default) {
                    val dir = Exporter.exportDir(ctx)
                    val headers = listOf("Main Block", "Sub-Block") + REP_COLS.map { it.second }
                    val subtitle = if (main.isBlank()) "All Main Blocks" else "Main Block $main"
                    if (pdf) {
                        val f = File(dir, "FDMN_8W_Report_" + Exporter.fileName("pdf").removePrefix("FDMN_8W_"))
                        Exporter.writeTablePdf(
                            ctx, f, "Camp- 8 West · Block-wise population summary", subtitle, headers,
                            listOf(110f, 70f) + REP_COLS.map { 52f }, listOf(false, false) + REP_COLS.map { true },
                            l.map { r -> listOf(r.a, r.b) + REP_COLS.map { fmt(r.v[it.first] ?: 0) } }, l.map { it.style },
                        )
                        ExportResult(f, Exporter.PDF)
                    } else {
                        val f = File(dir, "FDMN_8W_Report_" + Exporter.fileName("xlsx").removePrefix("FDMN_8W_"))
                        Exporter.writeTableXlsx(f, headers, l.map { r -> listOf<Any>(r.a, r.b) + REP_COLS.map { r.v[it.first] ?: 0 } }, listOf(18, 12) + REP_COLS.map { 10 })
                        ExportResult(f, Exporter.XLSX)
                    }
                }
            } catch (e: Exception) { app.toast(e.message ?: "Could not create the file.") } finally { busy = false }
        }
    }

    Scaffold(
        topBar = { BrandTopBar("Reports", "Block-wise population summary") },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker = true }, modifier = Modifier.weight(1f)) {
                    Text(if (main.isBlank()) "All Main Blocks" else "Main Block $main", maxLines = 1)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                Button(onClick = { make(true) }, enabled = !busy) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(4.dp)); Text("PDF") }
                Button(onClick = { make(false) }, enabled = !busy) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(4.dp)); Text("Excel") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            val l = lines
            when {
                l == null && error != null -> ErrorBox(error ?: "") { reload++ }
                l == null -> Loading()
                l.isEmpty() -> EmptyBox("No data", "There are no records yet.")
                else -> ReportTable(l, Modifier.weight(1f))
            }
        }
    }
    if (picker) PickerDialog("Main Block", app.meta.mainNames, main, "All Main Blocks", { picker = false }) { v -> main = v; picker = false }
    val r = result
    if (r != null) FileReadyDialog(app, r) { result = null }
}

@Composable
private fun ReportTable(lines: List<RepRow>, modifier: Modifier) {
    val h = rememberScrollState()
    val widths = listOf(110, 80) + REP_COLS.map { 64 }
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(h)) {
            Row(Modifier.background(Brand)) {
                (listOf("Main Block", "Sub-Block") + REP_COLS.map { it.second }).forEachIndexed { i, t ->
                    Text(t, Modifier.width(widths[i].dp).padding(8.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = if (i > 1) TextAlign.End else TextAlign.Start)
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                lines.forEach { r ->
                    val bg = when (r.style) { "sub" -> Color(0xFFE3F2EC); "grand" -> Brand; else -> Color.White }
                    val fg = if (r.style == "grand") Color.White else Color(0xFF17201D)
                    val fw = if (r.style.isNotBlank()) FontWeight.ExtraBold else FontWeight.Normal
                    Row(Modifier.background(bg)) {
                        (listOf(r.a, r.b) + REP_COLS.map { fmt(r.v[it.first] ?: 0) }).forEachIndexed { i, t ->
                            Text(t, Modifier.width(widths[i].dp).padding(horizontal = 8.dp, vertical = 9.dp), color = fg, fontWeight = fw, fontSize = 14.sp, maxLines = 1, textAlign = if (i > 1) TextAlign.End else TextAlign.Start)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
