import 'dart:async';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/app.dart';
import 'package:latte/src/design/latte_theme.dart';
import 'package:latte/src/domain/popular_query.dart';
import 'package:latte/src/domain/post.dart';
import 'package:latte/src/features/explore/explore_controller.dart';
import 'package:latte/src/features/explore/explore_screen.dart';
import 'package:latte/src/sites/site_adapter.dart';

void main() {
  testWidgets('renders explicit discovery cards without a content filter', (
    tester,
  ) async {
    final explicit = post('explicit', rating: PostRating.explicit);
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('safe'), explicit]),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
    expect(find.bySemanticsLabel('Post safe'), findsOneWidget);
    expect(find.bySemanticsLabel('Post explicit'), findsOneWidget);
    expect(find.text('Safe Mode'), findsNothing);
  });

  testWidgets(
    'shows reference-derived Explore composition and period surface',
    (tester) async {
      final adapter = WidgetAdapter(posts: [post('popular')]);
      final controller = ExploreController(
        adapter: adapter,
        now: () => DateTime.utc(2026, 9, 20),
      );
      await tester.pumpWidget(app(controller));
      await tester.pumpAndSettle();

      expect(find.byKey(const ValueKey('explore-tabs')), findsOneWidget);
      expect(find.text('Popular'), findsOneWidget);
      expect(find.text('Newest'), findsOneWidget);
      expect(find.byTooltip('Search'), findsOneWidget);
      expect(find.byTooltip('Settings'), findsOneWidget);
      expect(find.byTooltip('Choose popular period'), findsOneWidget);

      await tester.tap(find.byTooltip('Choose popular period'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('popular-period-sheet')),
        findsOneWidget,
      );
      expect(find.text('Day'), findsOneWidget);
      expect(find.text('Week'), findsOneWidget);
      expect(find.text('Month'), findsOneWidget);
      expect(find.text('2026-09-20'), findsOneWidget);
      expect(find.byTooltip('Previous period'), findsOneWidget);
      expect(find.byTooltip('Next period'), findsOneWidget);

      await tester.tap(find.text('Week'));
      await tester.pumpAndSettle();

      expect(controller.state.query?.popularQuery?.period, PopularPeriod.week);
      expect(
        controller.state.query?.popularQuery?.window.start,
        DateTime.utc(2026, 9, 14),
      );
    },
  );

  testWidgets('opens global settings from the Explore toolbar action', (
    tester,
  ) async {
    await tester.pumpWidget(
      LatteApp(adapter: WidgetAdapter(posts: [post('settings')])),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();

    expect(find.text('Appearance'), findsOneWidget);
    expect(find.text('System default'), findsOneWidget);
    expect(find.text('Light'), findsOneWidget);
    expect(find.text('Dark'), findsOneWidget);
    expect(find.text('Explore'), findsOneWidget);
    expect(find.text('Automatic'), findsOneWidget);
    expect(find.text('2 columns'), findsOneWidget);
    expect(find.text('3 columns'), findsOneWidget);
    expect(find.text('4 columns'), findsOneWidget);

    await tester.tap(find.text('3 columns'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-columns-3')), findsOneWidget);
  });

  testWidgets('swipes between Popular and Newest feeds', (tester) async {
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('tab-swipe')]),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Newest'));
    await tester.pumpAndSettle();
    expect(controller.state.query?.source, PostQuerySource.discovery);

    await tester.tap(find.text('Popular'));
    await tester.pumpAndSettle();
    expect(controller.state.query?.source, PostQuerySource.popular);

    await tester.drag(
      find.byKey(const ValueKey('explore-grid')),
      const Offset(-400, 0),
    );
    await tester.pumpAndSettle();
    expect(controller.state.query?.source, PostQuerySource.discovery);

    await tester.drag(
      find.byKey(const ValueKey('explore-grid')),
      const Offset(400, 0),
    );
    await tester.pumpAndSettle();
    expect(controller.state.query?.source, PostQuerySource.popular);
  });

  testWidgets('gallery cards expose only image semantics', (tester) async {
    final item = post(
      'scored',
      rating: PostRating.explicit,
      score: 17,
      width: 1200,
      height: 800,
    );
    final controller = ExploreController(adapter: WidgetAdapter(posts: [item]));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel('Post scored'), findsOneWidget);
    expect(find.text('score 17'), findsNothing);
    expect(find.text('1200 × 800'), findsNothing);
  });

  testWidgets('acknowledges a background download in the app', (tester) async {
    const channel = MethodChannel('com.azusachino.latte/download');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(channel, (call) async {
      return <String, Object?>{
        'status': 'started',
        'album': 'Pictures/Latte',
        'displayName': 'latte_fake_download_jpeg.jpg',
      };
    });
    addTearDown(() => messenger.setMockMethodCallHandler(channel, null));

    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('download')]),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('Post download'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Download'));
    await tester.pumpAndSettle();

    expect(find.text('Download started in the background.'), findsOneWidget);
    final toast = tester.widget<Material>(
      find.byKey(const ValueKey('download-toast')),
    );
    expect(toast.type, MaterialType.transparency);
    expect(toast.color, isNull);
    expect(
      tester.getTopLeft(find.byKey(const ValueKey('download-toast'))).dy,
      lessThan(
        tester.view.physicalSize.height / tester.view.devicePixelRatio / 2,
      ),
    );
  });

  testWidgets('detail pager keeps Popular context and moves between posts', (
    tester,
  ) async {
    final items = [post('first'), post('second')];
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: items),
      now: () => DateTime.utc(2026, 9, 20),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsLabel('Post first'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('detail-pager')), findsOneWidget);
    expect(find.byKey(const ValueKey('detail-inspect-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('detail-actions')), findsOneWidget);
    expect(find.byTooltip('Download'), findsOneWidget);
    expect(find.byTooltip('Expand details'), findsOneWidget);
    expect(find.textContaining('1 of 2'), findsOneWidget);
    expect(find.byTooltip('Previous post'), findsOneWidget);
    expect(find.byTooltip('Next post'), findsOneWidget);

    await tester.tap(find.byTooltip('Expand details'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Collapse details'), findsOneWidget);
    await tester.tap(find.byTooltip('Collapse details'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Expand details'), findsOneWidget);

    await tester.tap(find.byTooltip('Next post'));
    await tester.pumpAndSettle();
    expect(find.textContaining('2 of 2'), findsOneWidget);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Choose popular period'));
    await tester.pumpAndSettle();
    expect(find.text('Day'), findsOneWidget);
    expect(find.text('2026-09-20'), findsOneWidget);
  });

  testWidgets('swipes between posts in the detail pager', (tester) async {
    final items = [post('swipe-first'), post('swipe-second')];
    final controller = ExploreController(adapter: WidgetAdapter(posts: items));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsLabel('Post swipe-first'));
    await tester.pumpAndSettle();

    await tester.drag(
      find.byKey(const ValueKey('detail-pager')),
      const Offset(-400, 0),
    );
    await tester.pumpAndSettle();

    expect(find.textContaining('2 of 2'), findsOneWidget);
    expect(controller.state.selectedReference?.remoteId, 'swipe-second');
  });

  testWidgets('supports two-finger image zoom without taking pager swipes', (
    tester,
  ) async {
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('zoom')]),
    );
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.bySemanticsLabel('Post zoom'));
    await tester.pumpAndSettle();
    final image = tester.widget<ExtendedImage>(
      find.byKey(const ValueKey('detail-high-quality-image')),
    );
    await tester.runAsync(
      () => precacheImage(
        image.image,
        tester.element(find.byKey(const ValueKey('detail-high-quality-image'))),
      ),
    );
    await tester.pumpAndSettle();
    final loadedImage = tester.widget<ExtendedImage>(
      find.byKey(const ValueKey('detail-high-quality-image')),
    );
    final gestureKey =
        loadedImage.extendedImageGestureKey!
            as GlobalKey<ExtendedImageGestureState>;
    gestureKey.currentState!.handleScaleStart(
      ScaleStartDetails(focalPoint: Offset.zero),
    );
    gestureKey.currentState!.handleScaleUpdate(
      ScaleUpdateDetails(focalPoint: Offset.zero, scale: 2, pointerCount: 2),
    );
    await tester.pumpAndSettle();
    expect(gestureKey.currentState?.gestureDetails?.totalScale, greaterThan(1));
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

    await tester.tap(find.bySemanticsLabel('Post detail'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-detail')), findsOneWidget);
    await tester.drag(
      find.byKey(const ValueKey('detail-inspect-sheet')),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();
    expect(find.text('one'), findsOneWidget);
    expect(find.text('two'), findsOneWidget);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
    expect(find.bySemanticsLabel('Post detail'), findsOneWidget);
  });

  testWidgets('detail tags launch the shared tag search workflow', (
    tester,
  ) async {
    final item = post(
      'tagged-detail',
      tags: const ['artist', 'blue_eyes', 'character', 'background'],
    );
    final controller = ExploreController(adapter: WidgetAdapter(posts: [item]));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsLabel('Post tagged-detail'));
    await tester.pumpAndSettle();
    await tester.drag(
      find.byKey(const ValueKey('detail-inspect-sheet')),
      const Offset(0, -300),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('detail-tag-artist')), findsOneWidget);
    expect(find.byTooltip('Search tag artist'), findsOneWidget);
    final chip = tester.widget<ActionChip>(
      find.byKey(const ValueKey('detail-tag-artist')),
    );
    expect(chip.backgroundColor, isNotNull);
    final chipColors = ['artist', 'blue_eyes', 'character', 'background']
        .map(
          (tag) => tester
              .widget<ActionChip>(find.byKey(ValueKey('detail-tag-$tag')))
              .backgroundColor,
        )
        .toSet();
    expect(chipColors.length, greaterThanOrEqualTo(3));
    expect(find.text('Dimensions'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('detail-tag-artist')));
    await tester.pumpAndSettle();

    expect(controller.state.query?.source, PostQuerySource.tagSearch);
    expect(controller.state.query?.expression, 'artist');
    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
    expect(find.byKey(const ValueKey('explore-detail')), findsNothing);
    expect(find.byKey(const ValueKey('explore-tabs')), findsNothing);
    expect(find.text('Search results'), findsOneWidget);
  });

  testWidgets('platform back from detail restores the discovery grid', (
    tester,
  ) async {
    final item = post('platform-back');
    final controller = ExploreController(adapter: WidgetAdapter(posts: [item]));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.tap(find.bySemanticsLabel('Post platform-back'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('explore-detail')), findsOneWidget);

    await tester.binding.handlePopRoute();
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('explore-grid')), findsOneWidget);
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

  testWidgets('keeps compact controls available at 200 percent text', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = ExploreController(
      adapter: WidgetAdapter(posts: [post('compact')]),
    );
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(2)),
        child: app(controller),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Safe Mode'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('restores the feed scroll position after detail back', (
    tester,
  ) async {
    final items = List<PostSummary>.generate(20, (index) => post('$index'));
    final controller = ExploreController(adapter: WidgetAdapter(posts: items));
    await tester.pumpWidget(app(controller));
    await tester.pumpAndSettle();

    await tester.drag(
      find.byKey(const ValueKey('explore-grid')),
      const Offset(0, -500),
    );
    await tester.pump();
    final scrollController = tester
        .widget<ListView>(find.byKey(const ValueKey('explore-grid')))
        .controller!;
    final before = scrollController.offset;
    expect(before, greaterThan(0));

    await controller.openDetail(items.first.reference);
    await tester.pumpAndSettle();
    controller.closeDetail();
    await tester.pumpAndSettle();

    expect(scrollController.offset, closeTo(before, 0.1));
  });
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
  int? score,
  int? width = 1200,
  int? height = 800,
}) => PostSummary(
  reference: PostRef(siteId: const SiteId('fake'), remoteId: id),
  rating: rating,
  tags: tags,
  score: score,
  width: width,
  height: height,
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
  final queries = <PostQuery>[];

  @override
  SiteDescriptor get descriptor =>
      const SiteDescriptor(id: SiteId('fake'), displayName: 'Fake');

  @override
  Future<PostPage> queryPosts(PostQuery query) {
    queries.add(query);
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
    media: const [
      MediaVariant(
        id: MediaVariantId.jpeg,
        width: 1200,
        height: 800,
        extension: 'jpg',
      ),
    ],
  );

  @override
  Future<ResolvedMedia> resolveMedia(
    PostRef reference,
    MediaVariantId variant,
  ) async => ResolvedMedia(
    reference: reference,
    variant: MediaVariant(id: variant),
    source: Uri(scheme: 'asset', path: 'assets/branding/latte-icon.png'),
  );
}
