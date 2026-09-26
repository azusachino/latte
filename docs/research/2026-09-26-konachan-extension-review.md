# Konachan extension review

**Date**: 2026-09-26
**Subject**: How smoothly does adding a third platform work, and where is the
friction for a future platform?

## Verdict

Adding `konachan.net` is smooth at the transport/plugin boundary, but not yet
smooth at the application boundary.

The implementation needed only:

- a `PlatformId` entry;
- a thin `KonachanPlugin` reusing the Moebooru implementation;
- constructor-injected site identity and base URL;
- composition-root registration; and
- platform-specific palette and menu wiring.

That is a good result for another site with Yande's protocol and behavior.

## Friction found

### 1. The ViewModel still knows transport families

`ExploreViewModel` owns both a Yande API and a Konachan API and selects between
them. A fourth Moebooru site would require another ViewModel edit. The
fallback also treats every non-Konachan site as Yande, which is accidental
rather than a plugin contract.

The next boundary should be a `MoebooruFeedSource` (or equivalent site-owned
source) selected by the plugin, with the ViewModel consuming only
`PluginFeedSource`.

### 2. Feed state is still split by Pixiv versus non-Pixiv

The application maintains parallel state and action paths for popular,
newest, favorites, search, pagination, refresh, and errors. A platform with a
new feed shape would require changes across `ExploreViewModel` and
`ExploreScreen`.

The target is one neutral feed-state map keyed by `PluginFeedKind`, with tabs
and supported operations declared by the plugin.

### 3. Plugin tabs must be authoritative

The first common-layer draft supplied Yande-style tabs as the default for every
plugin. That was unsafe: a plugin could expose tabs it cannot implement. The
default is now empty, and concrete plugins declare their own tabs. Pixiv owns
Following/Popular/Favorites; Yande and Konachan inherit the explicit
Moebooru tab declaration.

### 4. Composition registration had duplicated knowledge

The UI initially received individual plugin parameters plus a separate manual
platform map. `SitePluginManager.byPlatform()` now provides the registry map,
so the composition root has one source of truth for platform/plugin identity.

The individual plugin parameters remain temporarily because older login and
account flows still consume them; they should be removed once those flows use
the registry.

### 5. UI identity assumptions were exposed

The experiment found binary `isPixiv` assumptions in tab counts, labels,
search suggestions, login prompts, and feed rendering. These are not blocking
for Konachan because it behaves like Yande, but they make a different fourth
platform expensive and risky.

## Extension-friction score

| Area | Assessment |
| --- | --- |
| Same-engine platform | Low friction |
| Different feed API | High friction |
| Platform-specific tabs | Medium friction, improving |
| Avoiding UI/ViewModel edits | High friction today |
| Plugin seam overall | Promising, incomplete |

## Follow-up found during device acceptance

The first Konachan favorite was routed to Yande because `YandePostDto.toDomain()`
hardcoded `PlatformId.YANDE`. The Konachan API adapter now supplies its configured
platform during DTO mapping. Device verification on 2026-09-26 followed
Konachan.net → Popular → Week → first post (#408739) → Add to favorites →
Favorites; the Konachan feed response contained post #408739. A regression test
asserts that the Moebooru adapter preserves `PlatformId.KONACHAN`.

## Recommended next slice

1. Move Moebooru feed operations behind a plugin-owned neutral feed source.
2. Replace parallel Pixiv/non-Pixiv feed state with a neutral feed map.
3. Make search, favorites, author work, and authentication requirements plugin
   capabilities rather than platform identity checks.
4. Remove the remaining `sitePlugin`/`pixivPlugin` UI parameters in favor of
   the registry.
5. Add a fake third/fourth plugin test fixture that has a deliberately
   different tab set, proving the UI does not assume Yande or Pixiv behavior.

## Conclusion

Konachan validated the reuse seam, but also showed that the current seam ends
too low in the stack. The next platform will be genuinely cheap only after
feed state and UI behavior move behind the neutral plugin contracts.
