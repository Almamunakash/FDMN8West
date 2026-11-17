package com.informationplant.fdmn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun ImportScreen(app: AppCtl) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reading by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<ParseResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var replace by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var sent by remember { mutableIntStateOf(0) }
    var done by remember { mutableStateOf(false) }
    var confirmReplace by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            reading = true; error = null; parsed = null; sent = 0; done = false
            scope.launch {
                try {
                    parsed = withContext(Dispatchers.IO) { ExcelReader.read(ctx, uri) }
                    replace = app.stats.total > 0
                } catch (e: Exception) {
                    error = e.message ?: "Could not read this file."
                } finally {
                    reading = false
                }
            }
        }
    }

    fun upload() {
        val p = parsed ?: return
        uploading = true; error = null
        scope.launch {
            val batch = 1000
            try {
                while (sent < p.rows.size) {
                    val part = p.rows.subList(sent, minOf(sent + batch, p.rows.size))
                    val arr = JSONArray()
                    part.forEach { arr.put(it.toJson()) }
                    Api.call("import", jo("rows" to arr, "replace" to (replace && sent == 0)))
                    sent += part.size
                }
                done = true
                app.changed()
                app.refreshStats()
            } catch (e: Exception) {
                error = (e.message ?: "Upload failed.") + "\n${fmt(sent)} of ${fmt(p.rows.size)} rows were uploaded. Tap Continue to upload the rest."
            } finally {
                uploading = false
            }
        }
    }

    Scaffold(
        topBar = { BrandTopBar("Import from Excel", onBack = { if (!uploading) app.back() }) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Text("How it works", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. Choose your Excel file (.xlsx) or a .csv file.\n" +
                        "2. The app finds the heading row (SL, Name, Sex, FCN, Date of birth, Relation, Main Block, Sub-Block, Majhi, Remarks). " +
                        "All sheets with this heading row are read.\n" +
                        "3. Check the summary, then tap Upload.\n\n" +
                        "Main Block and Sub-Block lists are created automatically. SL numbers are given automatically.",
                    fontSize = 14.sp,
                )
            }
            OutlinedButton(
                onClick = {
                    picker.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel", "text/csv", "text/comma-separated-values",
                        "application/octet-stream", "*/*",
                    ))
                },
                enabled = !reading && !uploading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (parsed == null) "Choose Excel file" else "Choose a different file") }

            if (reading) {
                Text("Reading the file…")
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            val p = parsed
            if (p != null) {
                Card {
                    Text("File summary", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    SumRow("Rows found", fmt(p.rows.size))
                    SumRow("Families (FCN)", fmt(p.rows.map { it.fcn }.filter { it.isNotBlank() }.distinct().size))
                    SumRow("Sheets read", p.sheetsUsed.joinToString(", "))
                    if (p.sheetsSkipped.isNotEmpty()) SumRow("Sheets skipped", p.sheetsSkipped.joinToString(", "))
                    SumRow("Columns matched", p.columnsFound.joinToString(", "))
                    if (p.columnsMissing.isNotEmpty()) SumRow("Columns not found", p.columnsMissing.joinToString(", "), warn = true)
                    if (p.emptyRowsSkipped > 0) SumRow("Empty rows skipped", fmt(p.emptyRowsSkipped))
                    if (p.badDates > 0) SumRow("Unclear dates", "${fmt(p.badDates)} (left empty; the original text was put in Remarks)", warn = true)
                }
                if (p.rows.isNotEmpty()) {
                    Card {
                        Text("First rows (preview)", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        p.rows.take(6).forEachIndexed { i, r ->
                            Text(
                                "${i + 1}. ${r.name} · ${r.sex} · FCN ${r.fcn} · ${DateUtil.display(r.dob).ifBlank { "no DOB" }} · ${r.relation} · ${r.mainBlock}/${r.subBlock} · ${r.majhi}",
                                fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !uploading && sent == 0) { replace = !replace },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = replace, onCheckedChange = { if (!uploading && sent == 0) replace = it })
                        Column {
                            Text("Delete all existing records first", fontWeight = FontWeight.Medium)
                            Text(
                                if (app.stats.total > 0) "There are ${fmt(app.stats.total)} records now. Tick this to replace them, or untick to add these rows to them."
                                else "The database is empty.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (uploading || sent > 0) {
                        Text("Uploaded ${fmt(sent)} of ${fmt(p.rows.size)}")
                        LinearProgressIndicator(
                            progress = { if (p.rows.isEmpty()) 0f else sent.toFloat() / p.rows.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (done) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                            Text("Import finished: ${fmt(sent)} records uploaded.", Modifier.padding(12.dp).fillMaxWidth(), fontWeight = FontWeight.SemiBold)
                        }
                        Button(onClick = { app.openRecords(Filters()) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("View records") }
                    } else {
                        Button(
                            onClick = { if (replace && sent == 0 && app.stats.total > 0) confirmReplace = true else upload() },
                            enabled = !uploading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) { Text(if (uploading) "Uploading…" else if (sent > 0) "Continue upload" else "Upload ${fmt(p.rows.size)} records") }
                    }
                }
            }
            if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmReplace) {
        ConfirmDialog(
            "Replace all records?",
            "All ${fmt(app.stats.total)} existing records will be deleted and replaced with the ${fmt(parsed?.rows?.size ?: 0)} rows from this file. This cannot be undone.",
            confirm = "Replace", danger = true, onDismiss = { confirmReplace = false },
        ) { confirmReplace = false; upload() }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun SumRow(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.width(130.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (warn) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurface)
    }
}
