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
import com.bliss.blisschat.ui.home.HomeScreen
import com.bliss.blisschat.ui.profile.ProfileScreen
import com.bliss.blisschat.ui.settings.SettingsScreen
import com.bliss.blisschat.ui.theme.AvatarShape

private enum class Stage { LOADING, AUTH, PROFILE, MAIN, ERROR }

@Composable
fun AppRoot(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    val call by container.calls.ui.collectAsStateWithLifecycle()
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
                Stage.ERROR -> SessionErrorScreen((session as? Session.Error)?.message.orEmpty()) { container.auth.signOut() }
            }
        }
        AnimatedVisibility(
            visible = call.phase != CallPhase.IDLE && stage == Stage.MAIN,
            enter = slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
        ) { CallScreen(container) }
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
    val showBar = route in Tabs.map { it.route }
    val myId = container.currentUid.orEmpty()

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav, startDestination = "chats", modifier = Modifier.fillMaxSize(),
            // Shared Axis (X) for drill-down, Fade Through for tab switches
            enterTransition = { if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeIn(tween(210, 90)) else slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
            exitTransition = { if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeOut(tween(90)) else slideOutHorizontally(tween(300)) { -it / 5 } + fadeOut(tween(200)) },
            popEnterTransition = { slideInHorizontally(tween(300)) { -it / 5 } + fadeIn(tween(300)) },
            popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)) }
        ) {
            composable("chats") { HomeScreen(container) { id -> nav.navigate("chat/$id") } }
            composable("profile") {
                ProfileScreen(container, myId, onBack = null, onOpenChat = { id -> nav.navigate("chat/$id") })
            }
            composable("settings") { SettingsScreen(container) { nav.navigate("admin") } }
            composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                ChatScreen(container, id, onBack = { nav.popBackStack() }, onOpenProfile = { uid -> nav.navigate("user/$uid") })
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
        ) { BottomPill(nav, route) }
    }
}

private fun isTab(route: String?) = route == "chats" || route == "profile" || route == "settings"

@Composable
private fun BottomPill(nav: NavHostController, current: String?) {
    Surface(
        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp, shadowElevation = 10.dp,
        modifier = Modifier.navigationBarsPadding().padding(bottom = 14.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Tabs.forEach { tab ->
                val selected = current == tab.route
                val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, tween(250), label = "tab-bg")
                val fg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, tween(250), label = "tab-fg")
                androidx.compose.foundation.layout.Column(
                    Modifier.clip(AvatarShape).background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!selected) nav.navigate(tab.route) {
                                popUpTo("chats") { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
                    androidx.compose.material3.Text(tab.label, style = MaterialTheme.typography.labelSmall, color = fg)
                }
            }
        }
    }
}
