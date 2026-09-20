import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/explore_state.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  test('a newer search response wins the replacement race', () async {
    final adapter = DeferredSearchAdapter();
    final controller = ExploreController(adapter: adapter);

    final first = controller.search('artist -tag:old');
    final second = controller.search('artist -tag:new');

    adapter.completers[1].complete(PostPage(posts: [post('newer')]));
    await second;
    adapter.completers[0].complete(PostPage(posts: [post('older')]));
    await first;

    expect(controller.state.status, ExploreStatus.content);
    expect(controller.state.query?.expression, 'artist -tag:new');
    expect(controller.state.posts.map((item) => item.reference.remoteId), [
      'newer',
    ]);
  });

  test('an empty search is a distinct no-results state', () async {
    final adapter = ImmediateSearchAdapter(const PostPage(posts: []));
    final controller = ExploreController(adapter: adapter);

    await controller.search('nothing order:score');

    expect(controller.state.status, ExploreStatus.noResults);
    expect(controller.state.query?.expression, 'nothing order:score');
    expect(adapter.queries.single.source, PostQuerySource.tagSearch);
  });

  test('clearing search restores discovery without refetching it', () async {
    final discovery = post('discovery');
    final adapter = QueuedSearchAdapter([
      PostPage(posts: [discovery]),
      PostPage(posts: [post('result')]),
    ]);
    final controller = ExploreController(adapter: adapter);

    await controller.loadDiscovery();
    await controller.search('artist');
    await controller.clearSearch();

    expect(controller.state.status, ExploreStatus.content);
    expect(controller.state.query?.source, PostQuerySource.discovery);
    expect(controller.state.posts, [discovery]);
    expect(adapter.queries, hasLength(2));
  });
}

PostSummary post(String id) => PostSummary(
  reference: PostRef(siteId: const SiteId('fake'), remoteId: id),
  rating: PostRating.safe,
  tags: const ['fixture'],
);

class DeferredSearchAdapter implements SiteAdapter {
  final queries = <PostQuery>[];
  final completers = <Completer<PostPage>>[];

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) {
    queries.add(query);
    final completer = Completer<PostPage>();
    completers.add(completer);
    return completer.future;
  }

  @override
  Future<PostDetail> getPost(PostRef reference) => throw UnimplementedError();

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) => throw UnimplementedError();
}

class ImmediateSearchAdapter implements SiteAdapter {
  ImmediateSearchAdapter(this.page);

  final PostPage page;
  final queries = <PostQuery>[];

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) async {
    queries.add(query);
    return page;
  }

  @override
  Future<PostDetail> getPost(PostRef reference) => throw UnimplementedError();

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) => throw UnimplementedError();
}

class QueuedSearchAdapter implements SiteAdapter {
  QueuedSearchAdapter(this.pages);

  final List<PostPage> pages;
  final queries = <PostQuery>[];

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) async {
    queries.add(query);
    return pages.removeAt(0);
  }

  @override
  Future<PostDetail> getPost(PostRef reference) => throw UnimplementedError();

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) => throw UnimplementedError();
}
