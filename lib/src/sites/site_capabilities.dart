import '../domain/post.dart';

/// Optional site behavior. The core [SiteAdapter] stays small so a new site
/// only implements the operations it actually supports.
class SiteCapabilities {
  const SiteCapabilities({
    this.tagSuggestions,
    this.relatedTags,
    this.authentication,
    this.remoteFavorites,
    this.personalScore,
    this.browserRoutes,
  });

  final TagSuggestionCapability? tagSuggestions;
  final RelatedTagCapability? relatedTags;
  final AuthenticationCapability? authentication;
  final RemoteFavoriteCapability? remoteFavorites;
  final PersonalScoreCapability? personalScore;
  final BrowserRoutesCapability? browserRoutes;
}

abstract interface class TagSuggestionCapability {
  Future<List<SiteTagSuggestion>> suggestTags(String prefix);
}

abstract interface class RelatedTagCapability {
  Future<List<String>> relatedTags(PostRef reference);
}

abstract interface class AuthenticationCapability {
  Future<void> signIn();

  Future<void> signOut();
}

abstract interface class RemoteFavoriteCapability {
  Future<bool> isFavorite(PostRef reference);

  Future<void> setFavorite(PostRef reference, bool favorite);
}

/// Reserved for the roadmap capability; it must never be shown unless a site
/// supplies a real authenticated implementation.
abstract interface class PersonalScoreCapability {
  Future<int?> score(PostRef reference);

  Future<void> setScore(PostRef reference, int score);
}

abstract interface class BrowserRoutesCapability {
  Uri postUri(PostRef reference);
}

class SiteTagSuggestion {
  const SiteTagSuggestion({required this.name, this.count, this.type});

  final String name;
  final int? count;
  final int? type;
}
