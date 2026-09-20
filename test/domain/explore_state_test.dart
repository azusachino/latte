import 'package:flutter_test/flutter_test.dart';
import 'package:latte/src/domain/failure.dart';
import 'package:latte/src/domain/explore_state.dart';
import 'package:latte/src/domain/post.dart';

void main() {
  const query = PostQuery.discovery();

  test('initial and loading states are distinct', () {
    expect(
      ExploreState.initial(),
      isNot(equals(ExploreState.initialLoading(query))),
    );
    expect(
      ExploreState.initialLoading(query).status,
      ExploreStatus.initialLoading,
    );
  });

  test('content state retains ordered posts and continuation', () {
    const posts = [
      PostSummary(
        reference: PostRef(siteId: SiteId('yandere'), remoteId: '1'),
        rating: PostRating.safe,
        tags: ['one'],
      ),
    ];
    final state = ExploreState.content(query, posts, next: 'page-2');

    expect(state.status, ExploreStatus.content);
    expect(state.posts, posts);
    expect(state.next, 'page-2');
    expect(state, equals(ExploreState.content(query, posts, next: 'page-2')));
  });

  test('empty, no-results, and failure remain observable states', () {
    expect(ExploreState.empty(query).status, ExploreStatus.empty);
    expect(
      ExploreState.noResults(PostQuery.tagSearch('nothing')).status,
      ExploreStatus.noResults,
    );

    const failure = SiteFailure(
      kind: SiteFailureKind.transportUnavailable,
      retryable: true,
      message: 'retry',
    );
    final state = ExploreState.failure(query, failure);

    expect(state.status, ExploreStatus.failure);
    expect(state.failure, failure);
  });
}
