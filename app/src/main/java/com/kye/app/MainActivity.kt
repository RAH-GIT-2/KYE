package com.kye.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kye.app.ui.KyeViewModel
import com.kye.app.ui.screens.AddEditScreen
import com.kye.app.ui.screens.BudgetsScreen
import com.kye.app.ui.screens.CategoriesScreen
import com.kye.app.ui.screens.CategoryDetailScreen
import com.kye.app.ui.screens.HistoryScreen
import com.kye.app.ui.screens.HomeScreen
import com.kye.app.ui.screens.InsightsScreen
import com.kye.app.ui.screens.RecurringScreen
import com.kye.app.ui.screens.RuleEditScreen
import com.kye.app.ui.screens.SettingsScreen
import com.kye.app.ui.screens.VoiceSheet
import com.kye.app.ui.theme.KyeTheme

class MainActivity : ComponentActivity() {
    private val vm: KyeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            KyeTheme(settings?.theme ?: "system") {
                KyeRoot(vm)
            }
        }
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
private fun KyeRoot(vm: KyeViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Hidden when turned off in Settings, or when the phone has no speech recognition at all.
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val showMic = speechAvailable && (settings?.voiceEnabled ?: true)

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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = onTab,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180))
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.goTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            // Home: mic (standard size) with a small + above it; just + when voice is off.
            // History: + alone. Insights and Recurring: nothing floating over the content.
            val micHere = route == "home" && showMic
            AnimatedVisibility(
                visible = route == "home" || route == "history",
                enter = scaleIn(tween(200)) + fadeIn(tween(200)),
                exit = scaleOut(tween(150)) + fadeOut(tween(150))
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (micHere) {
                        SmallFloatingActionButton(
                            onClick = { nav.navigate("add") },
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
                            onClick = { nav.navigate("add") },
                            shape = RoundedCornerShape(18.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Add entry", modifier = Modifier.size(28.dp)) }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.97f) },
            popExitTransition = { fadeOut(tween(150)) }
        ) {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    onOpenTxn = { nav.navigate("add?id=$it") },
                    onSeeAll = { nav.goTab("history") },
                    onSettings = { nav.navigate("settings") },
                    onBudgets = { nav.navigate("budgets") },
                    onRecurring = { nav.goTab("recurring") },
                    onAddPending = { nav.navigate("add?pending=$it") }
                )
            }
            composable("history") { HistoryScreen(vm) { nav.navigate("add?id=$it") } }
            composable("insights") { InsightsScreen(vm) { nav.navigate("budgets") } }
            composable("recurring") {
                RecurringScreen(vm, onOpenRule = { nav.navigate("rule?id=$it") }, onNewRule = { nav.navigate("rule") })
            }
            composable(
                "add?id={id}&pending={pending}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("pending") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { e ->
                AddEditScreen(
                    vm,
                    txnId = e.arguments?.getLong("id") ?: -1L,
                    pendingId = e.arguments?.getLong("pending") ?: -1L,
                    onManageCategories = { nav.navigate("categories") }
                ) { nav.popBackStack() }
            }
            composable("categories") {
                CategoriesScreen(vm, onOpen = { nav.navigate("category/$it") }) { nav.popBackStack() }
            }
            composable(
                "category/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { e ->
                CategoryDetailScreen(vm, e.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
            }
            composable(
                "rule?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { e ->
                RuleEditScreen(vm, e.arguments?.getLong("id") ?: -1L) { nav.popBackStack() }
            }
            composable("settings") {
                SettingsScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onBudgets = { nav.navigate("budgets") },
                    onCategories = { nav.navigate("categories") }
                )
            }
            composable("budgets") { BudgetsScreen(vm) { nav.popBackStack() } }
        }
    }

    if (vm.voiceOpen) {
        VoiceSheet(vm) { vm.voiceOpen = false }
    }
}

private fun NavHostController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
