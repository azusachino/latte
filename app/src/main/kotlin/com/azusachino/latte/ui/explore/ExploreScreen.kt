package com.azusachino.latte.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.azusachino.latte.data.model.PoolSummary
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import com.azusachino.latte.plugin.SitePlugin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val YANDE_ACCENT = Color(0xFF3F6F8F)
private val PIXIV_ACCENT = Color(0xFF0096FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onPostClick: (index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    sitePlugin: SitePlugin? = null,
    pixivPlugin: SitePlugin? = null,
    onRequireLogin: (SitePlugin) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    val popularGridState = rememberLazyStaggeredGridState()
    val newestGridState = rememberLazyStaggeredGridState()
    val favoritesGridState = rememberLazyStaggeredGridState()
    val searchGridState = rememberLazyStaggeredGridState()
    val pixivPopularGridState = rememberLazyStaggeredGridState()
    val pixivFollowedGridState = rememberLazyStaggeredGridState()
    val pixivFavoritesGridState = rememberLazyStaggeredGridState()
    val pixivSearchGridState = rememberLazyStaggeredGridState()
    val tabCount = if (uiState.isPixiv) 3 else 4
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab.coerceAtMost(tabCount - 1)) { tabCount }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(uiState.activeSearchTags) }
    var platformMenuOpen by remember { mutableStateOf(false) }
    val platformAccent = MaterialTheme.colorScheme.primary
    val pixivIsLoggedIn = pixivPlugin?.let { plugin ->
        val isLoggedIn by plugin.isLoggedInFlow.collectAsState(initial = plugin.isLoggedIn)
        isLoggedIn
    } ?: false

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
        searchQuery = if (!uiState.isPixiv && pagerState.currentPage == 3) uiState.poolsFeed.query else uiState.activeSearchTags
    }

    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    BackHandler(enabled = uiState.isSearch) {
        viewModel.clearSearch()
    }

    LaunchedEffect(uiState.activeSearchTags) {
        searchQuery = uiState.activeSearchTags
    }

    LaunchedEffect(pixivIsLoggedIn) {
        if (pixivIsLoggedIn) viewModel.retryPixivAfterAuthentication()
    }

    LaunchedEffect(isSearchExpanded, uiState.isPixiv, searchQuery) {
        if (isSearchExpanded && uiState.isPixiv) {
            delay(250)
            viewModel.loadPixivSearchSupport(searchQuery)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { platformMenuOpen = true },
                            ) {
                                Text(
                                    text = uiState.activePoolName
                                        ?: if (uiState.isSearch) uiState.activeSearchTags
                                        else "Latte",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = platformAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                PlatformLogo(
                                    label = if (uiState.isPixiv) "p" else "y",
                                    color = platformAccent,
                                    contentDescription = if (uiState.isPixiv) {
                                        "Current platform: Pixiv"
                                    } else {
                                        "Current platform: Yande"
                                    },
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            DropdownMenu(
                                expanded = platformMenuOpen,
                                onDismissRequest = { platformMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        PlatformLogo(
                                            label = "y",
                                            color = YANDE_ACCENT,
                                            contentDescription = "Yande platform",
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Yande",
                                            color = if (uiState.platform == ExplorePlatform.YANDE) YANDE_ACCENT
                                            else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (uiState.platform == ExplorePlatform.YANDE) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
                                    trailingIcon = {
                                        if (uiState.platform == ExplorePlatform.YANDE) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = YANDE_ACCENT,
                                            )
                                        }
                                    },
                                    onClick = {
                                        platformMenuOpen = false
                                        viewModel.selectPlatform(ExplorePlatform.YANDE)
                                    },
                                )
                                DropdownMenuItem(
                                    leadingIcon = {
                                        PlatformLogo(
                                            label = "p",
                                            color = PIXIV_ACCENT,
                                            contentDescription = "Pixiv platform",
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Pixiv",
                                            color = if (uiState.platform == ExplorePlatform.PIXIV) PIXIV_ACCENT
                                            else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (uiState.platform == ExplorePlatform.PIXIV) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    },
                                    trailingIcon = {
                                        if (uiState.platform == ExplorePlatform.PIXIV) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = PIXIV_ACCENT,
                                            )
                                        }
                                    },
                                    onClick = {
                                        platformMenuOpen = false
                                        viewModel.selectPlatform(ExplorePlatform.PIXIV)
                                    },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (uiState.isSearch) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to discovery",
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    actions = {
                        // Search toggle
                        IconButton(onClick = { isSearchExpanded = !isSearchExpanded }) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search",
                            )
                        }

                        // Moebooru-parity column toggle button: cycles 1 -> 2 -> 3 -> 1
                        IconButton(onClick = { viewModel.cycleColumns() }) {
                            val columnIcon = when (columnCount) {
                                1 -> Icons.Default.ViewAgenda
                                2 -> Icons.Default.GridView
                                else -> Icons.Default.ViewColumn
                            }
                            Icon(
                                imageVector = columnIcon,
                                contentDescription = "Columns ($columnCount)",
                            )
                        }

                        // Settings button
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    },
                )

                // Expandable Search Bar -- searches pools while the Pools tab is
                // active and no pool is open yet, otherwise searches post tags.
                val isPoolsSearch = !uiState.isPixiv && pagerState.currentPage == 3 && !uiState.isSearch

                AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(if (isPoolsSearch) "Search pools, e.g. genshin_impact" else "Search tags, e.g. genshin_impact")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    if (isPoolsSearch) {
                                        viewModel.loadPoolsInitial(searchQuery)
                                    } else {
                                        viewModel.search(searchQuery)
                                    }
                                },
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        if (isPoolsSearch) {
                                            viewModel.loadPoolsInitial("")
                                        } else {
                                            viewModel.clearSearch()
                                        }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                        if (uiState.isPixiv && isSearchExpanded) {
                            val supportTags = if (searchQuery.isBlank()) {
                                uiState.pixivTrendingTags
                            } else {
                                uiState.pixivSearchSuggestions
                            }
                            if (supportTags.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 64.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    supportTags.take(8).forEach { tag ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                searchQuery = tag
                                                viewModel.searchPixiv(tag)
                                            },
                                            label = { Text(tag) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Front page tabs: Popular / Newest / Favorites / Pools (hidden during search)
                if (!uiState.isSearch) {
                    val tabTitles = if (uiState.isPixiv) {
                        listOf("Popular", "Following", "Favorites")
                    } else {
                        listOf("Popular", "Newest", "Favorites", "Pools")
                    }
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.isSearch) {
                FeedGrid(
                    feed = if (uiState.isPixiv) uiState.pixivSearchFeed else uiState.searchFeed,
                    gridState = if (uiState.isPixiv) pixivSearchGridState else searchGridState,
                    columnCount = columnCount,
                    onPostClick = onPostClick,
                    onLoadMore = { viewModel.loadMoreSearch() },
                    onRetry = { viewModel.search(uiState.activeSearchTags, uiState.activePoolName) },
                    onRefresh = { viewModel.refreshSearch() },
                    onRequireLogin = if (uiState.isPixiv && pixivPlugin != null) {
                        { onRequireLogin(pixivPlugin) }
                    } else {
                        null
                    },
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                if (!uiState.isPixiv) {
                                    PopularControls(
                                        period = uiState.popularPeriod,
                                        date = uiState.popularDate,
                                        onPeriodSelect = { viewModel.selectPopularPeriod(it) },
                                        onShiftDate = { viewModel.shiftPopularDate(it) },
                                        onPickDate = { viewModel.setPopularDate(it) },
                                    )
                                }
                                FeedGrid(
                                    feed = if (uiState.isPixiv) uiState.pixivPopularFeed else uiState.popularFeed,
                                    gridState = if (uiState.isPixiv) pixivPopularGridState else popularGridState,
                                    columnCount = columnCount,
                                    onPostClick = onPostClick,
                                    onLoadMore = { if (uiState.isPixiv) viewModel.loadMorePixiv() else viewModel.loadMorePopular() },
                                    onRetry = { if (uiState.isPixiv) viewModel.loadPixivInitial() else viewModel.loadPopularInitial() },
                                    onRefresh = { if (uiState.isPixiv) viewModel.refreshPixiv() else viewModel.refreshPopular() },
                                    onRequireLogin = if (uiState.isPixiv && pixivPlugin != null) {
                                        { onRequireLogin(pixivPlugin) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        1 -> {
                            if (uiState.isPixiv) {
                                PersonalFeedContent(
                                    plugin = pixivPlugin,
                                    feed = uiState.pixivFollowedFeed,
                                    label = "Sign in to see followed updates",
                                    gridState = if (uiState.isPixiv) pixivFollowedGridState else newestGridState,
                                    columnCount = columnCount,
                                    onPostClick = onPostClick,
                                    onRequireLogin = onRequireLogin,
                                    onLoadMore = { viewModel.loadMorePixiv() },
                                    onRetry = { viewModel.loadPixivInitial() },
                                    onRefresh = { viewModel.refreshPixiv() },
                                )
                            } else {
                                FeedGrid(
                                    feed = uiState.newestFeed,
                                    gridState = newestGridState,
                                    columnCount = columnCount,
                                    onPostClick = onPostClick,
                                    onLoadMore = { viewModel.loadMoreNewest() },
                                    onRetry = { viewModel.loadNewestInitial() },
                                    onRefresh = { viewModel.refreshNewest() },
                                )
                            }
                        }
                        2 -> {
                            PersonalFeedContent(
                                plugin = if (uiState.isPixiv) pixivPlugin else sitePlugin,
                                feed = if (uiState.isPixiv) uiState.pixivFavoritesFeed else uiState.favoritesFeed,
                                label = if (uiState.isPixiv) "Sign in to see Pixiv favorites" else "Sign in to see your favorites",
                                gridState = if (uiState.isPixiv) pixivFavoritesGridState else favoritesGridState,
                                columnCount = columnCount,
                                onPostClick = onPostClick,
                                onRequireLogin = onRequireLogin,
                                onLoadMore = {
                                    if (uiState.isPixiv) viewModel.loadMorePixiv()
                                    else sitePlugin?.getDisplayUsername()?.let(viewModel::loadMoreFavorites)
                                },
                                onRetry = {
                                    if (uiState.isPixiv) viewModel.loadPixivInitial()
                                    else sitePlugin?.getDisplayUsername()?.let(viewModel::loadFavoritesInitial)
                                },
                                onRefresh = {
                                    if (uiState.isPixiv) viewModel.refreshPixiv()
                                    else sitePlugin?.getDisplayUsername()?.let(viewModel::refreshFavorites)
                                },
                            )
                        }
                        else -> {
                            PoolsTabContent(
                                feed = uiState.poolsFeed,
                                covers = uiState.poolCovers,
                                onQueryChange = { query -> viewModel.loadPoolsInitial(query) },
                                onLoadMore = { viewModel.loadMorePools() },
                                onRefresh = { viewModel.refreshPools() },
                                onPoolClick = { pool -> viewModel.openPool(pool) },
                                onNeedCover = { poolId -> viewModel.loadPoolCover(poolId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformLogo(
    label: String,
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier.size(32.dp),
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(modifier)
            .clip(RoundedCornerShape(9.dp))
            .background(color)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedGrid(
    feed: FeedState,
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    onPostClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onRequireLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 10
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = feed.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            feed.isLoading && feed.posts.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            feed.error != null && feed.posts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = feed.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (feed.authRequired && onRequireLogin != null) {
                            Button(onClick = onRequireLogin) {
                                Text("Sign in")
                            }
                        } else {
                            IconButton(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                            }
                        }
                    }
                }
            }
            feed.posts.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No illustrations available",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                    }
                }
            }
            else -> {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(columnCount),
                    state = gridState,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalItemSpacing = 4.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = feed.posts,
                        key = { _, post -> post.id },
                    ) { index, post ->
                        PostGridItem(
                            post = post,
                            onClick = { onPostClick(index) },
                        )
                    }

                    if (feed.isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonalFeedContent(
    plugin: SitePlugin?,
    feed: FeedState,
    label: String,
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    onPostClick: (Int) -> Unit,
    onRequireLogin: (SitePlugin) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (plugin == null) return

    val isLoggedIn by plugin.isLoggedInFlow.collectAsState(initial = plugin.isLoggedIn)
    val username = plugin.getDisplayUsername()
    val needsUsername = plugin.id != "pixiv"

    if (!isLoggedIn || (needsUsername && username.isNullOrBlank())) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onRequireLogin(plugin) }) {
                    Text("Sign in")
                }
            }
        }
        return
    }

    LaunchedEffect(isLoggedIn, username) {
        onRetry()
    }

    FeedGrid(
        feed = feed,
        gridState = gridState,
        columnCount = columnCount,
        onPostClick = onPostClick,
        onLoadMore = onLoadMore,
        onRetry = onRetry,
        onRefresh = onRefresh,
        onRequireLogin = { onRequireLogin(plugin) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PoolsTabContent(
    feed: PoolListState,
    covers: Map<Long, String>,
    onQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onPoolClick: (PoolSummary) -> Unit,
    onNeedCover: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LaunchedEffect(Unit) {
        onQueryChange(feed.query)
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            feed.isLoading && feed.pools.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            feed.error != null && feed.pools.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = feed.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        IconButton(onClick = { onQueryChange(feed.query) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retry")
                        }
                    }
                }
            }
            feed.pools.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No pools found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = feed.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = feed.pools, key = { it.id }) { pool ->
                            PoolListItem(
                                pool = pool,
                                coverUrl = covers[pool.id],
                                onClick = { onPoolClick(pool) },
                                onNeedCover = { onNeedCover(pool.id) },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        if (feed.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PoolListItem(
    pool: PoolSummary,
    coverUrl: String?,
    onClick: () -> Unit,
    onNeedCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(pool.id) {
        if (coverUrl == null) onNeedCover()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pool.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "#${pool.id} · ${pool.postCount} post${if (pool.postCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!pool.isPublic) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Private pool",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostGridItem(
    post: Post,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        AsyncImage(
            model = post.previewUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(post.aspectRatio.coerceIn(0.4f, 2.5f)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PopularControls(
    period: PopularPeriod,
    date: LocalDate,
    onPeriodSelect: (PopularPeriod) -> Unit,
    onShiftDate: (Int) -> Unit,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val canShiftForward = date.isBefore(today)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Period filter chips: Day, Week, Month, Year
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PopularPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { onPeriodSelect(p) },
                    label = {
                        Text(
                            when (p) {
                                PopularPeriod.DAY -> "Day"
                                PopularPeriod.WEEK -> "Week"
                                PopularPeriod.MONTH -> "Month"
                                PopularPeriod.YEAR -> "Year"
                            }
                        )
                    },
                )
            }
        }

        // Date range navigation: [<] [📅 Date / Range] [>]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onShiftDate(-1) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous period",
                )
            }

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showDatePicker = true },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Pick date",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatPopularWindow(period, date),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            IconButton(
                onClick = { onShiftDate(1) },
                enabled = canShiftForward,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next period",
                    tint = if (canShiftForward) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onPickDate(selected)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(
                state = datePickerState,
                headline = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    val formatted = if (selectedMillis != null) {
                        Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } else {
                        "Select date"
                    }
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                },
            )
        }
    }
}

private fun formatPopularWindow(period: PopularPeriod, anchor: LocalDate): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    return when (period) {
        PopularPeriod.DAY -> anchor.format(dateFormatter)
        PopularPeriod.WEEK -> {
            val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
            val end = start.plusDays(6)
            "${start.format(dateFormatter)} – ${end.format(dateFormatter)}"
        }
        PopularPeriod.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            val end = anchor.withDayOfMonth(anchor.lengthOfMonth())
            "${start.format(dateFormatter)} – ${end.format(dateFormatter)}"
        }
        PopularPeriod.YEAR -> {
            val start = anchor.withDayOfYear(1)
            val end = anchor.withDayOfYear(anchor.lengthOfYear())
            "${start.format(dateFormatter)} – ${end.format(dateFormatter)}"
        }
    }
}
