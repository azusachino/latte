import 'package:flutter/material.dart';

import '../../domain/explore_state.dart';
import '../../domain/failure.dart';
import '../../domain/popular_query.dart';
import '../../domain/post.dart';
import '../../sites/site_adapter.dart';
import 'explore_controller.dart';
import 'search_view.dart';

class ExploreScreen extends StatefulWidget {
  const ExploreScreen({required this.controller, this.onSearch, super.key});

  final ExploreController controller;
  final VoidCallback? onSearch;

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
                onSearch:
                    widget.onSearch ?? () => _searchKey.currentState?.open(),
                searchView: SearchView(
                  key: _searchKey,
                  controller: widget.controller,
                  showStatus: false,
                  showBar: false,
                ),
                scrollController: _scrollController,
              );
        final reducedMotion = MediaQuery.disableAnimationsOf(context);
        return AnimatedSwitcher(
          duration: reducedMotion
              ? Duration.zero
              : const Duration(milliseconds: 180),
          child: KeyedSubtree(key: ValueKey(isDetail), child: child),
        );
      },
    );
  }
}

class _ExploreScaffold extends StatelessWidget {
  const _ExploreScaffold({
    required this.controller,
    required this.state,
    required this.scrollController,
    required this.searchView,
    this.onSearch,
  });

  final ExploreController controller;
  final ExploreState state;
  final ScrollController scrollController;
  final SearchView searchView;
  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Latte'),
        actions: [
          IconButton(
            onPressed: onSearch,
            icon: const Icon(Icons.search),
            tooltip: 'Search',
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
        ],
      ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final expanded = constraints.maxWidth >= 600;
          return KeyedSubtree(
            key: ValueKey(expanded ? 'expanded-explore' : 'compact-explore'),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _DiscoveryModes(controller: controller, state: state),
                  if (_isPopular(state))
                    _PopularControls(controller: controller),
                  searchView,
                  Expanded(
                    child: _ExploreBody(
                      controller: controller,
                      state: state,
                      scrollController: scrollController,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

bool _isPopular(ExploreState state) =>
    state.query?.source != PostQuerySource.discovery;

class _DiscoveryModes extends StatelessWidget {
  const _DiscoveryModes({required this.controller, required this.state});

  final ExploreController controller;
  final ExploreState state;

  @override
  Widget build(BuildContext context) {
    final popular = _isPopular(state);
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 48),
      child: SegmentedButton<bool>(
        segments: const [
          ButtonSegment(value: true, label: Text('Popular')),
          ButtonSegment(value: false, label: Text('Newest')),
        ],
        selected: {popular},
        onSelectionChanged: (selection) {
          if (selection.single) {
            controller.loadPopular();
          } else {
            controller.loadDiscovery();
          }
        },
      ),
    );
  }
}

class _PopularControls extends StatelessWidget {
  const _PopularControls({required this.controller});

  final ExploreController controller;

  @override
  Widget build(BuildContext context) {
    final query = controller.selectedPopularQuery;
    return Column(
      key: const ValueKey('popular-controls'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const SizedBox(height: 12),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 48),
            child: SegmentedButton<PopularPeriod>(
              segments: const [
                ButtonSegment(value: PopularPeriod.day, label: Text('Day')),
                ButtonSegment(value: PopularPeriod.week, label: Text('Week')),
                ButtonSegment(value: PopularPeriod.month, label: Text('Month')),
              ],
              selected: {query.period},
              onSelectionChanged: (selection) =>
                  controller.loadPopular(period: selection.single),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Row(
          children: [
            IconButton(
              onPressed: () => controller.shiftPopularAnchor(-1),
              icon: const Icon(Icons.chevron_left),
              tooltip: 'Previous period',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            ),
            Expanded(
              child: Text(
                _popularWindowLabel(query),
                textAlign: TextAlign.center,
                semanticsLabel: 'Selected period ${_popularWindowLabel(query)}',
                style: Theme.of(context).textTheme.labelLarge,
              ),
            ),
            IconButton(
              onPressed: () => controller.shiftPopularAnchor(1),
              icon: const Icon(Icons.chevron_right),
              tooltip: 'Next period',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            ),
          ],
        ),
      ],
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
    required this.scrollController,
  });

  final ExploreController controller;
  final ExploreState state;
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
    required this.scrollController,
  });

  final ExploreController controller;
  final ExploreState state;
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
          final columnCount = constraints.maxWidth >= 600 ? 4 : 2;
          final columns = _masonryColumns(state.posts, columnCount);
          return ListView(
            key: const ValueKey('explore-grid'),
            controller: scrollController,
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              Row(
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
    final facts = <String>[
      'Post ${post.reference.remoteId}',
      post.rating.name,
      if (post.score != null) 'score ${post.score}',
      if (post.width != null && post.height != null)
        '${post.width} × ${post.height}',
    ];
    final label = facts.join(', ');
    return Semantics(
      button: true,
      container: true,
      label: label,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AspectRatio(
                aspectRatio: _aspectRatio(post),
                child: _RemoteArtwork(post: post, adapter: adapter),
              ),
              Padding(
                padding: const EdgeInsets.all(8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '#${post.reference.remoteId}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelLarge,
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _cardMetadata(post),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

String _cardMetadata(PostSummary post) {
  final values = <String>[post.rating.name];
  if (post.score != null) values.add('score ${post.score}');
  if (post.width != null && post.height != null) {
    values.add('${post.width} × ${post.height}');
  }
  return values.join(' · ');
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

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void didUpdateWidget(covariant _RemoteArtwork oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.post.reference != widget.post.reference) _start();
  }

  @override
  Widget build(BuildContext context) {
    final preview = widget.post.preview;
    if (preview == null) return const _ArtworkPlaceholder();
    return FutureBuilder<ResolvedMedia>(
      future: _media,
      builder: (context, snapshot) {
        final source = snapshot.data?.source;
        if (source == null) return const _ArtworkPlaceholder();
        return Image.network(
          source.toString(),
          fit: BoxFit.cover,
          semanticLabel: 'Artwork ${widget.post.reference.remoteId}',
          errorBuilder: (_, _, _) => const _ArtworkPlaceholder(),
        );
      },
    );
  }

  void _start() {
    final preview = widget.post.preview;
    _media = preview == null
        ? null
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
      appBar: AppBar(
        leading: BackButton(onPressed: controller.closeDetail),
        title: const Text('Detail'),
      ),
      body: Column(
        children: [
          _DetailPagerBar(
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
          Expanded(
            child: switch (state.status) {
              ExploreStatus.detailLoading => const _LoadingState(),
              ExploreStatus.detailFailure => _MessageState(
                title: _failureMessage(state.failure),
                action: controller.closeDetail,
              ),
              ExploreStatus.detail => _DetailBody(
                detail: state.detail!,
                adapter: controller.adapter,
              ),
              _ => const SizedBox.shrink(),
            },
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
      color: Theme.of(context).colorScheme.surfaceContainer,
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
  const _DetailBody({required this.detail, required this.adapter});

  final PostDetail detail;
  final SiteAdapter adapter;

  @override
  Widget build(BuildContext context) {
    final expanded = MediaQuery.sizeOf(context).width >= 600;
    final facts = <Widget>[
      _Fact(label: 'Rating', value: detail.summary.rating.name),
      if (detail.summary.width != null && detail.summary.height != null)
        _Fact(
          label: 'Dimensions',
          value: '${detail.summary.width} × ${detail.summary.height}',
        ),
      if (detail.summary.score != null)
        _Fact(label: 'Score', value: '${detail.summary.score}'),
      if (detail.summary.source != null)
        _Fact(label: 'Source', value: detail.summary.source!),
    ];
    final metadata = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(spacing: 8, runSpacing: 8, children: facts),
        if (detail.summary.tags.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text('Tags', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: detail.summary.tags
                .map((tag) => Chip(label: Text(tag)))
                .toList(),
          ),
        ],
      ],
    );
    final image = _RemoteArtwork(post: detail.summary, adapter: adapter);
    return SingleChildScrollView(
      key: const ValueKey('explore-detail'),
      padding: const EdgeInsets.all(16),
      child: expanded
          ? Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: image),
                const SizedBox(width: 24),
                Expanded(child: metadata),
              ],
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(height: 320, child: image),
                const SizedBox(height: 16),
                metadata,
              ],
            ),
    );
  }
}

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
