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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.azusachino.latte.data.model.PopularPeriod
import com.azusachino.latte.data.model.Post
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onPostClick: (index: Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    val popularGridState = rememberLazyStaggeredGridState()
    val newestGridState = rememberLazyStaggeredGridState()
    val searchGridState = rememberLazyStaggeredGridState()
    val pagerState = rememberPagerState(initialPage = uiState.selectedTab) { 2 }
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var isSearchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(uiState.searchTags) }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(pagerState.currentPage)
    }

    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    BackHandler(enabled = uiState.searchTags.isNotBlank()) {
        viewModel.clearSearch()
    }

    LaunchedEffect(uiState.searchTags) {
        searchQuery = uiState.searchTags
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.searchTags.isNotBlank()) uiState.searchTags else "Latte",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                    },
                    navigationIcon = {
                        if (uiState.searchTags.isNotBlank()) {
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

                // Expandable Search Bar
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
                            placeholder = { Text("Search tags, e.g. genshin_impact") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    viewModel.search(searchQuery)
                                },
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        viewModel.clearSearch()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                // Front page tabs: Popular vs Newest (hidden during search)
                if (uiState.searchTags.isBlank()) {
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Tab(
                            selected = pagerState.currentPage == 0,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            },
                            text = {
                                Text(
                                    text = "Popular",
                                    fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                        Tab(
                            selected = pagerState.currentPage == 1,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            },
                            text = {
                                Text(
                                    text = "Newest",
                                    fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
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
                    feed = uiState.searchFeed,
                    gridState = searchGridState,
                    columnCount = columnCount,
                    onPostClick = onPostClick,
                    onLoadMore = { viewModel.loadMoreSearch() },
                    onRetry = { viewModel.search(uiState.searchTags) },
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    if (page == 0) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            PopularControls(
                                period = uiState.popularPeriod,
                                date = uiState.popularDate,
                                onPeriodSelect = { viewModel.selectPopularPeriod(it) },
                                onShiftDate = { viewModel.shiftPopularDate(it) },
                                onPickDate = { viewModel.setPopularDate(it) },
                            )
                            FeedGrid(
                                feed = uiState.popularFeed,
                                gridState = popularGridState,
                                columnCount = columnCount,
                                onPostClick = onPostClick,
                                onLoadMore = { viewModel.loadMorePopular() },
                                onRetry = { viewModel.loadPopularInitial() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        FeedGrid(
                            feed = uiState.newestFeed,
                            gridState = newestGridState,
                            columnCount = columnCount,
                            onPostClick = onPostClick,
                            onLoadMore = { viewModel.loadMoreNewest() },
                            onRetry = { viewModel.loadNewestInitial() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedGrid(
    feed: FeedState,
    gridState: LazyStaggeredGridState,
    columnCount: Int,
    onPostClick: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
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

    when {
        feed.isLoading && feed.posts.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        feed.error != null && feed.posts.isEmpty() -> {
            Box(
                modifier = modifier
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
                modifier = modifier.fillMaxSize(),
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
