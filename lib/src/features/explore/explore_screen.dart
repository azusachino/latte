import 'package:flutter/material.dart';

import '../../domain/explore_state.dart';
import '../../domain/failure.dart';
import '../../domain/post.dart';
import '../../sites/site_adapter.dart';
import 'explore_controller.dart';

class ExploreScreen extends StatefulWidget {
  const ExploreScreen({required this.controller, this.onSearch, super.key});

  final ExploreController controller;
  final VoidCallback? onSearch;

  @override
  State<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends State<ExploreScreen> {
  late final ScrollController _scrollController;
  var _wasDetail = false;
  var _savedScrollOffset = 0.0;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && widget.controller.state.status == ExploreStatus.initial) {
        widget.controller.loadDiscovery();
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
                onSearch: widget.onSearch,
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
    this.onSearch,
  });

  final ExploreController controller;
  final ExploreState state;
  final ScrollController scrollController;
  final VoidCallback? onSearch;

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final scaledLabel = MediaQuery.textScalerOf(context).scale(14);
    final showSafeModeInBar = scaledLabel < 20 && width >= 340;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Latte'),
        actions: [
          IconButton(
            onPressed: onSearch ?? () {},
            icon: const Icon(Icons.search),
            tooltip: 'Search',
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          ),
          if (showSafeModeInBar)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: _SafeModeChip(state: state, controller: controller),
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
                  if (!showSafeModeInBar) ...[
                    Align(
                      alignment: Alignment.centerLeft,
                      child: _SafeModeChip(
                        state: state,
                        controller: controller,
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
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

class _SafeModeChip extends StatelessWidget {
  const _SafeModeChip({required this.state, required this.controller});

  final ExploreState state;
  final ExploreController controller;

  @override
  Widget build(BuildContext context) {
    return FilterChip(
      label: const Text('Safe Mode'),
      selected: state.query?.contentPolicy == ContentPolicy.safe,
      onSelected: controller.setSafeMode,
    );
  }
}

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
        action: controller.loadDiscovery,
      ),
      ExploreStatus.noResults => _MessageState(
        title: 'No matches',
        action: controller.loadDiscovery,
      ),
      ExploreStatus.failure => _MessageState(
        title: _failureMessage(state.failure),
        action: controller.loadDiscovery,
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
    final expanded = MediaQuery.sizeOf(context).width >= 600;
    final isLoading = state.status == ExploreStatus.nextPageLoading;
    final hasNextFailure = state.status == ExploreStatus.nextPageFailure;
    final itemCount =
        state.posts.length + (isLoading || hasNextFailure ? 1 : 0);
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
      child: GridView.builder(
        key: const ValueKey('explore-grid'),
        controller: scrollController,
        padding: const EdgeInsets.only(bottom: 24),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: expanded ? 4 : 2,
          crossAxisSpacing: 12,
          mainAxisSpacing: 12,
          childAspectRatio: 0.78,
        ),
        itemCount: itemCount,
        itemBuilder: (context, index) {
          if (index >= state.posts.length) {
            return hasNextFailure
                ? _PageRetry(onPressed: controller.loadNextPage)
                : const _PageProgress();
          }
          final post = state.posts[index];
          return _PostCard(
            post: post,
            adapter: controller.adapter,
            onTap: () => controller.openDetail(post.reference),
          );
        },
      ),
    );
  }
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
    final label = 'Post ${post.reference.remoteId}, ${post.rating.name}';
    return Semantics(
      button: true,
      label: label,
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(
                child: _RemoteArtwork(post: post, adapter: adapter),
              ),
              Padding(
                padding: const EdgeInsets.all(8),
                child: Text(
                  '#${post.reference.remoteId}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.labelLarge,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
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
    return Scaffold(
      appBar: AppBar(
        leading: BackButton(onPressed: controller.closeDetail),
        title: const Text('Detail'),
      ),
      body: switch (state.status) {
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
    );
  }
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
