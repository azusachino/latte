package com.azusachino.latte.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.azusachino.latte.BuildConfig
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.ui.common.ToastHost
import com.azusachino.latte.ui.common.ToastManager
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

private val Screen.navDepth: Int
    get() = when (this) {
        is Screen.Explore -> 0
        is Screen.Detail -> 1
        is Screen.Settings -> 1
        is Screen.AccountManager -> 2
    }

// A quick, slightly overshooting settle (inspired by transitions.dev's toggle
// curve) rather than Compose's flatter default spring for slideIn/Out.
private val screenSlideSpec = spring<IntOffset>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

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
    val pixivOAuthClient = remember {
        if (BuildConfig.PIXIV_OAUTH_CLIENT_ID.isNotBlank() && BuildConfig.PIXIV_OAUTH_CLIENT_SECRET.isNotBlank()) {
            com.azusachino.latte.data.network.PixivOAuthClient(
                httpClient = com.azusachino.latte.data.network.OkHttpProvider.client,
                configuration = com.azusachino.latte.data.network.PixivOAuthConfiguration(
                    clientId = BuildConfig.PIXIV_OAUTH_CLIENT_ID,
                    clientSecret = BuildConfig.PIXIV_OAUTH_CLIENT_SECRET,
                ),
            )
        } else {
            null
        }
    }
    val pixivPlugin = remember {
        com.azusachino.latte.plugin.pixiv.PixivPlugin(
            storage = pluginStorage,
            httpClient = com.azusachino.latte.data.network.OkHttpProvider.client,
            oauthClient = pixivOAuthClient,
        )
    }
    val sitePluginManager = remember { com.azusachino.latte.plugin.SitePluginManager(listOf(yandePlugin, pixivPlugin)) }

    LaunchedEffect(pixivPlugin.api) {
        exploreViewModel.configurePixiv(pixivPlugin.api)
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Explore) }
    var activeLoginPlugin by remember { mutableStateOf<com.azusachino.latte.plugin.SitePlugin?>(null) }

    LatteTheme(themeMode = themeMode) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentScreen,
                label = "ScreenTransition",
                transitionSpec = {
                    if (targetState.navDepth >= initialState.navDepth) {
                        (slideInHorizontally(screenSlideSpec) { width -> width } + fadeIn())
                            .togetherWith(slideOutHorizontally(screenSlideSpec) { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally(screenSlideSpec) { width -> -width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(screenSlideSpec) { width -> width } + fadeOut())
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                when (screen) {
                    is Screen.Explore -> {
                        ExploreScreen(
                            viewModel = exploreViewModel,
                            sitePlugin = yandePlugin,
                            pixivPlugin = pixivPlugin,
                            onPostClick = { index ->
                                currentScreen = Screen.Detail(index)
                            },
                            onOpenSettings = {
                                currentScreen = Screen.Settings
                            },
                            onRequireLogin = { plugin ->
                                activeLoginPlugin = plugin
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
                            pluginManager = sitePluginManager,
                            onBack = {
                                currentScreen = Screen.Explore
                            },
                            onTagClick = { tag ->
                                exploreViewModel.search(tag)
                                currentScreen = Screen.Explore
                            },
                            onRequireLogin = { plugin ->
                                activeLoginPlugin = plugin
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

            // Global Login Dialog
            activeLoginPlugin?.let { plugin ->
                com.azusachino.latte.ui.account.PluginLoginDialog(
                    plugin = plugin,
                    onDismissRequest = { activeLoginPlugin = null },
                    onLoginSuccess = {
                        activeLoginPlugin = null
                        ToastManager.showSuccess("Signed in to ${plugin.name}")
                    },
                )
            }

            // Stacking, full-width toasts over all screens
            ToastHost(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
