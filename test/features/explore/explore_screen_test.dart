import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/design/latte_theme.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/features/explore/explore_screen.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  testWidgets('renders explicit discovery cards with labelled Safe Mode', (
    tester,
  ) async {
    final explicit = post('explicit', rating: PostRating.explicit);
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('safe'), explicit]),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
    expect(find.bySemanticsLabel('Post safe, safe'), findsOneWidget);
    expect(find.bySemanticsLabel('Post explicit, explicit'), findsOneWidget);
    expect(find.text('Safe Mode'), findsOneWidget);
    expect(tester.getSemantics(find.text('Safe Mode')).label, 'Safe Mode');
  });

  testWidgets('shows loading, empty, and failure states with actionable copy', (
    tester,
  ) async {
    final adapter = WidgetAdapter.deferred();
    final controller = ExploreController(adapter: adapter);
    await tester.pumpWidget(app(controller));
    await tester.pump();
    expect(find.byKey(const ValueKey('explore-loading')), findsOneWidget);

    adapter.complete(const PostPage(posts: []));
    await tester.pumpAndSettle();
    expect(find.text('No posts yet'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);

    final failed = ExploreController(adapter: WidgetAdapter.error());
    await tester.pumpWidget(app(failed));
    await failed.loadDiscovery();
    await tester.pump();
    expect(find.text("Can't reach Yande.re"), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });

  testWidgets('opens detail and back restores the discovery grid', (
    tester,
  ) async {
    final item = post('detail', tags: const ['one', 'two']);
    final controller = ExploreController(adapter: WidgetAdapter(posts: [item]));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsLabel('Post detail, safe'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-detail')), findsOneWidget);
    expect(find.text('one'), findsOneWidget);
    expect(find.text('two'), findsOneWidget);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
    expect(find.bySemanticsLabel('Post detail, safe'), findsOneWidget);
  });

  testWidgets(
    'fits expanded dark layouts with 200 percent text and reduced motion',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(900, 700));
      addTearDown(() {
        tester.binding.setSurfaceSize(null);
      });
      final item = post(
        'wide',
        tags: List<String>.generate(8, (index) => 'tag$index'),
      );
      final controller = ExploreController(
        adapter: WidgetAdapter(posts: [item]),
      );
      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(
            disableAnimations: true,
            textScaler: TextScaler.linear(2),
          ),
          child: MaterialApp(
            theme: LatteTheme.dark(),
            home: ExploreScreen(controller: controller),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('expanded-explore')), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}

MaterialApp app(ExploreController controller) => MaterialApp(
  theme: LatteTheme.light(),
  darkTheme: LatteTheme.dark(),
  home: ExploreScreen(controller: controller),
);

PostSummary post(
  String id, {
  PostRating rating = PostRating.safe,
  List<String> tags = const ['fixture'],
}) => PostSummary(
  reference: PostRef(siteId: const SiteId('fake'), remoteId: id),
  rating: rating,
  tags: tags,
  width: 1200,
  height: 800,
);

class WidgetAdapter implements SiteAdapter {
  WidgetAdapter({required this.posts})
    : _posts = posts,
      _error = false,
      _pending = null;

  WidgetAdapter.deferred()
    : posts = const [],
      _posts = const [],
      _error = false,
      _pending = Completer<PostPage>();

  WidgetAdapter.error()
    : posts = const [],
      _posts = const [],
      _error = true,
      _pending = null;

  final List<PostSummary> posts;
  final List<PostSummary> _posts;
  final bool _error;
  final Completer<PostPage>? _pending;

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) {
    if (_error) return Future.error(StateError('network'));
    if (_pending != null) return _pending.future;
    return Future.value(PostPage(posts: _posts));
  }

  void complete(PostPage page) {
    final pending = _pending;
    if (pending != null) pending.complete(page);
  }

  @override
  Future<PostDetail> getPost(PostRef reference) async => PostDetail(
    summary: _posts.singleWhere((post) => post.reference == reference),
    media: const [],
  );

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) {
    throw UnimplementedError();
  }
}
