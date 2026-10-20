package com.checkin.assistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.checkin.assistant.ui.screens.ApiConfigScreen
import com.checkin.assistant.ui.screens.CredentialScreen
import com.checkin.assistant.ui.screens.HistoryScreen
import com.checkin.assistant.ui.screens.HomeScreen
import com.checkin.assistant.ui.screens.SettingsScreen
import com.checkin.assistant.ui.screens.WelcomeScreen
import com.checkin.assistant.ui.viewmodel.AppViewModel

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val CREDENTIAL = "credential"
    const val SETTINGS = "settings"
    const val HISTORY = "history"
    const val API_CONFIG = "api_config"
}

/** 这几个页面是全屏的，不显示底部导航 */
private val FULL_SCREEN_ROUTES = setOf(
    Routes.WELCOME, Routes.CREDENTIAL, Routes.API_CONFIG
)

@Composable
fun AppNavigation(vm: AppViewModel = hiltViewModel()) {
    val prefs by vm.prefs.collectAsState()

    // DataStore 还没读出来时不要渲染任何页面，
    // 否则会先闪一下首页、再跳到引导页，体验很怪。
    val loaded = prefs
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // 只在第一帧决定一次起点：没有 token 也没有保存的密码 = 第一次用
    val startRoute = remember {
        if (loaded.accessToken.isBlank() && !loaded.hasPassword) Routes.WELCOME
        else Routes.HOME
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val tabs = listOf(
        Triple(Routes.HOME, "首页", Icons.Filled.Home),
        Triple(Routes.HISTORY, "记录", Icons.Filled.History),
        Triple(Routes.SETTINGS, "设置", Icons.Filled.Settings)
    )

    val finishWelcome: () -> Unit = {
        nav.navigate(Routes.HOME) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            if (current !in FULL_SCREEN_ROUTES) {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = current == route,
                            onClick = {
                                if (current != route) {
                                    nav.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onDone = finishWelcome, onSkip = finishWelcome)
            }
            composable(Routes.HOME) {
                HomeScreen(onGoCredential = { nav.navigate(Routes.CREDENTIAL) })
            }
            composable(Routes.CREDENTIAL) {
                CredentialScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onGoCredential = { nav.navigate(Routes.CREDENTIAL) },
                    onGoApiConfig = { nav.navigate(Routes.API_CONFIG) }
                )
            }
            composable(Routes.API_CONFIG) {
                ApiConfigScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.HISTORY) { HistoryScreen() }
        }
    }
}
