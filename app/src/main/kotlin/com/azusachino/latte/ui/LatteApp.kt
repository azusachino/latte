package com.azusachino.latte.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.ui.common.ToastHost
import com.azusachino.latte.ui.detail.DetailScreen
import com.azusachino.latte.ui.explore.ExploreScreen
import com.azusachino.latte.ui.explore.ExploreViewModel
import com.azusachino.latte.ui.settings.SettingsScreen
import com.azusachino.latte.ui.theme.LatteTheme

sealed interface Screen {
    data object Explore : Screen
    data class Detail(val initialIndex: Int) : Screen
    data object Settings : Screen
    data object AccountManager : Screen
}

@Composable
fun LatteApp(
    exploreViewModel: ExploreViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager(context) }
    val themeMode by exploreViewModel.preferences.themeMode.collectAsState()

    val pluginStorage = remember { com.azusachino.latte.plugin.storage.SecurePluginStorage(context) }
    val yandePlugin = remember { com.azusachino.latte.plugin.yande.YandePlugin(pluginStorage, com.azusachino.latte.data.network.OkHttpProvider.client, com.azusachino.latte.data.network.OkHttpProvider.cookieJar) }
    val sitePluginManager = remember { com.azusachino.latte.plugin.SitePluginManager(listOf(yandePlugin)) }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Explore) }

    LatteTheme(themeMode = themeMode) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                label = "ScreenTransition",
                transitionSpec = {
                    when {
                        targetState is Screen.Detail || targetState is Screen.Settings || targetState is Screen.AccountManager -> {
                            (slideInHorizontally { width -> width } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                        }
                        else -> {
                            (slideInHorizontally { width -> -width / 3 } + fadeIn())
                                .togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                when (screen) {
                    is Screen.Explore -> {
                        ExploreScreen(
                            viewModel = exploreViewModel,
                            onPostClick = { index ->
                                currentScreen = Screen.Detail(index)
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
                            },
                        )
                    }
                    is Screen.Detail -> {
                        BackHandler {
                            currentScreen = Screen.Explore
                        }
                        val posts = exploreViewModel.uiState.collectAsState().value.posts
                        DetailScreen(
                            posts = posts,
                            initialIndex = screen.initialIndex,
                            downloadManager = downloadManager,
                            onBack = {
                                currentScreen = Screen.Explore
                            },
                            onTagClick = { tag ->
                                exploreViewModel.search(tag)
                                currentScreen = Screen.Explore
                            },
                        )
                    }
                    is Screen.Settings -> {
                        BackHandler {
                            currentScreen = Screen.Explore
                        }
                        SettingsScreen(
                            preferences = exploreViewModel.preferences,
                            onBack = {
                                currentScreen = Screen.Explore
                            },
                            onOpenAccountManager = {
                                currentScreen = Screen.AccountManager
                            },
                        )
                    }
                    is Screen.AccountManager -> {
                        BackHandler {
                            currentScreen = Screen.Settings
                        }
                        com.azusachino.latte.ui.account.AccountManagerScreen(
                            pluginManager = sitePluginManager,
                            onBack = {
                                currentScreen = Screen.Settings
                            },
                        )
                    }
                }
            }

            // Stacking, full-width toasts over all screens
            ToastHost(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
