package com.informationplant.fdmn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(app: AppCtl) {
    val user = app.user ?: return
    var confirmOut by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Scaffold(topBar = { BrandTopBar("More") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(user.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Tag(if (user.isAdmin) "Admin" else "Viewer", if (user.isAdmin) Accent else Brand)
                    }
                }
            }
            MenuGroup {
                if (user.isAdmin) {
                    MenuItem(Icons.Filled.AccountCircle, "Manage users", badge = app.stats.pendingUsers) { app.push(Screen.Users) }
                    MenuItem(Icons.Filled.AddCircle, "Import from Excel") { app.push(Screen.Import) }
                }
                MenuItem(Icons.Filled.Lock, "Change password") { app.push(Screen.ChangePassword) }
                MenuItem(Icons.Filled.Refresh, "Refresh data") { app.changed(); app.refreshStats(); app.toast("Refreshed.") }
                MenuItem(Icons.Filled.Info, "About this app") { about = true }
            }
            MenuGroup {
                MenuItem(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", color = MaterialTheme.colorScheme.error) { confirmOut = true }
            }
        }
    }
    if (confirmOut) {
        ConfirmDialog("Sign out?", "You will need your email and password to sign in again.", confirm = "Sign out", onDismiss = { confirmOut = false }) {
            confirmOut = false
            app.signOut()
        }
    }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("8 West FDMN Database") },
            text = {
                Text(
                    "Version ${BuildConfigInfo.version}\n\nBeneficiary records for Camp-8W, organised by Main Block and Sub-Block.\n\n" +
                        "Data is stored securely on app.informationplant.com and is only visible to approved users.",
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
        )
    }
}

object BuildConfigInfo {
    const val version = "1.0"
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column { content() }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, badge: Int = 0, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (color == Color.Unspecified) Brand else color)
        Spacer(Modifier.width(16.dp))
        Text(text, Modifier.weight(1f), color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color, fontWeight = FontWeight.Medium)
        if (badge > 0) {
            Tag("$badge new", Color(0xFFD32F2F))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================= Users (admin) =======================

@Composable
fun UsersScreen(app: AppCtl) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<User>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("pending") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var resetFor by remember { mutableStateOf<User?>(null) }
    val me = app.user

    LaunchedEffect(reload) {
        error = null
        try {
            val l = Api.call("users").optJSONArray("users").mapObjects { User.from(it) }
            list = l
            if (reload == 0 && l.none { it.status == "pending" }) filter = "active"
            app.refreshStats()
        } catch (e: Exception) { error = e.message }
    }

    fun act(body: org.json.JSONObject, done: String) {
        scope.launch {
            try { Api.call("user_set", body); app.toast(done); reload++ } catch (e: Exception) { app.toast(e.message ?: "Error") }
        }
    }

    fun del(u: User) {
        scope.launch {
            try { Api.call("user_delete", jo("id" to u.id)); app.toast("User deleted."); reload++ } catch (e: Exception) { app.toast(e.message ?: "Error") }
        }
    }

    Scaffold(
        topBar = { BrandTopBar("Manage users", list?.let { "${it.size} users" }, onBack = { app.back() }) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val all = list.orEmpty()
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pending" to "Waiting", "active" to "Active", "blocked" to "Blocked", "all" to "All").forEach { (k, label) ->
                    val n = if (k == "all") all.size else all.count { it.status == k }
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text("$label ($n)") })
                }
            }
            val l = list
            when {
                l == null && error != null -> ErrorBox(error ?: "") { reload++ }
                l == null -> Loading()
                else -> {
                    val shown = if (filter == "all") l else l.filter { it.status == filter }
                    if (shown.isEmpty()) {
                        EmptyBox("Nobody here", if (filter == "pending") "No new sign-ups are waiting for approval." else "No users in this list.")
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(shown, key = { it.id }) { u ->
                            UserCard(
                                u, isMe = u.id == me?.id,
                                onApprove = { act(jo("id" to u.id, "status" to "active"), "${u.name} approved.") },
                                onBlock = { confirm = "Block ${u.name}? They will not be able to sign in." to { act(jo("id" to u.id, "status" to "blocked"), "${u.name} blocked.") } },
                                onUnblock = { act(jo("id" to u.id, "status" to "active"), "${u.name} unblocked.") },
                                onMakeAdmin = { confirm = "Make ${u.name} an Admin? Admins can add, edit and delete all records and manage users." to { act(jo("id" to u.id, "role" to "admin"), "${u.name} is now an Admin.") } },
                                onMakeViewer = { confirm = "Change ${u.name} to Viewer? They will only be able to view records." to { act(jo("id" to u.id, "role" to "viewer"), "${u.name} is now a Viewer.") } },
                                onReset = { resetFor = u },
                                onDelete = { confirm = "Delete ${u.name}'s account permanently?" to { del(u) } },
                            )
                        }
                    }
                }
            }
        }
    }

    val c = confirm
    if (c != null) {
        ConfirmDialog("Please confirm", c.first, onDismiss = { confirm = null }) { confirm = null; c.second() }
    }
    val r = resetFor
    if (r != null) {
        var pass by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resetFor = null },
            title = { Text("Reset password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a new password for ${r.name}. Tell them the new password so they can sign in.")
                    OutlinedTextField(pass, { pass = it }, label = { Text("New password (min 6)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pass.length < 6) { app.toast("Password must be at least 6 characters."); return@TextButton }
                    resetFor = null
                    act(jo("id" to r.id, "password" to pass), "Password changed for ${r.name}.")
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { resetFor = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UserCard(
    u: User, isMe: Boolean,
    onApprove: () -> Unit, onBlock: () -> Unit, onUnblock: () -> Unit,
    onMakeAdmin: () -> Unit, onMakeViewer: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name + if (isMe) " (You)" else "", fontWeight = FontWeight.SemiBold)
                    Text(u.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Tag(if (u.isAdmin) "Admin" else "Viewer", if (u.isAdmin) Accent else Brand)
                        when (u.status) {
                            "pending" -> Tag("Waiting", Color(0xFFD32F2F))
                            "blocked" -> Tag("Blocked", Color.Gray)
                            else -> Tag("Active", Color(0xFF2E7D32))
                        }
                    }
                    Text(
                        "Joined ${u.createdAt.take(10)}" + (if (u.lastLogin.isNotBlank()) " · Last sign-in ${u.lastLogin.take(16)}" else ""),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (!isMe) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (u.status == "blocked") DropdownMenuItem(text = { Text("Unblock") }, onClick = { menu = false; onUnblock() })
                            else if (u.status == "active") DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; onBlock() })
                            if (u.isAdmin) DropdownMenuItem(text = { Text("Make Viewer") }, onClick = { menu = false; onMakeViewer() })
                            else DropdownMenuItem(text = { Text("Make Admin") }, onClick = { menu = false; onMakeAdmin() })
                            DropdownMenuItem(text = { Text("Reset password") }, onClick = { menu = false; onReset() })
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete user", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            if (u.status == "pending") {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onApprove) { Text("Approve") }
                    TextButton(onClick = onDelete) { Text("Reject", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ======================= Change password =======================

@Composable
fun ChangePasswordScreen(app: AppCtl) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { BrandTopBar("Change password", onBack = { app.back() }) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(old, { old = it }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(new1, { new1 = it }, label = { Text("New password (min 6)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(new2, { new2 = it }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            Button(
                enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp),
                onClick = {
                    error = when {
                        new1.length < 6 -> "New password must be at least 6 characters."
                        new1 != new2 -> "The two new passwords do not match."
                        else -> null
                    }
                    if (error == null) {
                        busy = true
                        scope.launch {
                            try {
                                Api.call("change_password", jo("old" to old, "new" to new1))
                                app.toast("Password changed.")
                                app.back()
                            } catch (e: Exception) { error = e.message } finally { busy = false }
                        }
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text("Change password")
            }
        }
    }
}
