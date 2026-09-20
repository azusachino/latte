import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/design/latte_theme.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/features/explore/explore_screen.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  testWidgets('compact light Explore baseline', (tester) async {
    await _pump(tester, LatteTheme.light(), const Size(360, 720));
    await expectLater(
      find.byKey(const ValueKey('explore-golden-content')),
      matchesGoldenFile('../../goldens/explore/explore_compact_light.png'),
    );
  });

  testWidgets('compact dark Explore baseline', (tester) async {
    await _pump(tester, LatteTheme.dark(), const Size(360, 720));
    await expectLater(
      find.byKey(const ValueKey('explore-golden-content')),
      matchesGoldenFile('../../goldens/explore/explore_compact_dark.png'),
    );
  });

  testWidgets('expanded light Explore baseline', (tester) async {
    await _pump(tester, LatteTheme.light(), const Size(900, 720));
    await expectLater(
      find.byKey(const ValueKey('explore-golden-content')),
      matchesGoldenFile('../../goldens/explore/explore_expanded_light.png'),
    );
  });

  testWidgets('expanded dark Explore baseline', (tester) async {
    await _pump(tester, LatteTheme.dark(), const Size(900, 720));
    await expectLater(
      find.byKey(const ValueKey('explore-golden-content')),
      matchesGoldenFile('../../goldens/explore/explore_expanded_dark.png'),
    );
  });
}

Future<void> _pump(WidgetTester tester, ThemeData theme, Size size) async {
  await tester.binding.setSurfaceSize(size);
  addTearDown(() => tester.binding.setSurfaceSize(null));
  final controller = ExploreController(
    adapter: _GoldenAdapter(
      posts: [
        const PostSummary(
          reference: PostRef(siteId: SiteId('fixture'), remoteId: 'safe'),
          rating: PostRating.safe,
          width: 1200,
          height: 900,
        ),
        const PostSummary(
          reference: PostRef(siteId: SiteId('fixture'), remoteId: 'explicit'),
          rating: PostRating.explicit,
          width: 900,
          height: 1200,
        ),
      ],
    ),
  );
  await tester.pumpWidget(
    MediaQuery(
      data: MediaQueryData(size: size, disableAnimations: true),
      child: MaterialApp(
        theme: theme,
        home: ExploreScreen(controller: controller),
      ),
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

class _GoldenAdapter implements SiteAdapter {
  const _GoldenAdapter({required this.posts});

  final List<PostSummary> posts;

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fixture'), displayName: 'Fixture');

  @override
  Future<PostPage> queryPosts(PostQuery query) async => PostPage(posts: posts);

  @override
  Future<PostDetail> getPost(PostRef reference) async => PostDetail(
    summary: posts.singleWhere((post) => post.reference == reference),
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
