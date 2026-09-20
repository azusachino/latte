import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';

import '../../design/latte_toast.dart';
import '../../domain/explore_state.dart';
import '../../domain/failure.dart';
import '../../domain/popular_query.dart';
import '../../domain/post.dart';
import '../../sites/site_adapter.dart';
import '../download/download_service.dart';
import 'explore_controller.dart';
import 'search_view.dart';

class ExploreScreen extends StatefulWidget {
  const ExploreScreen({
    required this.controller,
    this.columnCount,
    this.onSearch,
    this.onSettings,
    super.key,
  });

  final ExploreController controller;
  final int? columnCount;
  final VoidCallback? onSearch;
  final VoidCallback? onSettings;

  @override
  State<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends State<ExploreScreen> {
  late final ScrollController _scrollController;
  late final GlobalKey<SearchViewState> _searchKey;
  var _wasDetail = false;
  var _savedScrollOffset = 0.0;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _searchKey = GlobalKey<SearchViewState>();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.controller.state.status == ExploreStatus.initial) {
        widget.controller.loadPopular();
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.controller,
      builder: (context, _) {
        final state = widget.controller.state;
        final isDetail = switch (state.status) {
          ExploreStatus.detail ||
          ExploreStatus.detailLoading ||
          ExploreStatus.detailFailure => true,
          _ => false,
        };
        if (isDetail && !_wasDetail && _scrollController.hasClients) {
          _savedScrollOffset = _scrollController.offset;
        }
        if (!isDetail && _wasDetail) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (!mounted || !_scrollController.hasClients) return;
            final position = _scrollController.position;
            final offset = _savedScrollOffset
                .clamp(0.0, position.maxScrollExtent)
                .toDouble();
            _scrollController.jumpTo(offset);
          });
        }
        _wasDetail = isDetail;
        final child = isDetail
            ? _DetailScaffold(controller: widget.controller, state: state)
            : _ExploreScaffold(
                controller: widget.controller,
                state: state,
                columnCount: widget.columnCount,
                onSearch:
                    widget.onSearch ?? () => _searchKey.currentState?.open(),
                onSettings: widget.onSettings,
                searchView: SearchView(
                  key: _searchKey,
                  controller: widget.controller,
                  showStatus: false,
                  showBar: false,
                ),
                scrollController: _scrollController,
              );
        final reducedMotion = MediaQuery.disableAnimationsOf(context);
        return PopScope<void>(
          canPop: !isDetail,
          onPopInvokedWithResult: (didPop, _) {
            if (!didPop && isDetail) widget.controller.closeDetail();
          },
          child: AnimatedSwitcher(
            duration: reducedMotion
                ? Duration.zero
                : const Duration(milliseconds: 180),
            child: KeyedSubtree(key: ValueKey(isDetail), child: child),
          ),
        );
      },
    );
  }
}

class _ExploreScaffold extends StatelessWidget {
  const _ExploreScaffold({
    required this.controller,
    required this.state,
    required this.columnCount,
    required this.scrollController,
    required this.searchView,
    this.onSearch,
    this.onSettings,
  });

  final ExploreController controller;
  final ExploreState state;
  final int? columnCount;
  final ScrollController scrollController;
  final SearchView searchView;
  final VoidCallback? onSearch;
  final VoidCallback? onSettings;

  @override
  Widget build(BuildContext context) {
    final popular = _isPopular(state);
    return DefaultTabController(
      key: ValueKey(popular),
      length: 2,
      initialIndex: popular ? 0 : 1,
      child: _ExploreTabScaffold(
        controller: controller,
        state: state,
        columnCount: columnCount,
        scrollController: scrollController,
        searchView: searchView,
        onSearch: onSearch,
        onSettings: onSettings,
      ),
    );
  }
}

class _ExploreTabScaffold extends StatefulWidget {
  const _ExploreTabScaffold({
    required this.controller,
    required this.state,
    required this.columnCount,
    required this.scrollController,
    required this.searchView,
    this.onSearch,
    this.onSettings,
  });

  final ExploreController controller;
  final ExploreState state;
  final int? columnCount;
  final ScrollController scrollController;
  final SearchView searchView;
  final VoidCallback? onSearch;
  final VoidCallback? onSettings;

  @override
  State<_ExploreTabScaffold> createState() => _ExploreTabScaffoldState();
}

class _ExploreTabScaffoldState extends State<_ExploreTabScaffold> {
  double _horizontalDragDelta = 0;

  @override
  Widget build(BuildContext context) {
    final popular = _isPopular(widget.state);
    final isSearch = widget.state.query?.source == PostQuerySource.tagSearch;
    return Scaffold(
      appBar: AppBar(
        title: Text(isSearch ? 'Search results' : 'Latte'),
        leading: isSearch
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                tooltip: 'Back to discovery',
                onPressed: widget.controller.clearSearch,
              )
            : null,
        actions: [
          IconButton(
            onPressed: widget.onSearch,
            icon: const Icon(Icons.search),
            tooltip: 'Search',
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
          IconButton(
            onPressed: widget.onSettings,
            icon: const Icon(Icons.settings_outlined),
            tooltip: 'Settings',
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
        ],
        bottom: isSearch
            ? null
            : TabBar(
                key: const ValueKey('explore-tabs'),
                tabs: const [
                  Tab(text: 'Popular'),
                  Tab(text: 'Newest'),
                ],
                onTap: (index) =>
                    _selectExploreTab(context, widget.controller, index),
              ),
      ),
      body: GestureDetector(
        onHorizontalDragStart: (_) => _horizontalDragDelta = 0,
        onHorizontalDragUpdate: (details) {
          _horizontalDragDelta += details.primaryDelta ?? 0;
        },
        onHorizontalDragEnd: (details) {
          final velocity = details.primaryVelocity ?? 0;
          final direction = velocity.abs() > 300
              ? velocity
              : _horizontalDragDelta;
          if (isSearch) {
            if (direction > 80) {
              widget.controller.clearSearch();
            }
            _horizontalDragDelta = 0;
            return;
          }
          final tabController = DefaultTabController.of(context);
          if (direction < -80 && tabController.index == 0) {
            _selectExploreTab(context, widget.controller, 1);
          } else if (direction > 80 && tabController.index == 1) {
            _selectExploreTab(context, widget.controller, 0);
          }
          _horizontalDragDelta = 0;
        },
        child: LayoutBuilder(
          builder: (context, constraints) {
            final expanded = constraints.maxWidth >= 600;
            final columns = widget.columnCount ?? (expanded ? 4 : 2);
            return KeyedSubtree(
              key: ValueKey(expanded ? 'expanded-explore' : 'compact-explore'),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  widget.searchView,
                  Expanded(
                    child: RepaintBoundary(
                      key: const ValueKey('explore-golden-content'),
                      child: _ExploreBody(
                        controller: widget.controller,
                        state: widget.state,
                        columnCount: columns,
                        scrollController: widget.scrollController,
                      ),
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
      floatingActionButton: popular
          ? FloatingActionButton(
              onPressed: () =>
                  _showPopularPeriodSheet(context, widget.controller),
              tooltip: 'Choose popular period',
              child: const Icon(Icons.calendar_today_outlined),
            )
          : null,
    );
  }
}

void _selectExploreTab(
  BuildContext context,
  ExploreController controller,
  int index,
) {
  final tabController = DefaultTabController.of(context);
  if (tabController.index != index) tabController.animateTo(index);
  if (index == 0) {
    controller.loadPopular();
  } else {
    controller.loadDiscovery();
  }
}

bool _isPopular(ExploreState state) =>
    state.query == null || state.query?.source == PostQuerySource.popular;

Future<void> _showPopularPeriodSheet(
  BuildContext context,
  ExploreController controller,
) => showModalBottomSheet<void>(
  context: context,
  showDragHandle: true,
  builder: (context) => _PopularPeriodSheet(controller: controller),
);

class _PopularPeriodSheet extends StatelessWidget {
  const _PopularPeriodSheet({required this.controller});

  final ExploreController controller;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final query = controller.selectedPopularQuery;
        return SafeArea(
          child: SingleChildScrollView(
            key: const ValueKey('popular-period-sheet'),
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('Popular', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 16),
                SegmentedButton<PopularPeriod>(
                  segments: const [
                    ButtonSegment(value: PopularPeriod.day, label: Text('Day')),
                    ButtonSegment(
                      value: PopularPeriod.week,
                      label: Text('Week'),
                    ),
                    ButtonSegment(
                      value: PopularPeriod.month,
                      label: Text('Month'),
                    ),
                  ],
                  selected: {query.period},
                  onSelectionChanged: (selection) {
                    controller.loadPopular(period: selection.single);
                    Navigator.pop(context);
                  },
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    IconButton(
                      onPressed: () => controller.shiftPopularAnchor(-1),
                      icon: const Icon(Icons.chevron_left),
                      tooltip: 'Previous period',
                      constraints: const BoxConstraints(
                        minWidth: 48,
                        minHeight: 48,
                      ),
                    ),
                    Expanded(
                      child: Text(
                        _popularWindowLabel(query),
                        textAlign: TextAlign.center,
                        semanticsLabel:
                            'Selected period ${_popularWindowLabel(query)}',
                        style: Theme.of(context).textTheme.labelLarge,
                      ),
                    ),
                    IconButton(
                      onPressed: () => controller.shiftPopularAnchor(1),
                      icon: const Icon(Icons.chevron_right),
                      tooltip: 'Next period',
                      constraints: const BoxConstraints(
                        minWidth: 48,
                        minHeight: 48,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}

String _popularWindowLabel(PopularQuery query) {
  final start = _dateLabel(query.window.start);
  final end = _dateLabel(query.window.end);
  return start == end ? start : '$start – $end';
}

String _dateLabel(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-'
    '${value.month.toString().padLeft(2, '0')}-'
    '${value.day.toString().padLeft(2, '0')}';

class _ExploreBody extends StatelessWidget {
  const _ExploreBody({
    required this.controller,
    required this.state,
    required this.columnCount,
    required this.scrollController,
  });

  final ExploreController controller;
  final ExploreState state;
  final int columnCount;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context) {
    return switch (state.status) {
      ExploreStatus.initial ||
      ExploreStatus.initialLoading ||
      ExploreStatus.replacingQuery => const _LoadingState(),
      ExploreStatus.empty => _MessageState(
        title: 'No posts yet',
        action: _retryAction(controller, state),
      ),
      ExploreStatus.noResults => _MessageState(
        title: 'No matches',
        action: _retryAction(controller, state),
      ),
      ExploreStatus.failure => _MessageState(
        title: _failureMessage(state.failure),
        action: _retryAction(controller, state),
      ),
      ExploreStatus.content ||
      ExploreStatus.nextPageLoading ||
      ExploreStatus.nextPageFailure ||
      ExploreStatus.endReached => _GridState(
        controller: controller,
        state: state,
        columnCount: columnCount,
        scrollController: scrollController,
      ),
      ExploreStatus.detailLoading ||
      ExploreStatus.detail ||
      ExploreStatus.detailFailure => const SizedBox.shrink(),
    };
  }
}

VoidCallback _retryAction(ExploreController controller, ExploreState state) {
  final popular = state.query?.popularQuery;
  if (popular == null) return controller.loadDiscovery;
  return () =>
      controller.loadPopular(period: popular.period, anchor: popular.anchor);
}

class _GridState extends StatelessWidget {
  const _GridState({
    required this.controller,
    required this.state,
    required this.columnCount,
    required this.scrollController,
  });

  final ExploreController controller;
  final ExploreState state;
  final int columnCount;
  final ScrollController scrollController;

  @override
  Widget build(BuildContext context) {
    final isLoading = state.status == ExploreStatus.nextPageLoading;
    final hasNextFailure = state.status == ExploreStatus.nextPageFailure;
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollUpdateNotification &&
            notification.metrics.extentAfter < 240 &&
            state.next != null &&
            !isLoading) {
          controller.loadNextPage();
        }
        return false;
      },
      child: LayoutBuilder(
        builder: (context, constraints) {
          final columns = _masonryColumns(state.posts, columnCount);
          return ListView(
            key: const ValueKey('explore-grid'),
            controller: scrollController,
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              Row(
                key: ValueKey('explore-columns-$columnCount'),
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final column in columns)
                    Expanded(
                      child: Padding(
                        padding: EdgeInsets.only(
                          right: column == columns.last ? 0 : 6,
                          left: column == columns.first ? 0 : 6,
                        ),
                        child: Column(
                          children: [
                            for (final post in column) ...[
                              _PostCard(
                                post: post,
                                adapter: controller.adapter,
                                onTap: () =>
                                    controller.openDetail(post.reference),
                              ),
                              const SizedBox(height: 12),
                            ],
                          ],
                        ),
                      ),
                    ),
                ],
              ),
              if (isLoading || hasNextFailure)
                hasNextFailure
                    ? _PageRetry(onPressed: controller.loadNextPage)
                    : const _PageProgress(),
            ],
          );
        },
      ),
    );
  }
}

List<List<PostSummary>> _masonryColumns(
  List<PostSummary> posts,
  int columnCount,
) {
  final columns = List.generate(columnCount, (_) => <PostSummary>[]);
  final heights = List<double>.filled(columnCount, 0);
  for (final post in posts) {
    var shortest = 0;
    for (var index = 1; index < heights.length; index++) {
      if (heights[index] < heights[shortest]) shortest = index;
    }
    columns[shortest].add(post);
    heights[shortest] += 1 / _aspectRatio(post);
  }
  return columns;
}

class _PostCard extends StatelessWidget {
  const _PostCard({
    required this.post,
    required this.adapter,
    required this.onTap,
  });

  final PostSummary post;
  final SiteAdapter adapter;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      container: true,
      label: 'Post ${post.reference.remoteId}',
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: AspectRatio(
            aspectRatio: _aspectRatio(post),
            child: _RemoteArtwork(post: post, adapter: adapter),
          ),
        ),
      ),
    );
  }
}

double _aspectRatio(PostSummary post) {
  final width = post.width;
  final height = post.height;
  if (width == null || height == null || width <= 0 || height <= 0) {
    return 1;
  }
  return width / height;
}

class _RemoteArtwork extends StatefulWidget {
  const _RemoteArtwork({required this.post, required this.adapter});

  final PostSummary post;
  final SiteAdapter adapter;

  @override
  State<_RemoteArtwork> createState() => _RemoteArtworkState();
}

class _RemoteArtworkState extends State<_RemoteArtwork> {
  Future<ResolvedMedia>? _media;
  Future<ResolvedMedia>? _thumbnail;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void didUpdateWidget(covariant _RemoteArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.post.reference != widget.post.reference) {
      _start();
    }
  }

  @override
  Widget build(BuildContext context) {
    final preview = widget.post.preview;
    if (preview == null) {
      return const _ArtworkPlaceholder();
    }
    return FutureBuilder<ResolvedMedia>(
      future: _media,
      builder: (context, snapshot) {
        final source = snapshot.data?.source;
        return FutureBuilder<ResolvedMedia>(
          future: _thumbnail,
          builder: (context, thumbnailSnapshot) {
            final thumbnail = thumbnailSnapshot.data?.source;
            if (source == null && thumbnail == null) {
              return const _ArtworkPlaceholder();
            }
            return SizedBox.expand(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (thumbnail != null)
                    Image(
                      image: _imageProvider(thumbnail),
                      fit: BoxFit.cover,
                      semanticLabel:
                          'Artwork ${widget.post.reference.remoteId}',
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                  if (source != null)
                    Image(
                      image: _imageProvider(source),
                      fit: BoxFit.cover,
                      semanticLabel:
                          'Artwork ${widget.post.reference.remoteId}',
                      frameBuilder:
                          (context, child, frame, wasSynchronouslyLoaded) {
                            if (wasSynchronouslyLoaded || frame != null) {
                              return AnimatedOpacity(
                                opacity: 1,
                                duration: const Duration(milliseconds: 180),
                                child: child,
                              );
                            }
                            return const SizedBox.shrink();
                          },
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  void _start() {
    final preview = widget.post.preview;
    _media = preview == null
        ? null
        : widget.adapter.resolveMedia(widget.post.reference, preview.id);
    _thumbnail = _media;
  }
}

class _ArtworkPlaceholder extends StatelessWidget {
  const _ArtworkPlaceholder();

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: const Center(
        child: SizedBox.square(
          dimension: 18,
          child: DecoratedBox(
            decoration: BoxDecoration(
              border: Border.fromBorderSide(
                BorderSide(color: Colors.black87, width: 2),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _DetailScaffold extends StatelessWidget {
  const _DetailScaffold({required this.controller, required this.state});

  final ExploreController controller;
  final ExploreState state;

  @override
  Widget build(BuildContext context) {
    final selected = state.selectedReference;
    final index = selected == null
        ? -1
        : state.posts.indexWhere((post) => post.reference == selected);
    return Scaffold(
      extendBodyBehindAppBar: true,
      backgroundColor: Theme.of(context).colorScheme.surface,
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.surface.withAlpha(180),
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        leading: BackButton(onPressed: controller.closeDetail),
        title: Text(_detailTitle(state)),
      ),
      body: Stack(
        children: [
          Positioned.fill(
            child: switch (state.status) {
              ExploreStatus.detailLoading => const _LoadingState(),
              ExploreStatus.detailFailure => _MessageState(
                title: _failureMessage(state.failure),
                action: controller.closeDetail,
              ),
              ExploreStatus.detail => _DetailBody(
                controller: controller,
                state: state,
                detail: state.detail!,
                adapter: controller.adapter,
                downloadService: DownloadService(adapter: controller.adapter),
                index: index,
              ),
              _ => const SizedBox.shrink(),
            },
          ),
          Positioned(
            top: MediaQuery.paddingOf(context).top + kToolbarHeight,
            left: 0,
            right: 0,
            child: _DetailPagerBar(
              contextLabel: _contextLabel(state),
              index: index,
              count: state.posts.length,
              onPrevious: index > 0
                  ? () => controller.openAdjacentDetail(-1)
                  : null,
              onNext: index >= 0 && index < state.posts.length - 1
                  ? () => controller.openAdjacentDetail(1)
                  : null,
            ),
          ),
        ],
      ),
    );
  }
}

class _DetailPagerBar extends StatelessWidget {
  const _DetailPagerBar({
    required this.contextLabel,
    required this.index,
    required this.count,
    required this.onPrevious,
    required this.onNext,
  });

  final String contextLabel;
  final int index;
  final int count;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    final position = index < 0 ? '— of $count' : '${index + 1} of $count';
    return Material(
      color: Theme.of(context).colorScheme.surface.withAlpha(180),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8),
        child: Row(
          children: [
            IconButton(
              onPressed: onPrevious,
              icon: const Icon(Icons.chevron_left),
              tooltip: 'Previous post',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            ),
            Expanded(
              child: Text(
                '$contextLabel · $position',
                textAlign: TextAlign.center,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            IconButton(
              onPressed: onNext,
              icon: const Icon(Icons.chevron_right),
              tooltip: 'Next post',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            ),
          ],
        ),
      ),
    );
  }
}

String _detailTitle(ExploreState state) => switch (state.query?.source) {
  PostQuerySource.popular => 'Popular',
  PostQuerySource.tagSearch => 'Search results',
  _ => 'Newest',
};

String _contextLabel(ExploreState state) {
  final query = state.query;
  if (query?.source == PostQuerySource.popular) {
    final popular = query!.popularQuery!;
    final period =
        popular.period.name[0].toUpperCase() + popular.period.name.substring(1);
    return 'Popular · $period · ${_popularWindowLabel(popular)}';
  }
  if (query?.source == PostQuerySource.tagSearch) return 'Search results';
  return 'Newest';
}

class _DetailBody extends StatelessWidget {
  const _DetailBody({
    required this.controller,
    required this.state,
    required this.detail,
    required this.adapter,
    required this.downloadService,
    required this.index,
  });

  final ExploreController controller;
  final ExploreState state;
  final PostDetail detail;
  final SiteAdapter adapter;
  final DownloadService downloadService;
  final int index;

  @override
  Widget build(BuildContext context) => Stack(
    key: const ValueKey('explore-detail'),
    children: [
      _DetailImagePager(
        controller: controller,
        state: state,
        detail: detail,
        adapter: adapter,
        index: index,
      ),
      _DetailInspectSheet(
        detail: detail,
        downloadService: downloadService,
        onTagSelected: controller.search,
      ),
    ],
  );
}

class _DetailImagePager extends StatefulWidget {
  const _DetailImagePager({
    required this.controller,
    required this.state,
    required this.detail,
    required this.adapter,
    required this.index,
  });

  final ExploreController controller;
  final ExploreState state;
  final PostDetail detail;
  final SiteAdapter adapter;
  final int index;

  @override
  State<_DetailImagePager> createState() => _DetailImagePagerState();
}

class _DetailImagePagerState extends State<_DetailImagePager> {
  late final ExtendedPageController _pageController = ExtendedPageController(
    initialPage: widget.index,
  );
  final _gestureKeys = <int, GlobalKey<ExtendedImageGestureState>>{};
  final _prefetchedReferences = <PostRef>{};
  Offset? _pointerDown;
  var _activePointers = 0;
  var _hadMultiplePointers = false;

  @override
  void initState() {
    super.initState();
    _schedulePrefetch();
  }

  @override
  void didUpdateWidget(covariant _DetailImagePager oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.index != widget.index && _pageController.hasClients) {
      final current = _pageController.page?.round();
      if (current != widget.index) {
        _pageController.animateToPage(
          widget.index,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOut,
        );
      }
    }
    if (oldWidget.index != widget.index ||
        oldWidget.state.posts.length != widget.state.posts.length) {
      _schedulePrefetch();
    }
  }

  @override
  void dispose() {
    _pageController.dispose();
    _gestureKeys.clear();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => KeyedSubtree(
    key: const ValueKey('detail-pager'),
    child: Listener(
      behavior: HitTestBehavior.opaque,
      onPointerDown: (event) {
        if (_activePointers == 0) _pointerDown = event.position;
        if (_activePointers > 0) _hadMultiplePointers = true;
        _activePointers++;
      },
      onPointerUp: (event) {
        _activePointers = (_activePointers - 1).clamp(0, 10).toInt();
        if (_activePointers == 0) {
          final start = _pointerDown;
          _pointerDown = null;
          final hadMultiplePointers = _hadMultiplePointers;
          _hadMultiplePointers = false;
          if (!hadMultiplePointers && start != null) {
            _handleFallbackSwipe(start, event.position);
          }
        }
      },
      onPointerCancel: (_) {
        _activePointers = 0;
        _pointerDown = null;
        _hadMultiplePointers = false;
      },
      child: ExtendedImageGesturePageView.builder(
        controller: _pageController,
        itemCount: widget.state.posts.length,
        onPageChanged: (page) {
          if (page == widget.index) return;
          widget.controller.openAdjacentDetail(page - widget.index);
        },
        itemBuilder: (context, page) {
          final post = page == widget.index
              ? widget.detail.summary
              : widget.state.posts[page];
          return _DetailZoomArtwork(
            key: ValueKey(
              'detail-zoom-${post.reference.siteId.value}-${post.reference.remoteId}',
            ),
            gestureKey: _gestureKeyFor(page),
            post: post,
            adapter: widget.adapter,
            variantId: page == widget.index
                ? _detailImageVariant(widget.detail.media)
                : null,
          );
        },
      ),
    ),
  );

  GlobalKey<ExtendedImageGestureState> _gestureKeyFor(int page) =>
      _gestureKeys.putIfAbsent(page, GlobalKey<ExtendedImageGestureState>.new);

  void _schedulePrefetch() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_prefetchAdjacent());
    });
  }

  Future<void> _prefetchAdjacent() async {
    final candidates = [
      for (
        var page = widget.index + 1;
        page < widget.state.posts.length && page <= widget.index + 3;
        page++
      )
        widget.state.posts[page],
    ].where((post) => _prefetchedReferences.add(post.reference));
    await Future.wait(candidates.map(_prefetchPost));
  }

  Future<void> _prefetchPost(PostSummary post) async {
    try {
      final detail = await widget.adapter.getPost(post.reference);
      final variantId = _detailImageVariant(detail.media);
      if (variantId == null) return;
      final media = await widget.adapter.resolveMedia(
        post.reference,
        variantId,
      );
      if (!mounted) return;
      await precacheImage(_imageProvider(media.source), context);
    } on Object {
      // Prefetch is opportunistic; the detail page still loads on demand.
    }
  }

  void _handleFallbackSwipe(Offset start, Offset end) {
    if (!_pageController.hasClients ||
        _pageController.page?.round() != widget.index) {
      return;
    }
    final delta = end - start;
    if (delta.dx.abs() < 100 || delta.dx.abs() <= delta.dy.abs()) return;
    final gestureDetails =
        _gestureKeys[widget.index]?.currentState?.gestureDetails;
    if ((gestureDetails?.totalScale ?? 1) > 1) return;
    final amount = delta.dx < 0 ? 1 : -1;
    final nextIndex = widget.index + amount;
    if (nextIndex < 0 || nextIndex >= widget.state.posts.length) return;
    unawaited(widget.controller.openAdjacentDetail(amount));
  }
}

ImageProvider<Object> _imageProvider(Uri source) {
  if (source.scheme == 'asset') return AssetImage(_assetName(source));
  return ExtendedNetworkImageProvider(source.toString(), cache: true);
}

class _DetailZoomArtwork extends StatefulWidget {
  const _DetailZoomArtwork({
    required this.post,
    required this.adapter,
    required this.gestureKey,
    this.variantId,
    super.key,
  });

  final PostSummary post;
  final SiteAdapter adapter;
  final GlobalKey<ExtendedImageGestureState> gestureKey;
  final MediaVariantId? variantId;

  @override
  State<_DetailZoomArtwork> createState() => _DetailZoomArtworkState();
}

class _DetailZoomArtworkState extends State<_DetailZoomArtwork> {
  Future<ResolvedMedia>? _media;
  Future<ResolvedMedia>? _thumbnail;

  @override
  void initState() {
    super.initState();
    _start();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.gestureKey.currentState?.reset();
    });
  }

  @override
  void didUpdateWidget(covariant _DetailZoomArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.post.reference != widget.post.reference ||
        oldWidget.variantId != widget.variantId) {
      _start();
    }
  }

  @override
  Widget build(BuildContext context) {
    final preview = widget.post.preview;
    if (preview == null && widget.variantId == null) {
      return const _ArtworkPlaceholder();
    }
    return FutureBuilder<ResolvedMedia>(
      future: _media,
      builder: (context, snapshot) {
        final source = snapshot.data?.source;
        return FutureBuilder<ResolvedMedia>(
          future: _thumbnail,
          builder: (context, thumbnailSnapshot) {
            final thumbnail = thumbnailSnapshot.data?.source;
            if (source == null) {
              return _DetailLoadingArtwork(
                source: thumbnail,
                label: 'Artwork ${widget.post.reference.remoteId}',
              );
            }
            return _buildDetailImage(
              source: source,
              thumbnail: thumbnail,
              label: 'Artwork ${widget.post.reference.remoteId}',
            );
          },
        );
      },
    );
  }

  void _start() {
    final preview = widget.post.preview;
    final variantId = widget.variantId ?? preview?.id;
    _media = variantId == null
        ? null
        : widget.adapter.resolveMedia(widget.post.reference, variantId);
    _thumbnail = widget.variantId == null || preview == null
        ? _media
        : widget.adapter.resolveMedia(widget.post.reference, preview.id);
  }

  Widget _buildDetailImage({
    required Uri source,
    required Uri? thumbnail,
    required String label,
  }) {
    Widget? loadStateChanged(ExtendedImageState state) {
      if (state.extendedImageLoadState == LoadState.completed) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) widget.gestureKey.currentState?.reset();
        });
        return null;
      }
      return _DetailLoadingArtwork(source: thumbnail, label: label);
    }

    GestureConfig gestureConfig(ExtendedImageState _) => GestureConfig(
      inPageView: true,
      initialScale: 1,
      minScale: 1,
      maxScale: 4,
      animationMaxScale: 4.5,
      cacheGesture: false,
      initialAlignment: InitialAlignment.center,
    );
    if (source.scheme == 'asset') {
      return ExtendedImage.asset(
        _assetName(source),
        key: const ValueKey('detail-high-quality-image'),
        fit: BoxFit.contain,
        mode: ExtendedImageMode.gesture,
        extendedImageGestureKey: widget.gestureKey,
        initGestureConfigHandler: gestureConfig,
        loadStateChanged: loadStateChanged,
      );
    }
    return ExtendedImage.network(
      source.toString(),
      key: const ValueKey('detail-high-quality-image'),
      fit: BoxFit.contain,
      mode: ExtendedImageMode.gesture,
      cache: true,
      extendedImageGestureKey: widget.gestureKey,
      initGestureConfigHandler: gestureConfig,
      loadStateChanged: loadStateChanged,
    );
  }
}

class _DetailLoadingArtwork extends StatelessWidget {
  const _DetailLoadingArtwork({required this.source, required this.label});

  final Uri? source;
  final String label;

  @override
  Widget build(BuildContext context) {
    if (source == null) return const _ArtworkPlaceholder();
    if (source!.scheme == 'asset') {
      return Image.asset(
        _assetName(source!),
        fit: BoxFit.contain,
        semanticLabel: label,
        errorBuilder: (_, _, _) => const _ArtworkPlaceholder(),
      );
    }
    return Image.network(
      source!.toString(),
      fit: BoxFit.contain,
      semanticLabel: label,
      errorBuilder: (_, _, _) => const _ArtworkPlaceholder(),
    );
  }
}

String _assetName(Uri uri) =>
    uri.path.startsWith('/') ? uri.path.substring(1) : uri.path;

MediaVariantId? _detailImageVariant(List<MediaVariant> variants) {
  for (final candidate in const [
    MediaVariantId.sample,
    MediaVariantId.jpeg,
    MediaVariantId.original,
    MediaVariantId.preview,
  ]) {
    if (variants.any((variant) => variant.id == candidate)) return candidate;
  }
  return null;
}

class _DetailInspectSheet extends StatefulWidget {
  const _DetailInspectSheet({
    required this.detail,
    required this.downloadService,
    required this.onTagSelected,
  });

  final PostDetail detail;
  final DownloadService downloadService;
  final Future<void> Function(String tag) onTagSelected;

  @override
  State<_DetailInspectSheet> createState() => _DetailInspectSheetState();
}

class _DetailInspectSheetState extends State<_DetailInspectSheet> {
  late final DraggableScrollableController _sheetController =
      DraggableScrollableController();
  var _expanded = false;

  @override
  void initState() {
    super.initState();
    _sheetController.addListener(_onSheetChanged);
  }

  @override
  void dispose() {
    _sheetController.removeListener(_onSheetChanged);
    _sheetController.dispose();
    super.dispose();
  }

  void _onSheetChanged() {
    if (!_sheetController.isAttached) return;
    final expanded = _sheetController.size > 0.2;
    if (expanded != _expanded && mounted) {
      setState(() => _expanded = expanded);
    }
  }

  @override
  Widget build(BuildContext context) {
    final peekSize = (88 / MediaQuery.sizeOf(context).height)
        .clamp(0.06, 0.20)
        .toDouble();
    final metadata = <_MetadataEntry>[
      if (widget.detail.summary.width != null &&
          widget.detail.summary.height != null)
        _MetadataEntry(
          label: 'Dimensions',
          value:
              '${widget.detail.summary.width} × ${widget.detail.summary.height}',
        ),
      if (widget.detail.summary.score != null)
        _MetadataEntry(label: 'Score', value: '${widget.detail.summary.score}'),
      if (widget.detail.summary.source != null)
        _MetadataEntry(label: 'Source', value: widget.detail.summary.source!),
    ];
    return Align(
      alignment: Alignment.bottomCenter,
      child: DraggableScrollableSheet(
        key: const ValueKey('detail-inspect-sheet'),
        controller: _sheetController,
        initialChildSize: peekSize,
        minChildSize: peekSize,
        maxChildSize: 0.65,
        snap: true,
        snapSizes: [peekSize, 0.65],
        expand: false,
        builder: (context, scrollController) => Material(
          color: Theme.of(context).colorScheme.surfaceContainer,
          elevation: 4,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          clipBehavior: Clip.antiAlias,
          child: ListView(
            controller: scrollController,
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
            children: [
              Center(
                child: Container(
                  key: const ValueKey('detail-drag-handle'),
                  width: 36,
                  height: 4,
                  margin: const EdgeInsets.only(top: 8, bottom: 8),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.onSurfaceVariant
                        .withAlpha(100),
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              _DetailActions(
                variants: widget.detail.media,
                onDownload: _saveBestVariant,
                expanded: _expanded,
                onToggle: () => _sheetController.animateTo(
                  _expanded ? peekSize : 0.65,
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOut,
                ),
              ),
              const SizedBox(height: 12),
              if (metadata.isNotEmpty) _DetailMetadataTable(rows: metadata),
              if (widget.detail.summary.tags.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text('Tags', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (
                      var index = 0;
                      index < widget.detail.summary.tags.length;
                      index++
                    )
                      _DetailTagChip(
                        tag: widget.detail.summary.tags[index],
                        index: index,
                        onPressed: () => unawaited(
                          widget.onTagSelected(
                            widget.detail.summary.tags[index],
                          ),
                        ),
                      ),
                  ],
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  void _saveBestVariant() {
    if (widget.detail.media.isEmpty) return;
    unawaited(_startDownload());
  }

  Future<void> _startDownload({bool force = false}) async {
    try {
      final receipt = await widget.downloadService.save(
        reference: widget.detail.summary.reference,
        variant: _bestVariant(widget.detail.media),
        force: force,
      );
      if (!mounted) return;
      if (receipt.status == DownloadStatus.alreadySaved) {
        final confirm = await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Already saved'),
            content: const Text(
              'This image is already in Pictures/Latte. Do you want to download it again?',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Download again'),
              ),
            ],
          ),
        );
        if (confirm == true) {
          unawaited(_startDownload(force: true));
        }
      } else if (receipt.status == DownloadStatus.alreadyRunning) {
        LatteToast.show(
          context,
          message: 'Already in the download queue',
          type: ToastType.info,
        );
      } else {
        LatteToast.show(
          context,
          message: 'Downloading in background...',
          type: ToastType.info,
        );
      }
    } on Object {
      if (!mounted) return;
      LatteToast.show(
        context,
        message: 'Download could not be started',
        type: ToastType.error,
      );
    }
  }
}

class _DetailActions extends StatelessWidget {
  const _DetailActions({
    required this.variants,
    required this.onDownload,
    required this.expanded,
    required this.onToggle,
  });

  final List<MediaVariant> variants;
  final VoidCallback onDownload;
  final bool expanded;
  final VoidCallback onToggle;

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 56,
    key: const ValueKey('detail-actions'),
    child: Stack(
      alignment: Alignment.center,
      children: [
        Semantics(
          button: true,
          label: 'Download',
          child: FloatingActionButton(
            heroTag: 'detail-download',
            onPressed: variants.isEmpty ? null : onDownload,
            tooltip: 'Download',
            child: const Icon(Icons.save_alt_outlined),
          ),
        ),
        Align(
          alignment: Alignment.centerRight,
          child: FloatingActionButton.small(
            heroTag: 'detail-expand',
            onPressed: onToggle,
            tooltip: expanded ? 'Collapse details' : 'Expand details',
            child: Icon(expanded ? Icons.expand_more : Icons.expand_less),
          ),
        ),
      ],
    ),
  );
}

MediaVariant _bestVariant(List<MediaVariant> variants) {
  return variants.reduce((best, candidate) {
    final bestRank = _variantQuality(best);
    final candidateRank = _variantQuality(candidate);
    return candidateRank > bestRank ? candidate : best;
  });
}

int _variantQuality(MediaVariant variant) => switch (variant.id) {
  MediaVariantId.preview => 0,
  MediaVariantId.sample => 1,
  MediaVariantId.jpeg => 2,
  MediaVariantId.original => 3,
};

class _MetadataEntry {
  const _MetadataEntry({required this.label, required this.value});

  final String label;
  final String value;
}

class _DetailMetadataTable extends StatelessWidget {
  const _DetailMetadataTable({required this.rows});

  final List<_MetadataEntry> rows;

  @override
  Widget build(BuildContext context) => Table(
    columnWidths: const {0: IntrinsicColumnWidth(), 1: FlexColumnWidth()},
    defaultVerticalAlignment: TableCellVerticalAlignment.middle,
    border: TableBorder(
      horizontalInside: BorderSide(
        color: Theme.of(context).colorScheme.outlineVariant,
        width: 0.5,
      ),
    ),
    children: [
      for (final row in rows)
        TableRow(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 10),
              child: Text(
                row.label,
                style: Theme.of(context).textTheme.labelLarge?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 0, 10),
              child: Semantics(
                label: '${row.label}: ${row.value}',
                child: Text(row.value),
              ),
            ),
          ],
        ),
    ],
  );
}

class _DetailTagChip extends StatelessWidget {
  const _DetailTagChip({
    required this.tag,
    required this.index,
    required this.onPressed,
  });

  final String tag;
  final int index;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = _tagColors(context, index);
    return Tooltip(
      message: 'Search tag $tag',
      child: ActionChip(
        key: ValueKey('detail-tag-$tag'),
        label: Text(tag),
        onPressed: onPressed,
        backgroundColor: colors.background,
        labelStyle: TextStyle(color: colors.foreground),
        side: BorderSide(color: colors.border),
      ),
    );
  }
}

class _TagColors {
  const _TagColors({
    required this.background,
    required this.foreground,
    required this.border,
  });

  final Color background;
  final Color foreground;
  final Color border;
}

_TagColors _tagColors(BuildContext context, int index) {
  final scheme = Theme.of(context).colorScheme;
  final base = scheme.surfaceContainerHighest;
  final accents = [
    scheme.primary,
    scheme.secondary,
    scheme.tertiary,
    scheme.error,
    scheme.inversePrimary,
    scheme.outline,
  ];
  final accent = accents[index % accents.length];
  return _TagColors(
    background: Color.alphaBlend(accent.withAlpha(48), base),
    foreground: scheme.onSurface,
    border: accent,
  );
}

class _LoadingState extends StatelessWidget {
  const _LoadingState();

  @override
  Widget build(BuildContext context) {
    return Center(
      key: ValueKey('explore-loading'),
      child: Semantics(
        label: 'Loading posts',
        liveRegion: true,
        child: CircularProgressIndicator(),
      ),
    );
  }
}

class _MessageState extends StatelessWidget {
  const _MessageState({required this.title, required this.action});

  final String title;
  final VoidCallback action;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            title,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          FilledButton(onPressed: action, child: const Text('Retry')),
        ],
      ),
    );
  }
}

class _PageProgress extends StatelessWidget {
  const _PageProgress();

  @override
  Widget build(BuildContext context) =>
      const Center(child: CircularProgressIndicator());
}

class _PageRetry extends StatelessWidget {
  const _PageRetry({required this.onPressed});

  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => IconButton(
    onPressed: onPressed,
    icon: const Icon(Icons.refresh),
    tooltip: 'Retry next page',
    constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
  );
}

String _failureMessage(SiteFailure? failure) =>
    failure?.message ?? 'Yande.re is unavailable';
