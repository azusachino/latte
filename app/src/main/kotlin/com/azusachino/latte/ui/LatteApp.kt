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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.azusachino.latte.BuildConfig
import com.azusachino.latte.data.model.Post
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.ui.common.ToastHost
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.detail.DetailScreen
import com.azusachino.latte.ui.explore.ExploreScreen
import com.azusachino.latte.ui.explore.ExploreViewModel
import com.azusachino.latte.ui.settings.SettingsScreen
import com.azusachino.latte.ui.theme.LattePalette
import com.azusachino.latte.ui.theme.LatteTheme

sealed interface Screen {
    data object Explore : Screen
    data object AuthorWorks : Screen
    data class Detail(val posts: List<Post>, val initialIndex: Int) : Screen
    data object Settings : Screen
    data object AccountManager : Screen
}

internal data class ScreenStack(
    val screens: List<Screen> = listOf(Screen.Explore),
) {
    val current: Screen
        get() = screens.last()

    fun push(screen: Screen): ScreenStack = copy(screens = screens + screen)

    fun pop(): ScreenStack = if (screens.size > 1) {
        copy(screens = screens.dropLast(1))
    } else {
        this
    }

    fun root(): ScreenStack = copy(screens = listOf(Screen.Explore))
}

private val Screen.navDepth: Int
    get() = when (this) {
        is Screen.Explore -> 0
        is Screen.AuthorWorks -> 2
        is Screen.Detail -> 1
        is Screen.Settings -> 1
        is Screen.AccountManager -> 2
    }

private val Screen.stateKey: String
    get() = when (this) {
        is Screen.Explore -> "explore"
        is Screen.AuthorWorks -> "author-works"
        is Screen.Detail -> "detail:${posts.getOrNull(initialIndex)?.workIdentity ?: initialIndex}"
        is Screen.Settings -> "settings"
        is Screen.AccountManager -> "account-manager"
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
    val exploreState by exploreViewModel.uiState.collectAsState()
    val palette = if (exploreState.isPixiv) LattePalette.PIXIV else LattePalette.YANDE

    val pluginStorage = remember { com.azusachino.latte.plugin.storage.SecurePluginStorage(context) }
    val yandePlugin = remember { com.azusachino.latte.plugin.yande.YandePlugin(pluginStorage, com.azusachino.latte.data.network.OkHttpProvider.client, com.azusachino.latte.data.network.OkHttpProvider.cookieJar) }
    val pixivOAuthClient = remember {
        com.azusachino.latte.data.network.PixivOAuthClient(
            httpClient = com.azusachino.latte.data.network.OkHttpProvider.client,
            configuration = com.azusachino.latte.data.network.PixivOAuthConfiguration.pixivAndroid(
                clientId = BuildConfig.PIXIV_OAUTH_CLIENT_ID.takeIf(String::isNotBlank),
                clientSecret = BuildConfig.PIXIV_OAUTH_CLIENT_SECRET.takeIf(String::isNotBlank),
            ),
        )
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

    var navigation by remember { mutableStateOf(ScreenStack()) }
    var activeLoginPlugin by remember { mutableStateOf<com.azusachino.latte.plugin.SitePlugin?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()

    LatteTheme(themeMode = themeMode, palette = palette) {
        Box(modifier = modifier.fillMaxSize()) {
            BackHandler(enabled = navigation.screens.size > 1) {
                navigation = navigation.pop()
            }

            AnimatedContent(
                targetState = navigation.current,
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
                saveableStateHolder.SaveableStateProvider(screen.stateKey) {
                    when (screen) {
                        is Screen.Explore, is Screen.AuthorWorks -> {
                            ExploreScreen(
                                viewModel = exploreViewModel,
                                handleSearchBack = screen is Screen.Explore,
                                sitePlugin = yandePlugin,
                                pixivPlugin = pixivPlugin,
                                onPostClick = { index ->
                                    navigation = navigation.push(
                                        Screen.Detail(
                                            posts = exploreViewModel.uiState.value.posts,
                                            initialIndex = index,
                                        ),
                                    )
                                },
                                onOpenSettings = {
                                    navigation = navigation.push(Screen.Settings)
                                },
                                onRequireLogin = { plugin ->
                                    activeLoginPlugin = plugin
                                },
                            )
                        }
                        is Screen.Detail -> {
                            DetailScreen(
                                posts = screen.posts,
                                initialIndex = screen.initialIndex,
                                downloadManager = downloadManager,
                                pluginManager = sitePluginManager,
                                onBack = {
                                    navigation = navigation.pop()
                                },
                                onTagClick = { tag ->
                                    exploreViewModel.search(tag)
                                    navigation = navigation.root()
                                },
                                onAuthorClick = { post ->
                                    if (post.siteId == "pixiv" && post.authorId != null) {
                                        exploreViewModel.loadPixivUserWorks(post.authorId, post.author.orEmpty())
                                    } else {
                                        post.author?.takeIf(String::isNotBlank)?.let {
                                            exploreViewModel.search("user:$it")
                                        }
                                    }
                                    navigation = navigation.push(Screen.AuthorWorks)
                                },
                                onRequireLogin = { plugin ->
                                    activeLoginPlugin = plugin
                                },
                            )
                        }
                        is Screen.Settings -> {
                            SettingsScreen(
                                preferences = exploreViewModel.preferences,
                                onBack = {
                                    navigation = navigation.pop()
                                },
                                onOpenAccountManager = {
                                    navigation = navigation.push(Screen.AccountManager)
                                },
                            )
                        }
                        is Screen.AccountManager -> {
                            com.azusachino.latte.ui.account.AccountManagerScreen(
                                pluginManager = sitePluginManager,
                                onBack = {
                                    navigation = navigation.pop()
                                },
                            )
                        }
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
