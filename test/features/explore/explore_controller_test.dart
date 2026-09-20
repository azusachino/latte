import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/explore_state.dart';
import 'package:latte/src/domain/failure.dart';
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
    expect(adapter.queries.single.contentPolicy, ContentPolicy.all);
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

  test('safe mode restarts discovery and filters explicit content', () async {
    final adapter = ScriptedAdapter.immediate([
      PostPage(
        posts: [
          post('safe'),
          post('explicit', rating: PostRating.explicit),
        ],
      ),
      PostPage(posts: [post('safe-2')]),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();
    await controller.setSafeMode(true);

    expect(controller.state.query?.contentPolicy, ContentPolicy.safe);
    expect(controller.state.posts.map((item) => item.reference.remoteId), [
      'safe-2',
    ]);
    expect(adapter.queries.last.contentPolicy, ContentPolicy.safe);
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
