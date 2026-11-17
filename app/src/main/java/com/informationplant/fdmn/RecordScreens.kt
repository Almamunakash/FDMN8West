package com.informationplant.fdmn

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

// ======================= Detail =======================

@Composable
fun DetailScreen(app: AppCtl, id: Int) {
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf<Person?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf(false) }

    LaunchedEffect(id, reload) {
        error = null
        try { person = Person.from(Api.call("get", jo("id" to id)).getJSONObject("row")) } catch (e: Exception) { error = e.message }
    }

    Scaffold(
        topBar = {
            BrandTopBar(person?.name ?: "Record", person?.let { "FCN ${it.fcn}" }, onBack = { app.back() }) {
                val p = person
                if (app.isAdmin && p != null) {
                    IconButton(onClick = { app.push(Screen.Edit(p)) }) { Icon(Icons.Filled.Edit, "Edit") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val p = person
        when {
            p == null && error != null -> ErrorBox(error ?: "") { reload++ }
            p == null -> Loading(Modifier.padding(pad))
            else -> Column(
                Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WaveCard(if (p.sex == "F") TonePink else if (p.sex == "M") ToneBlue else ToneGreen, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Filled.Person, if (p.sex == "F") TonePink else if (p.sex == "M") ToneBlue else ToneGreen)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    if (p.sex.isNotBlank()) Tag(p.sexText, if (p.sex == "F") Female else Male)
                                    if (p.ageText.isNotBlank()) Tag(p.ageText, Brand)
                                    if (p.relation.isNotBlank()) Tag(p.relation, Accent)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        InfoRow("Name", p.name)
                        InfoRow("Sex", p.sexText)
                        InfoRow("FCN", p.fcn)
                        InfoRow("Date of birth", p.dobText)
                        InfoRow("Age", p.ageText)
                        InfoRow("Relation", p.relation)
                        InfoRow("Main Block", p.mainBlock)
                        InfoRow("Sub-Block", p.subBlock)
                        InfoRow("Majhi", p.majhi)
                        InfoRow("Remarks", p.remarks)
                        if (p.updatedAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Last updated ${p.updatedAt}" + (if (p.updatedBy.isNotBlank()) " by ${p.updatedBy}" else ""),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (p.fcn.isNotBlank()) {
                    Button(onClick = { app.push(Screen.Family(p.fcn)) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text("View whole family (FCN ${p.fcn})")
                    }
                }
                if (!app.isAdmin) {
                    OutlinedButton(onClick = { request = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Icon(Icons.Filled.Email, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Request a correction")
                    }
                }
            }
        }
    }

    val p = person
    if (confirmDelete && p != null) {
        ConfirmDialog(
            "Delete record?", "${p.name} (FCN ${p.fcn}) will be permanently deleted. This cannot be undone.",
            confirm = "Delete", danger = true, onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            scope.launch {
                try {
                    Api.call("delete", jo("id" to p.id))
                    app.toast("Record deleted.")
                    app.changed()
                    app.back()
                } catch (e: Exception) { app.toast(e.message ?: "Could not delete.") }
            }
        }
    }
    if (request && p != null) {
        ComposeMessageDialog(app, "Correction: ${p.name} (FCN ${p.fcn})", p.id) { request = false }
    }
}

// ======================= Family =======================

@Composable
fun FamilyScreen(app: AppCtl, fcn: String) {
    var rows by remember { mutableStateOf<List<Person>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(fcn, reload, app.version) {
        error = null
        try { rows = Api.call("family", jo("fcn" to fcn)).optJSONArray("rows").mapObjects { Person.from(it) } } catch (e: Exception) { error = e.message }
    }
    Scaffold(
        topBar = { BrandTopBar("Family · FCN $fcn", rows?.let { "${it.size} members" }, onBack = { app.back() }) },
        floatingActionButton = {
            val first = rows?.firstOrNull()
            if (app.isAdmin && first != null) {
                ExtendedFloatingActionButton(
                    onClick = { app.push(Screen.Edit(Person(fcn = fcn, mainBlock = first.mainBlock, subBlock = first.subBlock, majhi = first.majhi))) },
                    icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add member") },
                    containerColor = Brand, contentColor = Color.White,
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        val list = rows
        when {
            list == null && error != null -> ErrorBox(error ?: "") { reload++ }
            list == null -> Loading(Modifier.padding(pad))
            list.isEmpty() -> EmptyBox("No members", "No records found with FCN $fcn.")
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    val head = list.first()
                    val m = list.count { it.sex == "M" }
                    val f = list.count { it.sex == "F" }
                    val children = list.count { (DateUtil.parseIso(it.dob)?.let { d -> java.time.Period.between(d, LocalDate.now()).years } ?: 99) < 18 }
                    WaveCard(ToneOrange, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconTile(Icons.Filled.FamilyRestroom, ToneOrange)
                                Spacer(Modifier.width(12.dp))
                                Text("FCN $fcn", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = ToneOrange.dark)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOf(head.blockText, head.majhi.let { if (it.isBlank()) "" else "Majhi $it" }).filter { it.isNotBlank() }.joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Tag("${list.size} members", Brand)
                                Tag("$m male", Male)
                                Tag("$f female", Female)
                                if (children > 0) Tag("$children under 18", Accent)
                            }
                        }
                    }
                }
                itemsIndexed(list, key = { _, p -> p.id }) { i, p -> PersonCard(i + 1, p) { app.push(Screen.Detail(p.id)) } }
            }
        }
    }
}

// ======================= Add / Edit =======================

@Composable
fun EditScreen(app: AppCtl, start: Person) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isNew = start.id == 0
    var name by remember { mutableStateOf(start.name) }
    var sex by remember { mutableStateOf(start.sex) }
    var fcn by remember { mutableStateOf(start.fcn) }
    var dob by remember { mutableStateOf(start.dobText) }
    var relation by remember { mutableStateOf(start.relation) }
    var main by remember { mutableStateOf(start.mainBlock) }
    var sub by remember { mutableStateOf(start.subBlock) }
    var majhi by remember { mutableStateOf(start.majhi) }
    var remarks by remember { mutableStateOf(start.remarks) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val meta = app.meta

    fun save() {
        val iso = if (dob.isBlank()) "" else DateUtil.parseFlexible(dob)
        error = when {
            name.isBlank() -> "Name is required."
            iso == null -> "Date of birth is not clear. Use a format like 1 Jan 1992 or 01/01/1992."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            try {
                val p = start.copy(
                    name = name.trim(), sex = sex, fcn = fcn.trim(), dob = iso ?: "", relation = relation.trim().uppercase(),
                    mainBlock = main.trim(), subBlock = sub.trim(), majhi = majhi.trim(), remarks = remarks.trim(),
                )
                Api.call("save", p.toJson())
                app.toast(if (isNew) "Record added." else "Changes saved.")
                app.changed()
                app.back()
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = { BrandTopBar(if (isNew) "Add record" else "Edit record", if (isNew) null else start.name, onBack = { app.back() }) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Sex", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentChoice(listOf("M" to "Male", "F" to "Female"), sex) { sex = if (sex == it) "" else it }
            OutlinedTextField(fcn, { fcn = it }, label = { Text("FCN") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                dob, { dob = it }, label = { Text("Date of birth") }, singleLine = true,
                placeholder = { Text("e.g. 1 Jan 1992") },
                supportingText = { DateUtil.parseFlexible(dob)?.let { Text("= " + DateUtil.display(it) + " (" + DateUtil.age(it) + ")") } },
                trailingIcon = {
                    IconButton(onClick = {
                        val d = DateUtil.parseFlexible(dob)?.let { LocalDate.parse(it) } ?: LocalDate.of(1990, 1, 1)
                        DatePickerDialog(ctx, { _, y, m, day -> dob = DateUtil.display(LocalDate.of(y, m + 1, day).toString()) },
                            d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }) { Icon(Icons.Filled.DateRange, "Pick date") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ComboField("Relation", relation, meta.relations, { relation = it }, Modifier.fillMaxWidth(), upperCase = true)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ComboField("Main Block", main, meta.mainNames, { main = it }, Modifier.weight(1f))
                ComboField("Sub-Block", sub, meta.subNames(main), { sub = it }, Modifier.weight(1f))
            }
            ComboField("Majhi", majhi, meta.majhis, { majhi = it }, Modifier.fillMaxWidth())
            OutlinedTextField(remarks, { remarks = it }, label = { Text("Remarks") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = { save() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
            ) {
                if (busy) CircularProgressIndicator(Modifier.width(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isNew) "Add record" else "Save changes", fontSize = 16.sp)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
