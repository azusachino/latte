import 'package:flutter/material.dart';

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
    this.onSearch,
    this.onSettings,
    super.key,
  });

  final ExploreController controller;
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
  int? _columnCount;

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
                columnCount: _columnCount,
                onColumnCount: (value) => setState(() => _columnCount = value),
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
    required this.onColumnCount,
    required this.scrollController,
    required this.searchView,
    this.onSearch,
    this.onSettings,
  });

  final ExploreController controller;
  final ExploreState state;
  final int? columnCount;
  final ValueChanged<int> onColumnCount;
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
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Latte'),
          actions: [
            IconButton(
              onPressed: onSearch,
              icon: const Icon(Icons.search),
              tooltip: 'Search',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            ),
            PopupMenuButton<_ExploreMenuAction>(
              tooltip: 'Columns',
              icon: const Icon(Icons.view_column_outlined),
              onSelected: (action) {
                switch (action) {
                  case _ExploreMenuAction.columns2:
                    onColumnCount(2);
                  case _ExploreMenuAction.columns3:
                    onColumnCount(3);
                  case _ExploreMenuAction.columns4:
                    onColumnCount(4);
                  case _ExploreMenuAction.settings:
                    onSettings?.call();
                }
              },
              itemBuilder: (context) => [
                const PopupMenuItem<_ExploreMenuAction>(
                  value: _ExploreMenuAction.columns2,
                  child: Text('2 columns'),
                ),
                const PopupMenuItem<_ExploreMenuAction>(
                  value: _ExploreMenuAction.columns3,
                  child: Text('3 columns'),
                ),
                const PopupMenuItem<_ExploreMenuAction>(
                  value: _ExploreMenuAction.columns4,
                  child: Text('4 columns'),
                ),
                if (onSettings != null) ...[
                  const PopupMenuDivider(),
                  const PopupMenuItem<_ExploreMenuAction>(
                    value: _ExploreMenuAction.settings,
                    child: Text('Settings'),
                  ),
                ],
              ],
            ),
          ],
          bottom: TabBar(
            key: const ValueKey('explore-tabs'),
            tabs: const [
              Tab(text: 'Popular'),
              Tab(text: 'Newest'),
            ],
            onTap: (index) {
              if (index == 0) {
                controller.loadPopular();
              } else {
                controller.loadDiscovery();
              }
            },
          ),
        ),
        body: LayoutBuilder(
          builder: (context, constraints) {
            final expanded = constraints.maxWidth >= 600;
            final columns = columnCount ?? (expanded ? 4 : 2);
            return KeyedSubtree(
              key: ValueKey(expanded ? 'expanded-explore' : 'compact-explore'),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  searchView,
                  Expanded(
                    child: _ExploreBody(
                      controller: controller,
                      state: state,
                      columnCount: columns,
                      scrollController: scrollController,
                    ),
                  ),
                ],
              ),
            );
          },
        ),
        floatingActionButton: popular
            ? FloatingActionButton(
                onPressed: () => _showPopularPeriodSheet(context, controller),
                tooltip: 'Choose popular period',
                child: const Icon(Icons.calendar_today_outlined),
              )
            : null,
      ),
    );
  }
}

enum _ExploreMenuAction { columns2, columns3, columns4, settings }

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
  const _RemoteArtwork({
    required this.post,
    required this.adapter,
    this.fit = BoxFit.cover,
    this.variantId,
  });

  final PostSummary post;
  final SiteAdapter adapter;
  final BoxFit fit;
  final MediaVariantId? variantId;

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
            if (source == null && thumbnail == null) {
              return const _ArtworkPlaceholder();
            }
            return SizedBox.expand(
              child: Stack(
                fit: StackFit.expand,
                children: [
                  if (thumbnail != null)
                    Image.network(
                      thumbnail.toString(),
                      fit: widget.fit,
                      semanticLabel:
                          'Artwork ${widget.post.reference.remoteId}',
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                  if (source != null)
                    Image.network(
                      source.toString(),
                      fit: widget.fit,
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
    final variantId = widget.variantId ?? preview?.id;
    _media = variantId == null
        ? null
        : widget.adapter.resolveMedia(widget.post.reference, variantId);
    _thumbnail = widget.variantId == null || preview == null
        ? _media
        : widget.adapter.resolveMedia(widget.post.reference, preview.id);
  }
}

class _ArtworkPlaceholder extends StatelessWidget {
  const _ArtworkPlaceholder();

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      child: const Center(child: Icon(Icons.image_outlined)),
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
      _DetailInspectSheet(detail: detail, downloadService: downloadService),
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
  late final PageController _pageController = PageController(
    initialPage: widget.index,
  );
  var _pinching = false;

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
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => PageView.builder(
    key: const ValueKey('detail-pager'),
    controller: _pageController,
    physics: _pinching ? const NeverScrollableScrollPhysics() : null,
    itemCount: widget.state.posts.length,
    onPageChanged: (page) {
      if (page == widget.index) return;
      widget.controller.openAdjacentDetail(page - widget.index);
    },
    itemBuilder: (context, page) {
      final post = page == widget.index
          ? widget.detail.summary
          : widget.state.posts[page];
      return ColoredBox(
        color: Theme.of(context).colorScheme.surface,
        child: _DetailZoomImage(
          onPinchActive: (active) {
            if (!mounted || _pinching == active) return;
            setState(() => _pinching = active);
          },
          child: _RemoteArtwork(
            post: post,
            adapter: widget.adapter,
            fit: BoxFit.contain,
            variantId: page == widget.index
                ? _detailImageVariant(widget.detail.media)
                : null,
          ),
        ),
      );
    },
  );
}

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

class _DetailZoomImage extends StatefulWidget {
  const _DetailZoomImage({required this.child, required this.onPinchActive});

  final Widget child;
  final ValueChanged<bool> onPinchActive;

  @override
  State<_DetailZoomImage> createState() => _DetailZoomImageState();
}

class _DetailZoomImageState extends State<_DetailZoomImage> {
  final _pointers = <int, Offset>{};
  var _pinching = false;
  var _scale = 1.0;
  var _gestureStartScale = 1.0;
  var _initialDistance = 0.0;

  void _handlePointerDown(PointerDownEvent event) {
    _pointers[event.pointer] = event.position;
    if (_pointers.length != 2 || _pinching) return;
    _pinching = true;
    _gestureStartScale = _scale;
    _initialDistance = _distance();
    widget.onPinchActive(true);
  }

  void _handlePointerMove(PointerMoveEvent event) {
    if (!_pointers.containsKey(event.pointer)) return;
    _pointers[event.pointer] = event.position;
    if (!_pinching || _pointers.length != 2 || _initialDistance <= 0) return;
    final nextScale = (_gestureStartScale * _distance() / _initialDistance)
        .clamp(1.0, 4.0);
    if (nextScale == _scale) return;
    setState(() => _scale = nextScale);
  }

  void _handlePointerEnd(PointerEvent event) {
    _pointers.remove(event.pointer);
    if (_pinching && _pointers.length < 2) {
      _pinching = false;
      widget.onPinchActive(false);
      if (_scale < 1.01) setState(() => _scale = 1.0);
    }
  }

  @override
  Widget build(BuildContext context) => Listener(
    key: const ValueKey('detail-zoom'),
    behavior: HitTestBehavior.opaque,
    onPointerDown: _handlePointerDown,
    onPointerMove: _handlePointerMove,
    onPointerUp: _handlePointerEnd,
    onPointerCancel: _handlePointerEnd,
    child: Transform.scale(scale: _scale, child: widget.child),
  );

  double _distance() {
    final points = _pointers.values.toList(growable: false);
    return (points[0] - points[1]).distance;
  }
}

class _DetailInspectSheet extends StatefulWidget {
  const _DetailInspectSheet({
    required this.detail,
    required this.downloadService,
  });

  final PostDetail detail;
  final DownloadService downloadService;

  @override
  State<_DetailInspectSheet> createState() => _DetailInspectSheetState();
}

class _DetailInspectSheetState extends State<_DetailInspectSheet> {
  late final DraggableScrollableController _sheetController =
      DraggableScrollableController();
  var _saving = false;
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
    final peekSize = (66 / MediaQuery.sizeOf(context).height)
        .clamp(0.035, 0.16)
        .toDouble();
    final facts = <Widget>[
      if (widget.detail.summary.width != null &&
          widget.detail.summary.height != null)
        _Fact(
          label: 'Dimensions',
          value:
              '${widget.detail.summary.width} × ${widget.detail.summary.height}',
        ),
      if (widget.detail.summary.score != null)
        _Fact(label: 'Score', value: '${widget.detail.summary.score}'),
      if (widget.detail.summary.source != null)
        _Fact(label: 'Source', value: widget.detail.summary.source!),
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
            padding: const EdgeInsets.fromLTRB(16, 5, 16, 24),
            children: [
              _DetailActions(
                variants: widget.detail.media,
                saving: _saving,
                onDownload: _saveBestVariant,
                expanded: _expanded,
                onToggle: () => _sheetController.animateTo(
                  _expanded ? peekSize : 0.65,
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOut,
                ),
              ),
              const SizedBox(height: 12),
              Wrap(spacing: 8, runSpacing: 8, children: facts),
              if (widget.detail.summary.tags.isNotEmpty) ...[
                const SizedBox(height: 16),
                Text('Tags', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: widget.detail.summary.tags
                      .map((tag) => Chip(label: Text(tag)))
                      .toList(),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _saveBestVariant() async {
    if (_saving || widget.detail.media.isEmpty) return;
    setState(() => _saving = true);
    try {
      await widget.downloadService.save(
        reference: widget.detail.summary.reference,
        variant: _bestVariant(widget.detail.media),
      );
    } on Object {
      // Android reports completion, duplicate, and failure through its native
      // download notification.
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

class _DetailActions extends StatelessWidget {
  const _DetailActions({
    required this.variants,
    required this.saving,
    required this.onDownload,
    required this.expanded,
    required this.onToggle,
  });

  final List<MediaVariant> variants;
  final bool saving;
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
            onPressed: variants.isEmpty || saving ? null : onDownload,
            tooltip: 'Download',
            child: saving
                ? const SizedBox.square(
                    dimension: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save_alt_outlined),
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

class _Fact extends StatelessWidget {
  const _Fact({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '$label: $value',
      child: Chip(label: Text('$label  $value')),
    );
  }
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
