import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/explore_state.dart';
import 'package:latte/src/domain/failure.dart';
import 'package:latte/src/domain/popular_query.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  test('loads discovery with all ratings visible by default', () async {
    final adapter = ScriptedAdapter.immediate([
      PostPage(
        posts: [
          post('safe'),
          post('explicit', rating: PostRating.explicit),
        ],
      ),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();

    expect(controller.state.status, ExploreStatus.content);
    expect(controller.state.posts.map((item) => item.reference.remoteId), [
      'safe',
      'explicit',
    ]);
  });

  test('loads Popular with a visible period and anchor identity', () async {
    final adapter = ScriptedAdapter.immediate([
      PostPage(posts: [post('day')]),
      PostPage(posts: [post('week')]),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.loadPopular(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 20),
    );
    expect(controller.state.query?.source, PostQuerySource.popular);
    expect(
      controller.state.query?.popularQuery,
      PopularQuery(
        period: PopularPeriod.day,
        anchor: DateTime.utc(2026, 9, 20),
      ),
    );

    await controller.loadPopular(
      period: PopularPeriod.week,
      anchor: DateTime.utc(2026, 9, 20),
    );
    expect(controller.state.posts.single.reference.remoteId, 'week');
    expect(
      controller.state.query?.popularQuery?.window.start,
      DateTime.utc(2026, 9, 14),
    );
  });

  test('switching anchors rejects an older Popular response', () async {
    final adapter = ScriptedAdapter.deferred();
    final controller = ExploreController(adapter: adapter);

    final first = controller.loadPopular(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 20),
    );
    final second = controller.loadPopular(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 19),
    );

    adapter.completers[1].complete(PostPage(posts: [post('newer')]));
    await second;
    adapter.completers[0].complete(PostPage(posts: [post('older')]));
    await first;

    expect(controller.state.posts.single.reference.remoteId, 'newer');
    expect(
      controller.state.query?.popularQuery?.anchor,
      DateTime.utc(2026, 9, 19),
    );
  });

  test('anchor navigation and detail back preserve Popular context', () async {
    final item = post('detail');
    final adapter = ScriptedAdapter.immediate([
      PostPage(posts: [item]),
      PostPage(posts: [post('shifted')]),
    ])..details[item.reference] = PostDetail(summary: item, media: const []);
    final controller = ExploreController(adapter: adapter);

    await controller.loadPopular(
      period: PopularPeriod.month,
      anchor: DateTime.utc(2026, 9, 20),
    );
    await controller.openDetail(item.reference);
    controller.closeDetail();
    expect(controller.state.query?.popularQuery?.period, PopularPeriod.month);
    expect(
      controller.state.query?.popularQuery?.anchor,
      DateTime.utc(2026, 9, 20),
    );

    await controller.shiftPopularAnchor(-1);
    expect(
      controller.state.query?.popularQuery?.anchor,
      DateTime.utc(2026, 8, 1),
    );
  });

  test('appends the next page once and removes duplicate identities', () async {
    final adapter = ScriptedAdapter.immediate([
      PostPage(posts: [post('one'), post('two')], next: '2'),
      PostPage(posts: [post('two'), post('three')]),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();
    await controller.loadNextPage();

    expect(controller.state.status, ExploreStatus.endReached);
    expect(controller.state.posts.map((item) => item.reference.remoteId), [
      'one',
      'two',
      'three',
    ]);
  });

  test('ignores a response after a newer discovery request wins', () async {
    final adapter = ScriptedAdapter.deferred();
    final controller = ExploreController(adapter: adapter);
    final first = controller.loadDiscovery();
    final second = controller.loadDiscovery();

    adapter.completers[1].complete(PostPage(posts: [post('newer')]));
    await second;
    adapter.completers[0].complete(PostPage(posts: [post('older')]));
    await first;

    expect(controller.state.posts.map((item) => item.reference.remoteId), [
      'newer',
    ]);
  });

  test('keeps a structured failure separate from empty success', () async {
    final adapter = ScriptedAdapter.failure(
      const SiteFailure(
        kind: SiteFailureKind.transportUnavailable,
        retryable: true,
        message: "Can't reach Yande.re",
      ),
    );
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();

    expect(controller.state.status, ExploreStatus.failure);
    expect(
      controller.state.failure?.kind,
      SiteFailureKind.transportUnavailable,
    );
  });

  test('restores the feed after detail navigation', () async {
    final summary = post('one');
    final adapter =
        ScriptedAdapter.immediate([
            PostPage(posts: [summary]),
          ])
          ..details[summary.reference] = PostDetail(
            summary: summary,
            media: const [],
          );
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();
    await controller.openDetail(summary.reference);
    expect(controller.state.status, ExploreStatus.detail);
    expect(controller.state.detail?.summary, summary);

    controller.closeDetail();

    expect(controller.state.status, ExploreStatus.content);
    expect(controller.state.posts, [summary]);
  });

  test('preserves explicit search expressions without filtering', () async {
    final adapter = ScriptedAdapter.immediate([
      PostPage(posts: [post('explicit', rating: PostRating.explicit)]),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.search('rating:explicit order:score');

    expect(controller.state.posts.single.rating, PostRating.explicit);
    expect(adapter.queries.single.expression, 'rating:explicit order:score');
  });

  test('detail pager opens the adjacent loaded post', () async {
    final items = [post('first'), post('second')];
    final adapter = ScriptedAdapter.immediate([PostPage(posts: items)]);
    for (final item in items) {
      adapter.details[item.reference] = PostDetail(
        summary: item,
        media: const [],
      );
    }
    final controller = ExploreController(adapter: adapter);

    await controller.loadPopular(
      period: PopularPeriod.day,
      anchor: DateTime.utc(2026, 9, 20),
    );
    await controller.openDetail(items.first.reference);
    await controller.openAdjacentDetail(1);

    expect(controller.state.selectedReference, items[1].reference);
    expect(controller.state.detail?.summary, items[1]);
  });
}

PostSummary post(String id, {PostRating rating = PostRating.safe}) =>
    PostSummary(
      reference: PostRef(siteId: const SiteId('fake'), remoteId: id),
      rating: rating,
      tags: const ['fixture'],
    );

class ScriptedAdapter implements SiteAdapter {
  ScriptedAdapter.immediate(List<PostPage> pages)
    : _pages = pages,
      _failure = null;

  ScriptedAdapter.deferred() : _pages = [], _failure = null;

  ScriptedAdapter.failure(SiteFailure failure)
    : _pages = [],
      _failure = failure;

  final List<PostPage> _pages;
  final SiteFailure? _failure;
  final List<PostQuery> queries = [];
  final List<Completer<PostPage>> completers = [];
  final Map<PostRef, PostDetail> details = {};

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) {
    queries.add(query);
    if (_failure != null) return Future.error(SiteFailureException(_failure));
    if (_pages.isNotEmpty) return Future.value(_pages.removeAt(0));
    final completer = Completer<PostPage>();
    completers.add(completer);
    return completer.future;
  }

  @override
  Future<PostDetail> getPost(PostRef reference) async => details[reference]!;

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) {
    throw UnimplementedError();
  }
}
