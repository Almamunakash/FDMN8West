package com.informationplant.fdmn

import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    var profile by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Scaffold(topBar = { BrandTopBar("More") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WaveCard(ToneGreen, Modifier.fillMaxWidth(), onClick = { profile = true }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = ToneGreen.dark, modifier = Modifier.size(52.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(user.name.take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF14201C))
                        Text(user.email, fontSize = 13.sp, color = Color(0xFF3A4743))
                        Spacer(Modifier.height(4.dp))
                        Tag((if (user.isMaster) "👑 " else "") + user.roleName, if (user.isAdmin) Accent else Brand)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ToneGreen.dark)
                }
            }
            MenuGroup {
                MenuItem(Icons.Filled.AccountBox, "Camp Profile") { app.push(Screen.CampProfile) }
                if (user.isAdmin) MenuItem(Icons.Filled.Settings, "Site Update", badge = app.stats.pendingUsers) { app.push(Screen.Site) }
                MenuItem(Icons.Filled.Refresh, "Refresh data") { app.changed(); app.refreshStats(); app.refreshCamp(); app.toast("Refreshed.") }
                MenuItem(Icons.Filled.Info, "About this app") { about = true }
            }
            MenuGroup {
                MenuItem(Icons.Filled.Person, "My profile") { profile = true }
                MenuItem(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", color = MaterialTheme.colorScheme.error) { app.signOut() }
            }
        }
    }
    if (profile) ProfileDialog(app) { profile = false }
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("Camp- 8 West") },
            text = {
                Text(
                    "Information Management System · Version ${BuildConfigInfo.version}\n\nBeneficiary records for Camp-8W, organised by Main Block and Sub-Block.\n\n" +
                        "Data is stored securely on app.informationplant.com and is only visible to approved users. The web app uses the same data.",
                )
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("OK") } },
        )
    }
}

object BuildConfigInfo {
    const val version = "2.1"
}

/** Profile pop-up: Update Information, Reset Password, Delete Account, Sign out. */
@Composable
fun ProfileDialog(app: AppCtl, onClose: () -> Unit) {
    val user = app.user ?: return
    var step by remember { mutableStateOf("menu") }
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(when (step) { "info" -> "Update Information"; "delete" -> "Delete my account"; "out" -> "Sign out?"; else -> user.name }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    "menu" -> {
                        Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Tag((if (user.isMaster) "👑 " else "") + user.roleName, if (user.isAdmin) Accent else Brand)
                        Spacer(Modifier.height(6.dp))
                        MenuGroup {
                            MenuItem(Icons.Filled.Edit, "Update Information") { error = null; step = "info" }
                            MenuItem(Icons.Filled.Lock, "Reset Password") { onClose(); app.push(Screen.ChangePassword) }
                            if (!user.isMaster) MenuItem(Icons.Filled.Delete, "Delete Account", color = MaterialTheme.colorScheme.error) { error = null; step = "delete" }
                            MenuItem(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", color = MaterialTheme.colorScheme.error) { step = "out" }
                        }
                    }
                    "info" -> {
                        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    "delete" -> {
                        Text("Your account will be deleted permanently and you will be signed out. Records you added stay in the database.")
                        OutlinedTextField(
                            pw, { pw = it }, label = { Text("Enter your password to confirm") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    "out" -> Text("You will need your email and password to sign in again.")
                }
                if (error != null) Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when (step) {
                "info" -> TextButton(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val u = User.from(Api.call("me_update", jo("name" to name.trim(), "email" to email.trim())).getJSONObject("user"))
                            app.user = u; Prefs.userJson = u.toJson().toString(); Prefs.lastEmail = u.email
                            app.toast("Your information was updated."); onClose()
                        } catch (e: Exception) { error = e.message } finally { busy = false }
                    }
                }) { Text("Save", fontWeight = FontWeight.Bold) }
                "delete" -> TextButton(enabled = !busy, onClick = {
                    if (pw.isEmpty()) { error = "Please enter your password." } else {
                        busy = true; error = null
                        scope.launch {
                            try {
                                Api.call("delete_me", jo("password" to pw)); onClose(); app.signOut(callServer = false); app.toast("Your account was deleted.")
                            } catch (e: Exception) { error = e.message } finally { busy = false }
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                "out" -> TextButton(onClick = { onClose(); app.signOut() }) { Text("Sign out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                else -> TextButton(onClick = onClose) { Text("Close") }
            }
        },
        dismissButton = { if (step != "menu") TextButton(onClick = { step = "menu"; error = null }, enabled = !busy) { Text("Back") } },
    )
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column { content() }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, badge: Int = 0, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, if (color == Color.Unspecified) ToneGreen else ToneRed, 38.dp, 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(text, Modifier.weight(1f), color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color, fontWeight = FontWeight.Medium)
        if (badge > 0) {
            Tag("$badge new", Color(0xFFD32F2F))
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ======================= Users (admin) =======================

fun canManage(me: User?, u: User): Boolean {
    if (me == null || u.id == me.id || u.role == "master") return false
    if (me.isMaster) return true
    return me.role == "admin" && u.role == "viewer"
}

@Composable
fun UsersScreen(app: AppCtl) {
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<User>?>(null) }
    var hasMaster by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("all") }
    var search by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var resetFor by remember { mutableStateOf<User?>(null) }
    var infoFor by remember { mutableStateOf<User?>(null) }
    var adding by remember { mutableStateOf(false) }
    val me = app.user

    LaunchedEffect(reload) {
        error = null
        try {
            val r = Api.call("users")
            val l = r.optJSONArray("users").mapObjects { User.from(it) }
            hasMaster = r.optBoolean("has_master", true)
            list = l
            if (reload == 0 && l.any { it.status == "pending" }) filter = "pending"
            app.refreshStats()
        } catch (e: Exception) { error = e.message }
    }

    fun act(body: org.json.JSONObject, done: String) {
        scope.launch { try { Api.call("user_set", body); app.toast(done); reload++ } catch (e: Exception) { app.toast(e.message ?: "Error") } }
    }
    fun del(u: User) {
        scope.launch { try { Api.call("user_delete", jo("id" to u.id)); app.toast("Account deleted."); reload++ } catch (e: Exception) { app.toast(e.message ?: "Error") } }
    }

    Scaffold(
        topBar = { BrandTopBar("Manage Users", list?.let { "${it.size} users" }, onBack = { app.back() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add User") }, containerColor = Brand, contentColor = Color.White)
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            val all = list.orEmpty()
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pending" to "Waiting", "active" to "Active", "blocked" to "Blocked", "all" to "All").forEach { (k, label) ->
                    val n = if (k == "all") all.size else all.count { it.status == k }
                    FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text("$label ($n)") })
                }
            }
            SearchBox(search, { search = it }, "Search user by name or email…", Modifier.padding(horizontal = 12.dp))
            val l = list
            when {
                l == null && error != null -> ErrorBox(error ?: "") { reload++ }
                l == null -> Loading()
                else -> {
                    val q = search.trim().lowercase()
                    val shown = l.filter { (filter == "all" || it.status == filter) && (q.isEmpty() || it.name.lowercase().contains(q) || it.email.lowercase().contains(q)) }
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("master", "Master Admin", ToneIndigo), Triple("admin", "Admin", ToneBlue), Triple("viewer", "User", TonePurple)).forEach { (role, title, tone) ->
                            val us = shown.filter { it.role == role }
                            item(key = "h_$role") {
                                Surface(shape = RoundedCornerShape(12.dp), color = tone.dark, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                        Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                                        Text(us.size.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            if (us.isEmpty()) item(key = "e_$role") { Text("Nobody here.", Modifier.padding(6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(us, key = { it.id }) { u ->
                                UserCard(
                                    u, isMe = u.id == me?.id, manage = canManage(me, u), master = me?.isMaster == true,
                                    onApprove = { act(jo("id" to u.id, "status" to "active"), "${u.name} approved.") },
                                    onBlock = { confirm = "Block ${u.name}? They will not be able to sign in." to { act(jo("id" to u.id, "status" to "blocked"), "${u.name} blocked.") } },
                                    onUnblock = { act(jo("id" to u.id, "status" to "active"), "${u.name} unblocked.") },
                                    onMakeAdmin = { confirm = "Make ${u.name} an Admin? Admins can add, edit and delete records and manage Users." to { act(jo("id" to u.id, "role" to "admin"), "${u.name} is now an Admin.") } },
                                    onMakeViewer = { confirm = "Change ${u.name} to User (Viewer)?" to { act(jo("id" to u.id, "role" to "viewer"), "${u.name} is now a User.") } },
                                    onReset = { resetFor = u },
                                    onInfo = { infoFor = u },
                                    onDelete = { confirm = "Delete ${u.name}'s account permanently?" to { del(u) } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val c = confirm
    if (c != null) ConfirmDialog("Please confirm", c.first, onDismiss = { confirm = null }) { confirm = null; c.second() }
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
                    if (pass.length < 6) app.toast("Password must be at least 6 characters.")
                    else { resetFor = null; act(jo("id" to r.id, "password" to pass), "Password changed for ${r.name}.") }
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { resetFor = null }) { Text("Cancel") } },
        )
    }
    val inf = infoFor
    if (inf != null) {
        var n by remember(inf.id) { mutableStateOf(inf.name) }
        var e by remember(inf.id) { mutableStateOf(inf.email) }
        AlertDialog(
            onDismissRequest = { infoFor = null },
            title = { Text("Update Information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(n, { n = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(e, { e = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { infoFor = null; act(jo("id" to inf.id, "name" to n.trim(), "email" to e.trim()), "Saved.") }) { Text("Save", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { infoFor = null }) { Text("Cancel") } },
        )
    }
    if (adding) AddUserDialog(hasMaster, me?.isMaster == true, onClose = { adding = false }) { body ->
        scope.launch {
            try { Api.call("user_add", body); app.toast("User added."); adding = false; reload++ } catch (ex: Exception) { app.toast(ex.message ?: "Error") }
        }
    }
}

@Composable
private fun AddUserDialog(hasMaster: Boolean, isMaster: Boolean, onClose: () -> Unit, onSave: (org.json.JSONObject) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("viewer") }
    val roles = listOf("viewer" to "User (Viewer)") +
        (if (isMaster) listOf("admin" to "Admin") else emptyList()) +
        (if (!hasMaster) listOf("master" to "Master Admin") else emptyList())
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add User") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password (min 6)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Role", fontWeight = FontWeight.Bold)
                roles.forEach { (v, l) ->
                    Row(Modifier.fillMaxWidth().clickable { role = v }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = role == v, onClick = { role = v })
                        Text(l)
                    }
                }
                if (!hasMaster) Text("There is no Master Admin yet. You can create the one Master Admin account here.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(jo("name" to name.trim(), "email" to email.trim(), "password" to pass, "role" to role)) }) { Text("Add User", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun UserCard(
    u: User, isMe: Boolean, manage: Boolean, master: Boolean,
    onApprove: () -> Unit, onBlock: () -> Unit, onUnblock: () -> Unit, onMakeAdmin: () -> Unit, onMakeViewer: () -> Unit,
    onReset: () -> Unit, onInfo: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(u.name + if (isMe) " (You)" else "", fontWeight = FontWeight.SemiBold)
                    Text(u.email, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        when (u.status) {
                            "pending" -> Tag("Waiting", Color(0xFFD32F2F))
                            "blocked" -> Tag("Blocked", Color.Gray)
                            else -> Tag("Active", Color(0xFF2E7D32))
                        }
                    }
                    Text(
                        "Joined ${u.createdAt.take(10)}" + (if (u.lastLogin.isNotBlank()) " · Last login ${u.lastLogin.take(16)}" else ""),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (manage) {
                    Box {
                        Button(onClick = { menu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Update") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            val who = if (u.role == "admin") "Admin" else "User"
                            DropdownMenuItem(text = { Text("Update Information") }, onClick = { menu = false; onInfo() })
                            DropdownMenuItem(text = { Text("Reset Password") }, onClick = { menu = false; onReset() })
                            if (u.status == "pending") DropdownMenuItem(text = { Text("Approve") }, onClick = { menu = false; onApprove() })
                            if (u.status == "blocked") DropdownMenuItem(text = { Text("Unblock $who") }, onClick = { menu = false; onUnblock() })
                            else if (u.status == "active") DropdownMenuItem(text = { Text("Block $who") }, onClick = { menu = false; onBlock() })
                            if (master) {
                                if (u.role == "admin") DropdownMenuItem(text = { Text("Change to User") }, onClick = { menu = false; onMakeViewer() })
                                else DropdownMenuItem(text = { Text("Make Admin") }, onClick = { menu = false; onMakeAdmin() })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete Account", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
            if (u.status == "pending" && manage) {
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
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
