import 'failure.dart';
import 'post.dart';

enum ExploreStatus {
  initial,
  initialLoading,
  content,
  empty,
  noResults,
  replacingQuery,
  nextPageLoading,
  endReached,
  nextPageFailure,
  failure,
}

class ExploreState {
  const ExploreState._({
    required this.status,
    this.query,
    this.posts = const [],
    this.next,
    this.failure,
  });

  const ExploreState.initial() : this._(status: ExploreStatus.initial);

  const ExploreState.initialLoading(PostQuery query)
    : this._(status: ExploreStatus.initialLoading, query: query);

  factory ExploreState.content(
    PostQuery query,
    List<PostSummary> posts, {
    String? next,
  }) {
    return ExploreState._(
      status: ExploreStatus.content,
      query: query,
      posts: List.unmodifiable(posts),
      next: next,
    );
  }

  const ExploreState.empty(PostQuery query)
    : this._(status: ExploreStatus.empty, query: query);

  const ExploreState.noResults(PostQuery query)
    : this._(status: ExploreStatus.noResults, query: query);

  const ExploreState.replacingQuery(PostQuery query)
    : this._(status: ExploreStatus.replacingQuery, query: query);

  factory ExploreState.nextPageLoading(ExploreState content) {
    return ExploreState._(
      status: ExploreStatus.nextPageLoading,
      query: content.query,
      posts: content.posts,
      next: content.next,
    );
  }

  factory ExploreState.endReached(ExploreState content) {
    return ExploreState._(
      status: ExploreStatus.endReached,
      query: content.query,
      posts: content.posts,
    );
  }

  factory ExploreState.nextPageFailure(
    ExploreState content,
    SiteFailure failure,
  ) {
    return ExploreState._(
      status: ExploreStatus.nextPageFailure,
      query: content.query,
      posts: content.posts,
      next: content.next,
      failure: failure,
    );
  }

  const ExploreState.failure(PostQuery query, SiteFailure failure)
    : this._(status: ExploreStatus.failure, query: query, failure: failure);

  final ExploreStatus status;
  final PostQuery? query;
  final List<PostSummary> posts;
  final String? next;
  final SiteFailure? failure;

  @override
  bool operator ==(Object other) =>
      other is ExploreState &&
      other.status == status &&
      other.query == query &&
      _listEquals(other.posts, posts) &&
      other.next == next &&
      other.failure == failure;

  @override
  int get hashCode =>
      Object.hash(status, query, Object.hashAll(posts), next, failure);
}

bool _listEquals<T>(List<T> left, List<T> right) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}
