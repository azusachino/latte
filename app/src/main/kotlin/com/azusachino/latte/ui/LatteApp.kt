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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.azusachino.latte.plugin.storage.SecurePluginStorage
import com.azusachino.latte.plugin.konachan.KonachanPlugin
import com.azusachino.latte.plugin.yande.YandePlugin
import com.azusachino.latte.data.network.PixivOAuthClient
import com.azusachino.latte.plugin.pixiv.PixivPlugin
import com.azusachino.latte.plugin.SitePluginManager
import com.azusachino.latte.ui.account.AccountManagerScreen
import com.azusachino.latte.ui.account.PluginLoginDialog
import com.azusachino.latte.data.network.OkHttpProvider
import com.azusachino.latte.data.network.PixivOAuthConfiguration
import com.azusachino.latte.plugin.SitePlugin
import com.azusachino.latte.BuildConfig
import com.azusachino.latte.data.model.Post
import androidx.lifecycle.viewmodel.compose.viewModel
import com.azusachino.latte.data.download.DownloadManager
import com.azusachino.latte.plugin.PlatformCapability
import com.azusachino.latte.plugin.PlatformId
import com.azusachino.latte.ui.common.ToastHost
import com.azusachino.latte.ui.common.ToastManager
import com.azusachino.latte.ui.explore.FavoriteTagsScreen
import com.azusachino.latte.ui.detail.DetailScreen
import com.azusachino.latte.ui.explore.ExploreScreen
import com.azusachino.latte.ui.explore.ExploreViewModel
import com.azusachino.latte.ui.settings.SettingsScreen
import com.azusachino.latte.ui.theme.LattePalette
import com.azusachino.latte.ui.theme.LatteTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun LatteApp(
    exploreViewModel: ExploreViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloadManager = remember { DownloadManager(context) }
    val themeMode by exploreViewModel.preferences.themeMode.collectAsStateWithLifecycle()
    val exploreUiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val palette = when (exploreUiState.platform) {
        PlatformId.PIXIV -> LattePalette.PIXIV
        PlatformId.YANDE -> LattePalette.YANDE
        PlatformId.KONACHAN -> LattePalette.KONACHAN
    }

    val pluginStorage = remember { SecurePluginStorage(context) }
    val yandePlugin = remember {
        YandePlugin(pluginStorage, OkHttpProvider.client, OkHttpProvider.cookieJar)
    }
    val pixivOAuthClient = remember {
        PixivOAuthClient(
            httpClient = OkHttpProvider.client,
            configuration = PixivOAuthConfiguration.pixivAndroid(
                clientId = BuildConfig.PIXIV_OAUTH_CLIENT_ID.takeIf(String::isNotBlank),
                clientSecret = BuildConfig.PIXIV_OAUTH_CLIENT_SECRET.takeIf(String::isNotBlank),
            ),
        )
    }
    val pixivPlugin = remember {
        PixivPlugin(
            storage = pluginStorage,
            httpClient = OkHttpProvider.client,
            oauthClient = pixivOAuthClient,
        )
    }
    val konachanPlugin = remember {
        KonachanPlugin(pluginStorage, OkHttpProvider.client, OkHttpProvider.cookieJar)
    }
    val sitePluginManager = remember { SitePluginManager(listOf(yandePlugin, konachanPlugin, pixivPlugin)) }

    LaunchedEffect(yandePlugin, konachanPlugin, pixivPlugin) {
        exploreViewModel.configurePlugin(yandePlugin)
        exploreViewModel.configurePlugin(konachanPlugin)
        exploreViewModel.configurePlugin(pixivPlugin)
    }

    var navigation by remember { mutableStateOf(ScreenStack()) }
    var activeLoginPlugin by remember { mutableStateOf<SitePlugin?>(null) }
    val saveableStateHolder = rememberSaveableStateHolder()

    fun restoreFeedForDetail(screen: Screen.Detail) {
        val state = exploreViewModel.uiState.value
        when {
            screen.parentAuthorId != null -> {
                if (state.pixivAuthorId != screen.parentAuthorId || !state.isSearch) {
                    exploreViewModel.loadPixivUserWorks(
                        screen.parentAuthorId,
                        screen.parentAuthorName.orEmpty(),
                    )
                }
            }
            screen.parentSearchTags != null -> {
                val activeTags = if (state.supportsUserFeeds) state.pixivSearchTags else state.searchTags
                if (activeTags != screen.parentSearchTags || !state.isSearch) {
                    exploreViewModel.search(screen.parentSearchTags)
                }
            }
            else -> {
                if (state.isSearch) {
                    exploreViewModel.clearSearch()
                }
            }
        }
    }

    fun popNavigation() {
        navigation = navigation.popAndRestoreDetailFeed { targetDetail ->
            restoreFeedForDetail(targetDetail)
        }
    }

    LaunchedEffect(navigation.current) {
        when (val screen = navigation.current) {
            is Screen.Explore -> Unit
            is Screen.AuthorWorks -> {
                val state = exploreViewModel.uiState.value
                if (screen.authorId != null && (state.pixivAuthorId != screen.authorId || !state.isSearch)) {
                    exploreViewModel.loadPixivUserWorks(screen.authorId, screen.authorName)
                } else if (screen.authorId == null && screen.authorName.isNotBlank() && (!state.isSearch || state.searchTags != "user:${screen.authorName}")) {
                    exploreViewModel.search("user:${screen.authorName}")
                }
            }
            is Screen.TagSearch -> {
                val state = exploreViewModel.uiState.value
                val activeTags = if (state.supportsUserFeeds) state.pixivSearchTags else state.searchTags
                if (activeTags != screen.query || !state.isSearch) {
                    exploreViewModel.search(screen.query)
                }
            }
            is Screen.Detail -> {
                restoreFeedForDetail(screen)
            }
            else -> Unit
        }
    }

    LatteTheme(themeMode = themeMode, palette = palette) {
        // Root content the toast pill blurs; screens keep their own sources inside it.
        val toastHazeState = rememberHazeState()
        Box(modifier = modifier.fillMaxSize()) {
            BackHandler(enabled = navigation.size > 1) {
                popNavigation()
            }

            AnimatedContent(
                targetState = navigation,
                label = "ScreenTransition",
                transitionSpec = {
                    if (targetState.size >= initialState.size) {
                        (slideInHorizontally(screenSlideSpec) { width -> width } + fadeIn())
                            .togetherWith(slideOutHorizontally(screenSlideSpec) { width -> -width / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally(screenSlideSpec) { width -> -width / 3 } + fadeIn())
                            .togetherWith(slideOutHorizontally(screenSlideSpec) { width -> width } + fadeOut())
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(toastHazeState),
            ) { targetNav ->
                val screen = targetNav.current
                saveableStateHolder.SaveableStateProvider(screen.stateKey) {
                    when (screen) {
                        is Screen.Explore, is Screen.AuthorWorks, is Screen.TagSearch -> {
                            ExploreScreen(
                                viewModel = exploreViewModel,
                                downloadManager = downloadManager,
                                title = when (screen) {
                                    is Screen.AuthorWorks -> screen.authorName.ifBlank { null }
                                    is Screen.TagSearch -> screen.query
                                    else -> null
                                },
                                handleSearchBack = screen is Screen.Explore,
                                onBack = if (screen is Screen.AuthorWorks || screen is Screen.TagSearch) {
                                    { popNavigation() }
                                } else {
                                    null
                                },
                                sitePlugin = yandePlugin,
                                pixivPlugin = pixivPlugin,
                                platformPlugins = sitePluginManager.byPlatform(),
                                onPostClick = { index ->
                                    val state = exploreViewModel.uiState.value
                                    val (parentSearchTags, parentAuthorId, parentAuthorName) = when {
                                        state.pixivAuthorId != null -> Triple(null, state.pixivAuthorId, state.pixivAuthorName)
                                        state.isSearch -> Triple(
                                            if (state.supportsUserFeeds) state.pixivSearchTags else state.searchTags,
                                            null,
                                            null,
                                        )
                                        else -> Triple(null, null, null)
                                    }
                                    navigation = navigation.push(
                                        Screen.Detail(
                                            posts = state.posts,
                                            initialIndex = index,
                                            parentSearchTags = parentSearchTags,
                                            parentAuthorId = parentAuthorId,
                                            parentAuthorName = parentAuthorName,
                                        ),
                                    )
                                },
                                onOpenSettings = {
                                    navigation = navigation.push(Screen.Settings)
                                },
                                onOpenFavoriteTags = {
                                    navigation = navigation.push(Screen.FavoriteTags)
                                },
                                onRequireLogin = { plugin ->
                                    activeLoginPlugin = plugin
                                },
                            )
                        }
                        is Screen.Detail -> {
                            val postsToDisplay = detailPostsFor(screen, exploreUiState.posts)
                            DetailScreen(
                                posts = postsToDisplay,
                                initialIndex = screen.initialIndex,
                                initialPageIndex = screen.initialPageIndex,
                                downloadManager = downloadManager,
                                pluginManager = sitePluginManager,
                                favoriteTags = exploreUiState.favoriteTags,
                                onLoadMore = {
                                    exploreViewModel.loadMoreActiveFeed()
                                },
                                onToggleFavoriteTag = { tag, platform ->
                                    exploreViewModel.toggleFavoriteTag(tag, platform)
                                },
                                onBack = {
                                    popNavigation()
                                },
                                onTagClick = { tag, postIndex, pageIndex ->
                                    exploreViewModel.search(tag)
                                    navigation = navigation
                                        .replaceTop(
                                            screen.copy(
                                                posts = postsToDisplay,
                                                initialIndex = postIndex,
                                                initialPageIndex = pageIndex,
                                            ),
                                        )
                                        .push(Screen.TagSearch(query = tag))
                                },
                                onAuthorClick = { post, postIndex, pageIndex ->
                                    if (PlatformCapability.USER_FEED in post.platform.capabilities && post.authorId != null) {
                                        exploreViewModel.loadPixivUserWorks(post.authorId, post.author.orEmpty())
                                    } else {
                                        post.author?.takeIf(String::isNotBlank)?.let {
                                            exploreViewModel.search("user:$it")
                                        }
                                    }
                                    navigation = navigation
                                        .replaceTop(
                                            screen.copy(
                                                posts = postsToDisplay,
                                                initialIndex = postIndex,
                                                initialPageIndex = pageIndex,
                                            ),
                                        )
                                        .push(
                                            Screen.AuthorWorks(
                                                authorId = post.authorId,
                                                authorName = post.author.orEmpty(),
                                            ),
                                        )
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
                                    popNavigation()
                                },
                                onOpenAccountManager = {
                                    navigation = navigation.push(Screen.AccountManager)
                                },
                            )
                        }
                        is Screen.FavoriteTags -> {
                            FavoriteTagsScreen(
                                viewModel = exploreViewModel,
                                onBack = { popNavigation() },
                                onOpenTag = { favorite ->
                                    exploreViewModel.selectPlatform(favorite.platform)
                                    exploreViewModel.search(favorite.tag)
                                    navigation = navigation.push(Screen.TagSearch(query = favorite.tag))
                                },
                            )
                        }
                        is Screen.AccountManager -> {
                            AccountManagerScreen(
                                pluginManager = sitePluginManager,
                                onBack = {
                                    popNavigation()
                                },
                            )
                        }
                    }
                }
            }

            // Global Login Dialog
            activeLoginPlugin?.let { plugin ->
                PluginLoginDialog(
                    plugin = plugin,
                    onDismissRequest = { activeLoginPlugin = null },
                    onLoginSuccess = {
                        activeLoginPlugin = null
                        ToastManager.showSuccess("Signed in to ${plugin.name}")
                    },
                )
            }

            // Notification stack over all screens
            ToastHost(hazeState = toastHazeState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
