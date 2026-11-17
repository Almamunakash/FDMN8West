package com.informationplant.fdmn

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MessagesScreen(app: AppCtl) {
    val scope = rememberCoroutineScope()
    val admin = app.isAdmin
    var showOpen by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf<List<Message>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var compose by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(reload, showOpen) {
        error = null
        try {
            val r = if (admin) Api.call("msg_all", jo("status" to if (showOpen) "open" else "all")) else Api.call("msg_mine")
            list = r.optJSONArray("messages").mapObjects { Message.from(it) }
            app.refreshStats()
        } catch (e: Exception) { error = e.message }
    }

    Scaffold(
        topBar = {
            BrandTopBar("Messages", if (admin) "Requests from users" else "Your requests to the Admin") {
                IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Refresh") }
            }
        },
        floatingActionButton = {
            if (!admin) {
                ExtendedFloatingActionButton(
                    onClick = { compose = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New request") },
                    containerColor = Brand, contentColor = Color.White,
                )
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (admin) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = showOpen, onClick = { showOpen = true }, label = { Text("Open") })
                    FilterChip(selected = !showOpen, onClick = { showOpen = false }, label = { Text("All") })
                }
            }
            val l = list
            when {
                l == null && error != null -> ErrorBox(error ?: "") { reload++ }
                l == null -> Loading()
                l.isEmpty() -> EmptyBox(
                    "No messages",
                    if (admin) (if (showOpen) "There are no open requests." else "No requests yet.")
                    else "Tap \"New request\" to ask the Admin to add, correct or remove a record.",
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(l, key = { it.id }) { m ->
                        MessageCard(m, admin,
                            onOpenRecord = if (m.beneficiaryId > 0) ({ app.push(Screen.Detail(m.beneficiaryId)) }) else null,
                            onReply = { replyTo = m },
                            onClose = {
                                scope.launch {
                                    try { Api.call("msg_close", jo("id" to m.id)); reload++ } catch (e: Exception) { app.toast(e.message ?: "Error") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (compose) ComposeMessageDialog(app, "", 0) { sent -> compose = false; if (sent) reload++ }
    val r = replyTo
    if (r != null) ReplyDialog(app, r) { sent -> replyTo = null; if (sent) reload++ }
}

@Composable
private fun MessageCard(m: Message, admin: Boolean, onOpenRecord: (() -> Unit)?, onReply: () -> Unit, onClose: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row {
                Text(m.subject.ifBlank { "Request" }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                when (m.status) {
                    "open" -> Tag("Open", Accent)
                    "replied" -> Tag("Replied", Brand)
                    else -> Tag("Closed", Color.Gray)
                }
            }
            Text(
                (if (admin) "${m.userName} · " else "") + m.createdAt,
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(m.body)
            if (m.beneficiary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Record: ${m.beneficiary}", fontSize = 13.sp, color = Brand, fontWeight = FontWeight.Medium)
            }
            if (m.reply.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Reply from ${m.repliedBy.ifBlank { "Admin" }} · ${m.repliedAt}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(m.reply, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (admin || onOpenRecord != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onOpenRecord != null) OutlinedButton(onClick = onOpenRecord) { Text("Open record") }
                    if (admin) {
                        OutlinedButton(onClick = onReply) { Text(if (m.reply.isBlank()) "Reply" else "Edit reply") }
                        if (m.status != "closed") TextButton(onClick = onClose) { Text("Close") }
                    }
                }
            }
        }
    }
}

@Composable
fun ComposeMessageDialog(app: AppCtl, subject0: String, beneficiaryId: Int, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var subject by remember { mutableStateOf(subject0) }
    var body by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        title = { Text("Message to Admin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    body, { body = it }, label = { Text("Message") }, minLines = 4,
                    placeholder = { Text("What should the Admin add, correct or remove?") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (body.isBlank()) { error = "Please write your message."; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        val r = Api.call("msg_send", jo("subject" to subject.trim(), "body" to body.trim(), "beneficiary_id" to beneficiaryId))
                        app.toast(r.optString("message", "Sent."))
                        onDone(true)
                    } catch (e: Exception) { error = e.message } finally { busy = false }
                }
            }) { Text(if (busy) "Sending…" else "Send", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun ReplyDialog(app: AppCtl, m: Message, onDone: (Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var reply by remember { mutableStateOf(m.reply) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDone(false) },
        title = { Text("Reply to ${m.userName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.body, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(reply, { reply = it }, label = { Text("Your reply") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (reply.isBlank()) { error = "Please write a reply."; return@TextButton }
                busy = true
                scope.launch {
                    try {
                        Api.call("msg_reply", jo("id" to m.id, "reply" to reply.trim()))
                        app.toast("Reply sent.")
                        onDone(true)
                    } catch (e: Exception) { error = e.message } finally { busy = false }
                }
            }) { Text(if (busy) "Sending…" else "Send reply", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onDone(false) }, enabled = !busy) { Text("Cancel") } },
    )
}
