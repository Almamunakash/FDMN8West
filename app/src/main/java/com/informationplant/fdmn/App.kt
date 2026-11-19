package com.informationplant.fdmn

import android.app.Activity
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
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
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
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
        enableEdgeToEdge()
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
    object CampProfile : Screen()
    object Reports : Screen()
    object Site : Screen()
    data class CampItem(val key: String, val edit: Boolean) : Screen()
    object Contacts : Screen()
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
    var camp by mutableStateOf(CampData())
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
        refreshCamp()
    }

    fun refreshCamp() {
        scope.launch {
            try { camp = CampData.from(Api.call("settings_get").getJSONObject("settings")) } catch (_: Exception) { }
        }
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
    var splash by remember { mutableStateOf(true) }

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
                app.refreshCamp()
            }
        }
        delay(1500)
        splash = false
        if (app.user != null) {
            try {
                val u = User.from(Api.call("me").getJSONObject("user"))
                app.user = u
                Prefs.userJson = u.toJson().toString()
            } catch (_: Exception) { }
        }
    }

    // Dark status-bar icons on white screens, light icons on green/photo screens.
    val user = app.user
    val lightScreen = splash || user == null
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, view).isAppearanceLightStatusBars = lightScreen
        }
    }

    if (splash) {
        SplashScreen()
        return
    }
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
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val msgCount = if (app.isAdmin) app.stats.openMessages else app.stats.unreadReplies
                    NavPill(root == Screen.Home, Icons.Filled.Dashboard, "Dashboard", 0, Modifier.weight(1f)) { app.tab(Screen.Home) }
                    NavPill(root == Screen.Records, Icons.Filled.Storage, "Records", 0, Modifier.weight(1f)) { app.tab(Screen.Records) }
                    NavPill(root == Screen.Reports, Icons.Filled.BarChart, "Reports", 0, Modifier.weight(1f)) { app.tab(Screen.Reports) }
                    NavPill(root == Screen.Messages, Icons.Outlined.Email, "Messages", msgCount, Modifier.weight(1f)) { app.tab(Screen.Messages) }
                    NavPill(root == Screen.More, Icons.Filled.Menu, "More", if (app.isAdmin) app.stats.pendingUsers else 0, Modifier.weight(1f)) { app.tab(Screen.More) }
                }
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
                Screen.CampProfile -> CampProfileScreen(app)
                Screen.Reports -> ReportsScreen(app)
                Screen.Site -> SiteUpdateScreen(app)
                is Screen.CampItem -> CampItemScreen(app, s.key, s.edit)
                Screen.Contacts -> ContactsEditScreen(app)
            }
        }
    }
}

@Composable
private fun NavPill(selected: Boolean, icon: ImageVector, label: String, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .then(if (selected) Modifier.shadow(6.dp, shape) else Modifier)
            .clip(shape)
            .then(if (selected) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF138A6E), BrandDark)), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(icon, label, tint = if (selected) Color.White else BrandDark, modifier = Modifier.size(26.dp))
                if (badge > 0) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp)
                            .size(if (badge > 9) 20.dp else 16.dp).background(Color(0xFFE53935), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Text(
                label, fontSize = 11.sp, maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) Color.White else BrandDark,
            )
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Image(
                painterResource(R.drawable.logo_full), contentDescription = "Office of the Camp-in-Charge, Camp-8 West",
                modifier = Modifier.fillMaxWidth(0.92f),
            )
            Spacer(Modifier.height(28.dp))
            Text("Camp- 8 West", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Brand)
            Text("Information Management System", fontSize = 15.sp, color = Color(0xFF5B6B65))
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = Brand, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}
