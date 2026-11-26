package com.bliss.blisschat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.data.call.CallPhase
import com.bliss.blisschat.ui.admin.AdminScreen
import com.bliss.blisschat.ui.auth.AuthScreen
import com.bliss.blisschat.ui.auth.ProfileSetupScreen
import com.bliss.blisschat.ui.auth.SessionErrorScreen
import com.bliss.blisschat.ui.auth.SplashScreen
import com.bliss.blisschat.ui.call.CallScreen
import com.bliss.blisschat.ui.chat.ChatScreen
import com.bliss.blisschat.ui.create.CreateScreen
import com.bliss.blisschat.ui.home.HomeScreen
import com.bliss.blisschat.ui.home.SearchFocus
import com.bliss.blisschat.ui.calls.CallsScreen
import androidx.compose.material.icons.filled.Search
import com.bliss.blisschat.ui.home.SystemBotScreen
import com.bliss.blisschat.ui.plugins.ModerationScreen
import com.bliss.blisschat.ui.plugins.PluginsScreen
import com.bliss.blisschat.ui.stickers.StickersScreen
import com.bliss.blisschat.ui.update.UpdateDialog
import com.bliss.blisschat.ui.profile.ProfileScreen
import com.bliss.blisschat.ui.settings.AppearancePage
import com.bliss.blisschat.ui.settings.BotsPage
import com.bliss.blisschat.ui.settings.GuidePage
import com.bliss.blisschat.ui.settings.PluginMessages
import com.bliss.blisschat.ui.settings.PluginSettingsPage
import com.bliss.blisschat.ui.settings.NotificationsPage
import com.bliss.blisschat.ui.settings.PrivacyPage
import com.bliss.blisschat.ui.settings.SecurityPage
import com.bliss.blisschat.ui.settings.SettingsScreen
import com.bliss.blisschat.ui.settings.UpdatesPage
import com.bliss.blisschat.ui.security.RestoreKeysDialog
import com.bliss.blisschat.KeyState
import com.bliss.blisschat.ui.theme.AvatarShape
import androidx.compose.ui.graphics.Color
import com.bliss.blisschat.ui.glass.glassPanel
import com.bliss.blisschat.ui.glass.glassSource

private enum class Stage { LOADING, AUTH, PROFILE, MAIN, ERROR }

@Composable
fun AppRoot(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    val call by container.calls.ui.collectAsStateWithLifecycle()
    val group by container.groupCalls.ui.collectAsStateWithLifecycle()
    val stage = when (session) {
        Session.Loading -> Stage.LOADING
        Session.SignedOut -> Stage.AUTH
        is Session.NeedsProfile -> Stage.PROFILE
        is Session.Ready -> Stage.MAIN
        is Session.Error -> Stage.ERROR
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Fade Through between the top-level stages
        AnimatedContent(
            targetState = stage, label = "stage",
            transitionSpec = {
                (fadeIn(tween(210, 90)) + scaleIn(tween(210, 90), initialScale = 0.92f)) togetherWith fadeOut(tween(90))
            }
        ) { st ->
            when (st) {
                Stage.LOADING -> SplashScreen()
                Stage.AUTH -> AuthScreen(container)
                Stage.PROFILE -> ProfileSetupScreen(container)
                Stage.MAIN -> MainNav(container)
                Stage.ERROR -> SessionErrorScreen((session as? Session.Error)?.message.orEmpty()) { container.signOutPlain() }
            }
        }
        AnimatedVisibility(
            visible = call.phase != CallPhase.IDLE && stage == Stage.MAIN,
            enter = slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
        ) { CallScreen(container) }
        AnimatedVisibility(
            visible = group.active && stage == Stage.MAIN,
            enter = slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
        ) { com.bliss.blisschat.ui.call.GroupCallScreen(container) }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("chats", "чаты", Icons.Filled.ChatBubble),
    Tab("profile", "профиль", Icons.Filled.Person),
    Tab("settings", "настройки", Icons.Filled.Settings)
)

@Composable
private fun MainNav(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // the settings sub-screens keep the bottom bar (the "настройки" tab stays highlighted), like in the mockups
    val barRoute = if (route != null && route.startsWith("settings/")) "settings" else route
    val showBar = barRoute in Tabs.map { it.route }
    val myId = container.currentUid.orEmpty()

    // Background update check on every start of the main screen
    LaunchedEffect(Unit) { container.updates.check(silent = true) }
    UpdateDialog(container.updates)
    PluginMessages(container)

    // New phone with a cloud key backup: ask for the password before anything is shown (otherwise chats can't be read)
    val keyState by container.keyState.collectAsStateWithLifecycle()
    if (keyState == KeyState.NEEDS_RESTORE) RestoreKeysDialog(container)

    // Tapped notification -> open that chat
    val pending by container.pendingChat.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let { id ->
            container.pendingChat.value = null
            nav.navigate("chat/$id") { launchSingleTop = true }
        }
    }

    val glassBackdrop = com.bliss.blisschat.ui.glass.rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav, startDestination = "chats", modifier = Modifier.fillMaxSize().glassSource(glassBackdrop),
            // Shared Axis (X) for drill-down, Fade Through for tab switches
            enterTransition = { if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeIn(tween(210, 90)) else slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
            exitTransition = { if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeOut(tween(90)) else slideOutHorizontally(tween(300)) { -it / 5 } + fadeOut(tween(200)) },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 5 } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)) }
        ) {
            composable("chats") { HomeScreen(container, onOpenChat = { id -> nav.navigate("chat/$id") }, onCreate = { nav.navigate("create") }, onOpenSystemBot = { nav.navigate("bot") }, onOpenCalls = { nav.navigate("calls") }) }
            composable("calls") { CallsScreen(container, onBack = { nav.popBackStack() }, onOpenChat = { id -> nav.navigate("chat/$id") }) }
            composable("create") { CreateScreen(container, onClose = { nav.popBackStack() }, onOpenChat = { id -> nav.navigate("chat/$id") { popUpTo("chats") } }) }
            composable("bot") { SystemBotScreen(container) { nav.popBackStack() } }
            composable("plugins") { PluginsScreen(container) { nav.popBackStack() } }
            composable("moderation") { ModerationScreen(container) { nav.popBackStack() } }
            composable("stickers") { StickersScreen(container) { nav.popBackStack() } }
            composable("profile") {
                ProfileScreen(container, myId, onBack = null, onOpenChat = { id -> nav.navigate("chat/$id") })
            }
            composable("settings") {
                SettingsScreen(container, onNavigate = { r ->
                    if (r == "profile") nav.navigate("profile") { popUpTo("chats") { saveState = true }; launchSingleTop = true; restoreState = true }
                    else nav.navigate(r)
                })
            }
            composable("settings/appearance") { AppearancePage(container) { nav.popBackStack() } }
            composable("settings/notifications") { NotificationsPage(container) { nav.popBackStack() } }
            composable("settings/privacy") { PrivacyPage(container) { nav.popBackStack() } }
            composable("settings/security") { SecurityPage(container) { nav.popBackStack() } }
            composable("settings/plugins") { PluginSettingsPage(container, { nav.navigate("plugins") }) { nav.popBackStack() } }
            composable("settings/guide") { GuidePage { nav.popBackStack() } }
            composable("settings/bots") { BotsPage(container) { nav.popBackStack() } }
            composable("settings/updates") { UpdatesPage(container) { nav.popBackStack() } }
            composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                ChatScreen(container, id, onBack = { nav.popBackStack() }, onOpenProfile = { uid -> nav.navigate("user/$uid") }, onOpenStickers = { nav.navigate("stickers") })
            }
            composable("user/{uid}", arguments = listOf(navArgument("uid") { type = NavType.StringType })) { e ->
                val uid = e.arguments?.getString("uid").orEmpty()
                ProfileScreen(container, uid, onBack = { nav.popBackStack() }, onOpenChat = { id ->
                    nav.navigate("chat/$id") { popUpTo("chats") }
                })
            }
            composable("admin") { AdminScreen(container) { nav.popBackStack() } }
        }
        AnimatedVisibility(
            visible = showBar, modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(260)) { it } + fadeIn(), exit = slideOutVertically(tween(200)) { it } + fadeOut()
        ) { BottomPill(nav, barRoute, glassBackdrop) }
    }
}

private fun isTab(route: String?) = route == "chats" || route == "profile" || route == "settings"

@Composable
private fun BottomPill(nav: NavHostController, current: String?, backdrop: com.bliss.blisschat.ui.glass.GlassBackdrop) {
    val glassOn = com.bliss.blisschat.BuildConfig.GLASS
    val quality = com.bliss.blisschat.ui.glass.LocalGlassQuality.current
    val scheme = MaterialTheme.colorScheme
    // Round search button + pill with the three tabs; the selected tab expands to icon + label.
    Row(
        Modifier.navigationBarsPadding().padding(bottom = 14.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = {
                if (current != "chats") nav.navigate("chats") { popUpTo("chats") { saveState = true }; launchSingleTop = true; restoreState = true }
                SearchFocus.tick.value = SearchFocus.tick.value + 1
            },
            shape = CircleShape, color = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = if (glassOn) 0.dp else 10.dp,
            modifier = Modifier.size(60.dp).glassPanel(backdrop, CircleShape, quality, scheme.primary.copy(alpha = 0.6f), 30.dp)
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Search, "Поиск", Modifier.size(28.dp)) } }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
        Surface(
            shape = CircleShape, color = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 0.dp,
            shadowElevation = if (glassOn) 0.dp else 10.dp,
            modifier = Modifier.glassPanel(backdrop, CircleShape, quality, scheme.secondaryContainer.copy(alpha = 0.5f), 32.dp)
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tabs.forEach { tab ->
                    val selected = current == tab.route
                    val bg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (glassOn) 0.55f else 1f)
                        else if (glassOn) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer, tween(250), label = "tab-bg")
                    val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer, tween(250), label = "tab-fg")
                    Row(
                        Modifier.clip(CircleShape).background(bg)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                if (!selected) nav.navigate(tab.route) {
                                    popUpTo("chats") { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                } else if (tab.route == "settings") nav.popBackStack("settings", false)
                            }
                            .padding(horizontal = if (selected) 16.dp else 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
                        androidx.compose.animation.AnimatedVisibility(selected) {
                            androidx.compose.material3.Text(tab.label, style = MaterialTheme.typography.labelLarge, color = fg, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
