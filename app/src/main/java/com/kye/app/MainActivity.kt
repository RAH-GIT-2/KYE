@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.kye.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kye.app.ui.AppLock
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.screens.AddEditScreen
import com.kye.app.ui.screens.AskScreen
import com.kye.app.ui.screens.BudgetsScreen
import com.kye.app.ui.screens.CategoriesScreen
import com.kye.app.ui.screens.CategoryDetailScreen
import com.kye.app.ui.screens.HistoryScreen
import com.kye.app.ui.screens.HomeScreen
import com.kye.app.ui.screens.EventDetailScreen
import com.kye.app.ui.screens.EventsScreen
import com.kye.app.ui.screens.GoalDetailScreen
import com.kye.app.ui.screens.GoalsScreen
import com.kye.app.ui.screens.GoldSchemeDetailScreen
import com.kye.app.ui.screens.GoldSchemesScreen
import com.kye.app.ui.screens.SubscriptionsScreen
import com.kye.app.ui.screens.CardDetailScreen
import com.kye.app.ui.screens.CardsScreen
import com.kye.app.ui.screens.ImportScreen
import com.kye.app.ui.screens.WarrantiesScreen
import com.kye.app.ui.screens.LoanDetailScreen
import com.kye.app.ui.screens.LoansScreen
import com.kye.app.ui.screens.InsightsScreen
import com.kye.app.ui.screens.RecurringScreen
import com.kye.app.ui.screens.RuleEditScreen
import com.kye.app.ui.screens.SettingsScreen
import com.kye.app.ui.screens.StoryScreen
import com.kye.app.ui.screens.VoiceSheet
import com.kye.app.ui.theme.KyeTheme
import com.kye.app.ui.components.ConfettiBurst
import com.kye.app.ui.components.LocalNavAnimatedScope
import com.kye.app.ui.components.LocalSharedScope
import com.kye.app.ui.components.NEW_ENTRY_KEY
import com.kye.app.ui.components.sharedCard
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

/** FragmentActivity because the fingerprint / PIN prompt needs it. */
class MainActivity : FragmentActivity() {
    private val vm: KyeViewModel by viewModels()

    /** Only matters when app lock is on. Decided in onStart. */
    private var locked by mutableStateOf(true)
    private val action = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The launch screen leaves by growing a little and fading, instead of cutting away.
        runCatching {
            splashScreen.setOnExitAnimationListener { view ->
                view.animate().alpha(0f).scaleX(1.12f).scaleY(1.12f).setDuration(280)
                    .withEndAction { view.remove() }
                    .start()
            }
        }
        action.value = intent?.getStringExtra(EXTRA_ACTION)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            KyeTheme(settings?.theme ?: "system", settings?.palette ?: "violet", settings?.pureBlack ?: false) {
                val s = settings
                when {
                    // Settings not read yet: show nothing rather than flash the data before a lock.
                    s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    s.appLock && locked -> LockScreen(::unlock)
                    else -> {
                        // Lock is off: stay unlocked, so switching it on in Settings doesn't lock straight away.
                        if (!s.appLock && locked) SideEffect { locked = false }
                        KyeRoot(vm, action)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ACTION)?.let { action.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Settings not loaded yet (fresh start) counts as "maybe locked"; the screen decides once they are.
        val lockOn = vm.settings.value?.appLock != false
        if (lockOn && System.currentTimeMillis() - lastSeen > AppLock.RELOCK_AFTER_MS) locked = true
    }

    override fun onStop() {
        super.onStop()
        // Also on rotation, so turning the phone never asks again.
        if (!locked) lastSeen = System.currentTimeMillis()
    }

    private fun unlock() {
        if (!AppLock.isAvailable(this)) {
            // The phone's screen lock was removed: nothing to check against, so let the user in.
            locked = false
            lastSeen = System.currentTimeMillis()
            return
        }
        AppLock.authenticate(this, "Unlock KYE", onSuccess = {
            locked = false
            lastSeen = System.currentTimeMillis()
        })
    }

    companion object {
        const val EXTRA_ACTION = "kye.action"
        const val ACTION_VOICE = "voice"
        const val ACTION_ADD = "add"
        const val ACTION_ASK = "ask"

        /** Last time KYE was open and unlocked. Kept for the whole process, across rotations. */
        private var lastSeen = 0L
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("KYE is locked", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text("Unlock") }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Rounded.Home),
    Tab("history", "History", Icons.Rounded.History),
    Tab("insights", "Insights", Icons.Rounded.PieChart),
    Tab("recurring", "Recurring", Icons.Rounded.Repeat)
)

@Composable
private fun KyeRoot(vm: KyeViewModel, action: MutableState<String?>) {
    val nav = rememberNavController()
    // The screen behind the floating bottom bar, blurred through it.
    val haze = remember { HazeState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Hidden when turned off in Settings, or when the phone has no speech recognition at all.
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val showMic = speechAvailable && (settings?.voiceEnabled ?: true)

    // Opened from the home-screen widget or the quick-settings tile.
    LaunchedEffect(action.value, settings != null) {
        if (settings == null) return@LaunchedEffect
        when (action.value) {
            MainActivity.ACTION_VOICE -> if (showMic) vm.voiceOpen = true else nav.navigate("add")
            MainActivity.ACTION_ADD -> nav.navigate("add")
            MainActivity.ACTION_ASK -> nav.navigate("ask?mic=false")
        }
        action.value = null
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.markAskedNotifications()
    }
    LaunchedEffect(settings?.askedNotifications) {
        val s = settings ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= 33 && !s.askedNotifications &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Lets a tapped row or the + button grow into the screen it opens.
    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedScope provides this) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = onTab,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180))
            ) {
                GlassBar(route, haze) { nav.goTab(it) }
            }
        },
        floatingActionButton = {
            // Home: mic (standard size) with a small + above it; just + when voice is off.
            // History: + adds an entry. Recurring: + adds a bill. Insights: nothing.
            val micHere = route == "home" && showMic
            val onRecurring = route == "recurring"
            AnimatedVisibility(
                visible = route == "home" || route == "history" || onRecurring,
                enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150))
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (micHere) {
                        SmallFloatingActionButton(
                            onClick = { nav.navigate("add") },
                            modifier = Modifier.sharedCard(NEW_ENTRY_KEY, this@AnimatedVisibility),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = RoundedCornerShape(14.dp)
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Add manually") }
                        FloatingActionButton(
                            onClick = { vm.voiceOpen = true },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Rounded.Mic, contentDescription = "Speak an expense", modifier = Modifier.size(26.dp)) }
                    } else {
                        FloatingActionButton(
                            onClick = { nav.navigate(if (onRecurring) "rule" else "add") },
                            modifier = if (onRecurring) Modifier else Modifier.sharedCard(NEW_ENTRY_KEY, this@AnimatedVisibility),
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = if (onRecurring) "Add recurring bill" else "Add entry",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            // Screens run under the floating bar (it is see-through); tab screens leave room at the bottom.
            modifier = Modifier.padding(top = pad.calculateTopPadding()).haze(haze),
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
            // Going back (also while the back swipe is in progress) shrinks the screen away.
            popExitTransition = { fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.9f) }
        ) {
            screen("home") {
                HomeScreen(
                    vm = vm,
                    onOpenTxn = { nav.navigate("add?id=$it") },
                    onSeeAll = { nav.goTab("history") },
                    onSettings = { nav.navigate("settings") },
                    onBudgets = { nav.navigate("budgets") },
                    onRecurring = { nav.goTab("recurring") },
                    onLoans = { nav.navigate("loans") },
                    onAsk = { mic -> nav.navigate("ask?mic=$mic") },
                    onEvent = { nav.navigate("event/$it") },
                    onGoal = { nav.navigate("goal/$it") },
                    onScheme = { nav.navigate("scheme/$it") },
                    onCard = { nav.navigate("card/$it") },
                    onStory = { nav.navigate("story/$it") }
                )
            }
            screen("goals") { GoalsScreen(vm, onOpen = { nav.navigate("goal/$it") }) { nav.popBackStack() } }
            screen(
                "goal/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                GoalDetailScreen(vm, e.arguments?.getLong("id") ?: -1L, onOpenTxn = { nav.navigate("add?id=$it") }) { nav.popBackStack() }
            }
            screen("schemes") { GoldSchemesScreen(vm, onOpen = { nav.navigate("scheme/$it") }) { nav.popBackStack() } }
            screen(
                "scheme/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                GoldSchemeDetailScreen(vm, e.arguments?.getLong("id") ?: -1L, onOpenTxn = { nav.navigate("add?id=$it") }) { nav.popBackStack() }
            }
            screen("cards") { CardsScreen(vm, onOpen = { nav.navigate("card/$it") }) { nav.popBackStack() } }
            screen(
                "card/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                CardDetailScreen(vm, e.arguments?.getLong("id") ?: -1L, onOpenTxn = { nav.navigate("add?id=$it") }) { nav.popBackStack() }
            }
            screen("subscriptions") {
                SubscriptionsScreen(vm, onOpenRule = { nav.navigate("rule?id=$it") }, onNew = { nav.navigate("rule") }) { nav.popBackStack() }
            }
            screen("warranties") { WarrantiesScreen(vm, onOpenTxn = { nav.navigate("add?id=$it") }) { nav.popBackStack() } }
            screen("events") { EventsScreen(vm, onOpen = { nav.navigate("event/$it") }) { nav.popBackStack() } }
            screen(
                "event/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                EventDetailScreen(vm, e.arguments?.getLong("id") ?: -1L, onOpenTxn = { nav.navigate("add?id=$it") }) { nav.popBackStack() }
            }
            screen(
                "ask?mic={mic}",
                arguments = listOf(navArgument("mic") { type = NavType.BoolType; defaultValue = false })
            ) { e ->
                AskScreen(
                    vm,
                    startWithMic = e.arguments?.getBoolean("mic") ?: false,
                    onOpenTxn = { nav.navigate("add?id=$it") }
                ) { nav.popBackStack() }
            }
            screen("history") { HistoryScreen(vm) { nav.navigate("add?id=$it") } }
            screen("insights") {
                InsightsScreen(vm, onOpenTxn = { nav.navigate("add?id=$it") }, onStory = { nav.navigate("story/$it") }) { nav.navigate("budgets") }
            }
            screen("story/{month}") { e ->
                val month = runCatching { java.time.YearMonth.parse(e.arguments?.getString("month")) }
                    .getOrElse { java.time.YearMonth.now().minusMonths(1) }
                StoryScreen(vm, month) { nav.popBackStack() }
            }
            screen("recurring") {
                RecurringScreen(vm, onOpenRule = { nav.navigate("rule?id=$it") })
            }
            screen(
                "add?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { e ->
                AddEditScreen(
                    vm,
                    txnId = e.arguments?.getLong("id") ?: -1L,
                    onManageCategories = { nav.navigate("categories") }
                ) { nav.popBackStack() }
            }
            screen("categories") {
                CategoriesScreen(vm, onOpen = { nav.navigate("category/$it") }) { nav.popBackStack() }
            }
            screen(
                "category/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                CategoryDetailScreen(vm, e.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
            }
            screen(
                "rule?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { e ->
                RuleEditScreen(vm, e.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
            }
            screen("settings") {
                SettingsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onBudgets = { nav.navigate("budgets") },
                    onCategories = { nav.navigate("categories") },
                    onImport = { nav.navigate("import") },
                    onLoans = { nav.navigate("loans") },
                    onEvents = { nav.navigate("events") },
                    onSubscriptions = { nav.navigate("subscriptions") },
                    onCards = { nav.navigate("cards") },
                    onGoals = { nav.navigate("goals") },
                    onSchemes = { nav.navigate("schemes") },
                    onWarranties = { nav.navigate("warranties") }
                )
            }
            screen("budgets") { BudgetsScreen(vm) { nav.popBackStack() } }
            screen("import") { ImportScreen(vm) { nav.popBackStack() } }
            screen("loans") { LoansScreen(vm, onOpen = { nav.navigate("loan/$it") }) { nav.popBackStack() } }
            screen(
                "loan/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                LoanDetailScreen(vm, e.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
            }
        }
    }
    }
    }

    if (vm.voiceOpen) {
        VoiceSheet(vm) { vm.voiceOpen = false }
    }
    ConfettiBurst(vm.confetti)
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Floating, frosted bottom bar: a rounded pill above the phone's own buttons. What scrolls under
 * it shows through, blurred. The chosen tab gets a soft pill of colour behind its icon.
 */
@Composable
private fun GlassBar(route: String?, haze: HazeState, onTab: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .shadow(18.dp, shape, ambientColor = cs.primary.copy(alpha = 0.25f), spotColor = cs.primary.copy(alpha = 0.25f))
            .clip(shape)
            .hazeChild(haze) {
                backgroundColor = cs.background
                tints = listOf(HazeTint(cs.surfaceContainerLowest.copy(alpha = 0.70f)))
                blurRadius = 24.dp
                fallbackTint = HazeTint(cs.surfaceContainerLowest.copy(alpha = 0.96f))
            }
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), shape)
            .height(66.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { t ->
            val selected = route == t.route
            val pill by animateColorAsState(
                if (selected) cs.primaryContainer else Color.Transparent, tween(250), label = "tab"
            )
            val tint by animateColorAsState(
                if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, tween(250), label = "tabTint"
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = selected,
                        onClick = { onTab(t.route) },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(pill)
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = tint
                )
            }
        }
    }
}

/** A navigation destination that also hands its enter/leave animation to shared cards. */
private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(route, arguments) { e ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(e) }
    }
}
