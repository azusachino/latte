import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/explore_state.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/features/explore/search_view.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  testWidgets('exposes a labelled SearchAnchor and focuses its view', (
    tester,
  ) async {
    final controller = ExploreController(adapter: SearchWidgetAdapter());
    await tester.pumpWidget(app(SearchView(controller: controller)));

    expect(find.byType(SearchAnchor), findsOneWidget);
    expect(find.byType(SearchBar), findsOneWidget);
    expect(find.text('Search tags'), findsOneWidget);

    await tester.tap(find.byType(SearchBar));
    await tester.pumpAndSettle();

    expect(find.byType(TextField), findsWidgets);
    expect(tester.binding.focusManager.primaryFocus, isNotNull);
  });

  testWidgets('submits the complete expression through the controller', (
    tester,
  ) async {
    final adapter = SearchWidgetAdapter();
    final controller = ExploreController(adapter: adapter);
    await tester.pumpWidget(app(SearchView(controller: controller)));

    await tester.tap(find.byType(SearchBar));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byType(TextField).last,
      'artist_name -tag:example order:score',
    );
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(
      adapter.queries.single.expression,
      'artist_name -tag:example order:score',
    );
    expect(controller.state.status, ExploreStatus.content);
  });

  testWidgets('clear restores the prior discovery feed', (tester) async {
    final adapter = SearchWidgetAdapter();
    final controller = ExploreController(adapter: adapter);
    await controller.loadDiscovery();
    await tester.pumpWidget(app(SearchView(controller: controller)));

    await tester.tap(find.byType(SearchBar));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'artist');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Clear search'));
    await tester.pumpAndSettle();

    expect(controller.state.query?.source, PostQuerySource.discovery);
    expect(controller.state.posts.single.reference.remoteId, 'discovery');
  });

  testWidgets('overflow exposes no content-policy controls', (tester) async {
    final controller = ExploreController(adapter: SearchWidgetAdapter());
    await tester.pumpWidget(app(SearchView(controller: controller)));

    expect(find.byTooltip('More options'), findsNothing);
    expect(find.text('Safe Mode'), findsNothing);
  });

  testWidgets('forwards an explicit search expression without filtering', (
    tester,
  ) async {
    final adapter = SearchWidgetAdapter();
    final controller = ExploreController(adapter: adapter);
    await tester.pumpWidget(app(SearchView(controller: controller)));

    await tester.tap(find.byType(SearchBar));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, 'rating:explicit');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(adapter.queries.single.expression, 'rating:explicit');
    expect(controller.state.status, ExploreStatus.content);
  });
}

MaterialApp app(Widget child) => MaterialApp(home: child);

class SearchWidgetAdapter implements SiteAdapter {
  final queries = <PostQuery>[];

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) async {
    queries.add(query);
    return PostPage(
      posts: [
        PostSummary(
          reference: PostRef(
            siteId: const SiteId('fake'),
            remoteId: query.source == PostQuerySource.discovery
                ? 'discovery'
                : 'result',
          ),
          rating: PostRating.safe,
        ),
      ],
    );
  }

  @override
  Future<PostDetail> getPost(PostRef reference) => throw UnimplementedError();

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) => throw UnimplementedError();
}
