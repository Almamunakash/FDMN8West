package com.informationplant.fdmn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContent { FdmnTheme { FdmnApp() } }
    }
}

sealed class Screen {
    object Home : Screen()
    object Records : Screen()
    object Messages : Screen()
    object More : Screen()
    data class Detail(val id: Int) : Screen()
    data class Family(val fcn: String) : Screen()
    data class Edit(val person: Person) : Screen()
    object Users : Screen()
    object Import : Screen()
    object ChangePassword : Screen()
}

/** Keeps the record list, its filters and scroll position while moving between screens. */
class RecordsState {
    var filters by mutableStateOf(Filters())
    var query by mutableStateOf("")
    val items = mutableStateListOf<Person>()
    var total by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loadedKey by mutableStateOf<Any?>(null)
    val listState = LazyListState()
    private var generation = 0
    val endReached get() = items.size >= total

    suspend fun reload(key: Any) {
        val gen = ++generation
        loading = true
        error = null
        try {
            val r = Api.call("list", filters.toJson().put("per", 60).put("offset", 0))
            if (gen != generation) return
            items.clear()
            items.addAll(r.optJSONArray("rows").mapObjects { Person.from(it) })
            total = r.optInt("total")
            loadedKey = key
            listState.scrollToItem(0)
        } catch (e: Exception) {
            if (gen == generation) error = e.message
        } finally {
            if (gen == generation) loading = false
        }
    }

    suspend fun loadMore() {
        if (loading || endReached) return
        val gen = generation
        loading = true
        try {
            val r = Api.call("list", filters.toJson().put("per", 60).put("offset", items.size))
            if (gen != generation) return
            val have = items.map { it.id }.toHashSet()
            val more = r.optJSONArray("rows").mapObjects { Person.from(it) }.filter { it.id !in have }
            items.addAll(more)
            total = r.optInt("total")
            if (more.isEmpty()) total = items.size
        } catch (e: Exception) {
            if (gen == generation) error = e.message
        } finally {
            if (gen == generation) loading = false
        }
    }
}

class AppCtl(val scope: CoroutineScope, val snackbar: SnackbarHostState) {
    var user by mutableStateOf<User?>(null)
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var version by mutableIntStateOf(0)
    var meta by mutableStateOf(Meta())
    var stats by mutableStateOf(Stats())
    val records = RecordsState()

    val current: Screen get() = stack.last()
    val isAdmin get() = user?.isAdmin == true

    fun push(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(s: Screen) { stack.clear(); stack.add(s) }

    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    /** Call after data was changed, so lists and counts reload. */
    fun changed() { version++; refreshMeta() }

    fun openRecords(f: Filters) {
        records.filters = f
        records.query = f.q
        tab(Screen.Records)
    }

    fun refreshMeta() {
        scope.launch {
            try {
                val b = Api.call("blocks")
                val o = Api.call("options")
                meta = Meta(
                    blocks = b.optJSONArray("blocks").mapObjects { m ->
                        MainBlock(m.str("name"), m.optInt("count"), m.optInt("families"),
                            m.optJSONArray("subs").mapObjects { SubBlock(it.str("name"), it.optInt("count"), it.optInt("families")) })
                    },
                    relations = o.optJSONArray("relations").strings(),
                    majhis = o.optJSONArray("majhis").strings(),
                )
            } catch (_: Exception) { }
        }
    }

    fun refreshStats() {
        scope.launch {
            try { stats = Stats.from(Api.call("stats").getJSONObject("stats")) } catch (_: Exception) { }
        }
    }

    fun signedIn(token: String, u: User) {
        Api.token = token
        Prefs.token = token
        Prefs.userJson = u.toJson().toString()
        Prefs.lastEmail = u.email
        user = u
        records.filters = Filters()
        records.query = ""
        records.items.clear()
        records.loadedKey = null
        tab(Screen.Home)
        refreshMeta()
        refreshStats()
    }

    fun signOut(callServer: Boolean = true) {
        val t = Api.token
        if (callServer && t.isNotEmpty()) scope.launch { try { Api.call("logout") } catch (_: Exception) { } }
        Api.token = ""
        Prefs.token = ""
        Prefs.userJson = ""
        user = null
        tab(Screen.Home)
    }
}

@Composable
fun FdmnApp() {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val app = remember { AppCtl(scope, snackbar) }

    LaunchedEffect(Unit) {
        Api.onAuthLost = {
            if (app.user != null) {
                app.signOut(callServer = false)
                app.toast("Please sign in again.")
            }
        }
        val t = Prefs.token
        val uj = Prefs.userJson
        if (t.isNotEmpty() && uj.isNotEmpty()) {
            Api.token = t
            app.user = try { User.from(JSONObject(uj)) } catch (e: Exception) { null }
            if (app.user != null) {
                app.refreshMeta()
                app.refreshStats()
                try {
                    val u = User.from(Api.call("me").getJSONObject("user"))
                    app.user = u
                    Prefs.userJson = u.toJson().toString()
                } catch (_: Exception) { }
            }
        }
    }

    val user = app.user
    if (user == null) {
        AuthFlow(app)
        return
    }

    BackHandler(enabled = app.stack.size > 1) { app.back() }

    val root = app.stack.first()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Brand, selectedTextColor = Brand,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                )
                NavigationBarItem(
                    selected = root == Screen.Home, onClick = { app.tab(Screen.Home) },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") }, colors = colors,
                )
                NavigationBarItem(
                    selected = root == Screen.Records, onClick = { app.tab(Screen.Records) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("Records") }, colors = colors,
                )
                val msgCount = if (app.isAdmin) app.stats.openMessages else app.stats.unreadReplies
                NavigationBarItem(
                    selected = root == Screen.Messages, onClick = { app.tab(Screen.Messages) },
                    icon = { BadgeIcon(msgCount) { Icon(Icons.Filled.Email, null) } },
                    label = { Text("Messages") }, colors = colors,
                )
                NavigationBarItem(
                    selected = root == Screen.More, onClick = { app.tab(Screen.More) },
                    icon = { BadgeIcon(if (app.isAdmin) app.stats.pendingUsers else 0) { Icon(Icons.Filled.Menu, null) } },
                    label = { Text("More") }, colors = colors,
                )
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).consumeWindowInsets(pad)) {
            when (val s = app.current) {
                Screen.Home -> HomeScreen(app)
                Screen.Records -> RecordsScreen(app)
                Screen.Messages -> MessagesScreen(app)
                Screen.More -> MoreScreen(app)
                is Screen.Detail -> DetailScreen(app, s.id)
                is Screen.Family -> FamilyScreen(app, s.fcn)
                is Screen.Edit -> EditScreen(app, s.person)
                Screen.Users -> UsersScreen(app)
                Screen.Import -> ImportScreen(app)
                Screen.ChangePassword -> ChangePasswordScreen(app)
            }
        }
    }
}

@Composable
fun BadgeIcon(count: Int, icon: @Composable () -> Unit) {
    if (count > 0) {
        BadgedBox(badge = { Badge(containerColor = Color(0xFFD32F2F)) { Text(if (count > 99) "99+" else count.toString()) } }) { icon() }
    } else {
        icon()
    }
}
