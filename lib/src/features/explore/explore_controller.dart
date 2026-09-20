import 'package:flutter/foundation.dart';

import '../../domain/explore_state.dart';
import '../../domain/failure.dart';
import '../../domain/popular_query.dart';
import '../../domain/post.dart';
import '../../sites/site_adapter.dart';

class ExploreController extends ChangeNotifier {
  ExploreController({required this.adapter, DateTime Function()? now})
    : _lastPopularQuery = PopularQuery(
        period: PopularPeriod.day,
        anchor: (now ?? _systemNow)(),
      );

  final SiteAdapter adapter;
  ExploreState _state = ExploreState.initial();
  ExploreState? _discoveryState;
  ExploreState? _browseState;
  ExploreState? _feedState;
  PopularQuery _lastPopularQuery;
  var _requestGeneration = 0;

  ExploreState get state => _state;

  PopularQuery get selectedPopularQuery =>
      _state.query?.popularQuery ?? _lastPopularQuery;

  Future<void> loadDiscovery() async {
    final query = PostQuery.discovery();
    final generation = ++_requestGeneration;
    _setState(ExploreState.initialLoading(query));
    try {
      final page = await adapter.queryPosts(query);
      if (generation != _requestGeneration) return;
      final state = page.posts.isEmpty
          ? ExploreState.empty(query)
          : ExploreState.content(query, page.posts, next: page.next);
      _discoveryState = state;
      _browseState = state;
      _feedState = state;
      _setState(state);
    } on Object catch (error) {
      if (generation != _requestGeneration) return;
      _setState(ExploreState.failure(query, _failure(error)));
    }
  }

  Future<void> loadPopular({PopularPeriod? period, DateTime? anchor}) async {
    final current = _lastPopularQuery;
    final queryValue = PopularQuery(
      period: period ?? current.period,
      anchor: anchor ?? current.anchor,
    );
    _lastPopularQuery = queryValue;
    final query = PostQuery.popular(queryValue);
    final generation = ++_requestGeneration;
    _setState(ExploreState.initialLoading(query));
    try {
      final page = await adapter.queryPosts(query);
      if (generation != _requestGeneration) return;
      final state = page.posts.isEmpty
          ? ExploreState.empty(query)
          : ExploreState.content(query, page.posts, next: page.next);
      _browseState = state;
      _feedState = state;
      _setState(state);
    } on Object catch (error) {
      if (generation != _requestGeneration) return;
      _setState(ExploreState.failure(query, _failure(error)));
    }
  }

  Future<void> shiftPopularAnchor(int amount) {
    final next = selectedPopularQuery.shifted(amount);
    return loadPopular(period: next.period, anchor: next.anchor);
  }

  Future<void> search(String expression) async {
    final query = PostQuery.tagSearch(expression);
    final generation = ++_requestGeneration;
    _setState(ExploreState.replacingQuery(query));
    try {
      final page = await adapter.queryPosts(query);
      if (generation != _requestGeneration) return;
      final state = page.posts.isEmpty
          ? ExploreState.noResults(query)
          : ExploreState.content(query, page.posts, next: page.next);
      _feedState = state;
      _setState(state);
    } on Object catch (error) {
      if (generation != _requestGeneration) return;
      _setState(ExploreState.failure(query, _failure(error)));
    }
  }

  Future<void> clearSearch() async {
    ++_requestGeneration;
    final discovery = _browseState ?? _discoveryState;
    if (discovery != null) {
      _feedState = discovery;
      _setState(discovery);
      return;
    }
    await loadDiscovery();
  }

  Future<void> loadNextPage() async {
    final feed = _feedState ?? _state;
    final query = feed.query;
    final continuation = feed.next;
    if (query == null || continuation == null) return;

    final generation = ++_requestGeneration;
    _setState(ExploreState.nextPageLoading(feed));
    try {
      final page = await adapter.queryPosts(
        query.withContinuation(continuation),
      );
      if (generation != _requestGeneration) return;
      final posts = _appendUnique(feed.posts, page.posts);
      final state = page.next == null
          ? ExploreState.endReached(ExploreState.content(query, posts))
          : ExploreState.content(query, posts, next: page.next);
      _feedState = state;
      _setState(state);
    } on Object catch (error) {
      if (generation != _requestGeneration) return;
      _setState(ExploreState.nextPageFailure(feed, _failure(error)));
    }
  }

  Future<void> openDetail(PostRef reference) async {
    final feed = _feedState ?? _state;
    final summary = feed.posts
        .where((post) => post.reference == reference)
        .firstOrNull;
    if (summary == null) return;

    final generation = ++_requestGeneration;
    _setState(ExploreState.detailLoading(feed, reference));
    try {
      final detail = await adapter.getPost(reference);
      if (generation != _requestGeneration) return;
      _setState(ExploreState.detail(feed, detail));
    } on Object catch (error) {
      if (generation != _requestGeneration) return;
      _setState(ExploreState.detailFailure(feed, _failure(error)));
    }
  }

  Future<void> openAdjacentDetail(int amount) async {
    final feed = _feedState ?? _state;
    final selected = _state.selectedReference;
    if (selected == null) return;
    final index = feed.posts.indexWhere((post) => post.reference == selected);
    final nextIndex = index + amount;
    if (index < 0 || nextIndex < 0 || nextIndex >= feed.posts.length) return;
    await openDetail(feed.posts[nextIndex].reference);
  }

  void closeDetail() {
    ++_requestGeneration;
    final feed = _feedState;
    if (feed != null) _setState(feed);
  }

  @override
  void dispose() {
    ++_requestGeneration;
    super.dispose();
  }

  void _setState(ExploreState state) {
    _state = state;
    notifyListeners();
  }

  static List<PostSummary> _appendUnique(
    List<PostSummary> current,
    List<PostSummary> incoming,
  ) {
    final result = List<PostSummary>.of(current);
    final seen = current.map((post) => post.reference).toSet();
    for (final post in incoming) {
      if (seen.add(post.reference)) result.add(post);
    }
    return result;
  }

  static SiteFailure _failure(Object error) => error is SiteFailureException
      ? error.failure
      : const SiteFailure(
          kind: SiteFailureKind.transportUnavailable,
          retryable: true,
          message: "Can't reach Yande.re",
        );

  static DateTime _systemNow() => DateTime.now().toUtc();
}
