package com.bliss.blisschat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bliss.blisschat.AppContainer
import com.bliss.blisschat.Session
import com.bliss.blisschat.ui.admin.AdminScreen
import com.bliss.blisschat.ui.auth.AuthFlow
import com.bliss.blisschat.ui.auth.ProfileSetupScreen
import com.bliss.blisschat.ui.auth.SessionErrorScreen
import com.bliss.blisschat.ui.auth.SplashScreen
import com.bliss.blisschat.ui.chat.ChatScreen
import com.bliss.blisschat.ui.home.HomeScreen
import com.bliss.blisschat.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private enum class Stage { LOADING, AUTH, PROFILE, MAIN, ERROR }

private fun Session.stage() = when (this) {
    Session.Loading -> Stage.LOADING
    Session.SignedOut -> Stage.AUTH
    is Session.NeedsProfile -> Stage.PROFILE
    is Session.Ready -> Stage.MAIN
    is Session.Error -> Stage.ERROR
}

@Composable
fun AppRoot(container: AppContainer) {
    val session by container.session.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Material Motion "Fade Through" between the big app stages
        AnimatedContent(
            targetState = session.stage(),
            transitionSpec = {
                (fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing)) +
                    scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.92f)) togetherWith
                    fadeOut(tween(90, easing = FastOutSlowInEasing))
            },
            label = "root-stage"
        ) { stage ->
            when (stage) {
                Stage.LOADING -> SplashScreen()
                Stage.AUTH -> AuthFlow(container)
                Stage.PROFILE -> ProfileSetupScreen(container)
                Stage.MAIN -> MainNav(container)
                Stage.ERROR -> SessionErrorScreen(
                    message = (session as? Session.Error)?.message.orEmpty(),
                    onSignOut = { scope.launch { container.auth.signOut() } }
                )
            }
        }
    }
}

/* Material Motion "Shared Axis X" for screen-to-screen navigation */
private const val NAV_MS = 320
private val forwardEnter: EnterTransition =
    slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 7 } +
        fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing))
private val forwardExit: ExitTransition =
    slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 7 } +
        fadeOut(tween(90, easing = FastOutSlowInEasing))
private val backEnter: EnterTransition =
    slideInHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { -it / 7 } +
        fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing))
private val backExit: ExitTransition =
    slideOutHorizontally(tween(NAV_MS, easing = FastOutSlowInEasing)) { it / 7 } +
        fadeOut(tween(90, easing = FastOutSlowInEasing))

@Composable
private fun MainNav(container: AppContainer) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { forwardEnter },
        exitTransition = { forwardExit },
        popEnterTransition = { backEnter },
        popExitTransition = { backExit }
    ) {
        composable("home") {
            HomeScreen(
                container = container,
                onOpenChat = { id -> nav.navigate("chat/$id") },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        composable(
            route = "chat/{chatId}",
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { entry ->
            ChatScreen(
                container = container,
                chatId = entry.arguments?.getString("chatId").orEmpty(),
                onBack = { nav.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenAdmin = { nav.navigate("admin") }
            )
        }
        composable("admin") {
            AdminScreen(container = container, onBack = { nav.popBackStack() })
        }
    }
}
