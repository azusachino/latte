# Yande-compatible redesign: UX learning study

Date: 2026-09-20  
Status: read-only research for the Latte redesign. The owner decision recorded
below supersedes the earlier Safe Mode recommendation in this evidence note;
the observed reference behavior remains historical evidence, not a product
requirement.

## Scope and evidence boundary

This study follows the workflow the user actually relies on: choose Popular by
day, week, or month; browse a masonry feed; inspect a post; download the chosen
media. Authenticated scoring is treated as a separate capability that must not
be confused with browsing, downloading, favorites, or the displayed community
score.

Evidence used:

- The installed `com.github.yueeng.moebooru.yande` Android app (`1.0.9.1`)
  was observed on the connected OnePlus 8 on 2026-09-20. The observations below
  are interaction evidence, not a claim that the app is the target UI.
- The pinned open-source reference at
  [`refs/image-gallery-apps/moebooru`](../../../../refs/image-gallery-apps/moebooru/README.md)
  (`5bcf766`) supplies source-level behavior for the installed app. The
  `PopularActivity`, `PreviewActivity`, and `UserActivity` links below are the
  primary local references.
- Latte's current boundaries are recorded in
  [`product-experience.md`](../../specs/001-yandere-core-journey/contracts/product-experience.md#information-architecture)
  and its current Explore/Detail implementation is in
  [`explore_screen.dart`](../../lib/src/features/explore/explore_screen.dart#L117-L169).
- The current official pages were checked for route drift. The live
  [`popular_recent` page](https://yande.re/post/popular_recent) exposes recent
  popular-period links and post tiles with rating and aggregate score text.
  Latte's bounded anonymous `/post.json?limit=1` probe returned HTTP 200 and
  decoded one post on 2026-09-20, while the help-page behavior varied by client.
  Popular-period and authenticated mutation details remain compatibility
  requirements to verify through dedicated adapter spikes.

## 1. Observed information architecture and interaction map

The installed app makes Popular the practical home journey. It has a purple
toolbar with Search, Column, and Settings actions; a Popular/Newest tab row; a
staggered image grid; and a floating action button (FAB) that expands into
Popular by day, week, month, year, and all-time choices. Selecting a period
opens a period title plus date/window navigation. The reference source maps
those choices and their date windows in
[`PopularActivity.kt`](../../../../refs/image-gallery-apps/moebooru/app/src/main/java/com/github/yueeng/moebooru/PopularActivity.kt#L52-L143),
while the menu labels are explicit in
[`menu/popular.xml`](../../../../refs/image-gallery-apps/moebooru/app/src/main/res/menu/popular.xml#L2-L22).

The focused journey is:

```text
Launch
  -> Popular (default) or Newest
  -> choose Popular by day / week / month
  -> choose or advance the date/window
  -> scroll the aspect-preserving masonry feed
  -> tap a post
  -> image-first detail pager
       -> swipe to previous/next post
       -> inspect metadata, tags, source, and media variants
       -> download a selected variant
       -> authenticated star control (0..3), if signed in
  -> back
  -> return to the same period, date/window, feed position, and query context
```

The reference implementation supports the important detail interactions: an
image pager, progress state, tap-to-toggle controls, left/right navigation, and
a bottom sheet for tags and media actions in
[`PreviewActivity.kt`](../../../../refs/image-gallery-apps/moebooru/app/src/main/java/com/github/yueeng/moebooru/PreviewActivity.kt#L71-L159),
[`PreviewActivity.kt`](../../../../refs/image-gallery-apps/moebooru/app/src/main/java/com/github/yueeng/moebooru/PreviewActivity.kt#L215-L347),
and [`fragment_preview.xml`](../../../../refs/image-gallery-apps/moebooru/app/src/main/res/layout/fragment_preview.xml#L2-L102).

The scoring route is distinct. The observed star action opened a `You star 0`
screen with a visible 0–3 rating control, reset action, and groups of users by
their vote. The source fetches the current user's vote and calls
`post/vote.json` with a score from 0 through 3 in
[`UserActivity.kt`](../../../../refs/image-gallery-apps/moebooru/app/src/main/java/com/github/yueeng/moebooru/UserActivity.kt#L372-L451)
and [`Model.kt`](../../../../refs/image-gallery-apps/moebooru/app/src/main/java/com/github/yueeng/moebooru/Model.kt#L390-L397).

## 2. Compatibility invariants Latte must preserve

These are behavioral contracts, even though the presentation can be fully
redesigned.

1. **Popular is a first-class discovery entry point.** Day, week, and month
   must be visible choices, not hidden behind an unexplained query syntax. A
   selected period and its anchor date/window form part of the query identity.
2. **The feed is image-first and masonry-like.** Preserve each thumbnail's
   aspect ratio, keep dense scanning efficient, and page continuously without
   blanking already loaded content. The current Latte grid is fixed-column and
   has no period identity or score on its cards
   ([`explore_screen.dart`](../../lib/src/features/explore/explore_screen.dart#L232-L330));
   redesigning that presentation must not lose the image-first behavior.
3. **Popular ranking remains legible.** The aggregate community score and the
   selected period/window must be available as data for ranking and inspection,
   even if the redesigned card does not permanently overlay every fact.
4. **Inspect is reversible and contextual.** Opening a post must support
   previous/next navigation, loading/error states, and back navigation that
   restores the selected period, date/window, query, and approximate scroll
   position. The current Latte detail already exposes rating, dimensions,
   score, source, and tags as facts
   ([`explore_screen.dart`](../../lib/src/features/explore/explore_screen.dart#L397-L488)).
5. **Download is a local side effect.** The detail flow must expose the best
   available media variant and a clear local-download action with progress,
   completion, duplicate, and failure states. A download must never imply a
   remote vote or favorite.
6. **The four post states remain separate.**

   | State | Meaning | Required UI treatment |
   | --- | --- | --- |
   | Display aggregate score | Community score returned with the post; commonly used for Popular ordering | Read-only fact; do not render as the user's vote |
   | Authenticated personal score | The signed-in user's 0–3 star vote; 0 means reset/no vote | Interactive only when authenticated; show sign-in gate otherwise |
   | Favorite | A separate remote account relationship, if the site adapter supports it | Do not map it to a star or local save until the endpoint and semantics are verified |
   | Local download | Device file creation/queue state | Use download copy, progress, and local duplicate handling; never mutate remote score/favorite |

7. **Content policy is explicit.** The default remains visible content, as
   required by Latte's existing product contract. Mark explicit-rated content
   and keep Safe Mode as an optional, user-controlled filter. Safe Mode should
   be understandable before a user opens a detail and should block explicit
   detail/download actions when enabled; it must not silently change the
   Popular period or ranking.
8. **Site identity and post identity are stable.** Cache keys, detail routes,
   and download records must include the site plus remote post id; yande and a
   future Moebooru-compatible site must not collide.

## 3. Legacy presentation and interaction debt to redesign

The reference app is useful because it proves the workflow, but its surface is
not a good template for Latte:

| Observed debt | Redesign implication |
| --- | --- |
| Period choices are hidden in a FAB menu, and the current title/date context can be easy to miss | Put Day / Week / Month in an always-readable Material 3 control and show the anchor date/window beside it |
| Purple toolbar, floating controls, and full-screen overlays make state and hierarchy compete with the artwork | Use a quieter M3 surface system, a stable top context bar, and a persistent but unobtrusive action rail |
| Several detail controls are icon-only; the reference XML gives controls generic `Yande` content descriptions | Give every action a meaningful label, tooltip, state, and 48 dp target; make Download and Score discoverable without guessing |
| Cards emphasize dimensions/quality but do not clearly communicate period, rank, aggregate score, or rating | Use a restrained metadata line or accessible card semantics; keep the image dominant and facts available on inspect |
| The tag bottom sheet is dense and color-coded without a simple explanation of the categories | Group metadata into clear sections: score/rating, dimensions/media, tags, source, and actions; keep tags selectable but secondary |
| Star, favorite-like affordances, and download sit together visually | Separate remote account mutations from local download, and label personal score as “Your score” |
| Login is discovered only after invoking a protected action | Show an inline “Sign in to score” state and explain that browsing/download remain anonymous |
| A full-screen image can hide the feed context and the current post position | Add a compact “Popular · Week · 2026-09-20 · 12 of 80” context treatment and preserve it on back |

These are presentation debts, not reasons to discard compatibility. Latte's
current contract already calls for Material 3, compact/expanded adaptive detail,
accessible semantics, and explicit content by default
([`product-experience.md`](../../specs/001-yandere-core-journey/contracts/product-experience.md#material-3-design-language)).

## 4. Proposed Material 3 information architecture

Keep one primary destination, Explore, with progressive disclosure rather than
adding account, library, pools, or a site switcher to this slice. The proposed
IA is:

```text
Explore
  ├─ context bar: Latte, search, Safe Mode status
  ├─ discovery controls: Popular + Day | Week | Month + date/window rail
  ├─ masonry feed: image, loading/error state, optional compact facts
  └─ detail route/sheet
       ├─ image pager and position/context
       ├─ bottom action bar: Download, Your score, More
       └─ inspect sheet/pane: aggregate score, rating, dimensions, variants,
          tags, source, and policy state
```

### Feed anatomy

- Use a `Scaffold` with a Material 3 top app bar. Keep Search available but do
  not let it displace the Popular period control.
- Put Day / Week / Month in a segmented control or compact tab row with a
  visible selected state. Put date/window navigation in a horizontal rail below
  it; the selected anchor must be readable and restorable.
- Use an adaptive, aspect-preserving masonry grid. On compact widths, prioritize
  two columns; on expanded widths, increase columns without changing card
  semantics. Do not use a fixed `childAspectRatio` as the only layout rule.
- Keep cards quiet: image first, explicit-content marker when applicable, and
  a compact accessible description containing post id, aggregate score, rating,
  and dimensions. Avoid permanent metadata overlays that reduce the artwork.
- Show paging progress after existing content and provide an actionable retry
  state. Do not turn a temporary API failure into an empty Popular feed.

### Detail anatomy

- On compact screens, use a full-screen route with a visible top back/context
  affordance and a bottom action bar. On expanded screens, use a side-by-side
  image and inspect pane, matching Latte's existing adaptive intent.
- Keep swipe/next/previous navigation, zoom, and image loading progress. The
  inspect pane should remain available without making the user hunt through a
  tag sheet.
- Show aggregate score as a read-only fact. Show “Your score” as a separate
  0–3 control with a sign-in gate. Do not show a favorite control until its
  remote mapping is verified; if added later, give it a separate label and
  state.
- Download opens a small variant sheet (preview/sample/JPEG/original when
  available) with dimensions and an approximate size if known. The resulting
  progress and completion message belong to the local device flow.
- Safe Mode status is visible in the context bar and detail pane. Explicit
  default visibility is not hidden behind an unlabeled setting.

## 5. Staged capability plan

### Stage A: anonymous Popular and download

1. Define a site adapter operation that accepts `period = day | week | month`,
   anchor date/window, page, and page size, and returns normalized posts plus
   pagination metadata. The adapter owns yande.re route/query compatibility;
   do not hard-code a currently unverified `/post.json` assumption in the UI.
2. Render the three period choices, anchor navigation, loading/empty/error
   states, and an aspect-preserving paged masonry feed.
3. Open the image-first detail pager with aggregate score, rating, dimensions,
   tags, source, policy state, and media variants.
4. Add anonymous local download with foreground progress, duplicate detection,
   and retry. Keep the download target/device policy explicit.
5. Preserve the complete feed context through detail navigation and back.

This stage is enough to validate the user's dominant daily journey and should
not wait for account infrastructure.

### Stage B: authenticated personal scoring

1. Add an opaque authenticated session boundary owned by the site adapter; do
   not log credentials or make account details part of the anonymous feed
   model.
2. Fetch the current user's score for a post and render it as `Your score` in
   the range 0–3. The aggregate score remains a different read-only field.
3. Submit 0, 1, 2, or 3 through the verified vote contract; treat 0 as reset
   only after live verification. Optimistic UI must have rollback and an
   actionable failure state.
4. For anonymous users, the control must explain “Sign in to score” and must
   not issue a mutation. Favorite remains a later, separately verified
   capability; do not infer it from the score endpoint.

## 6. TDD seams and acceptance scenarios

Recommended seams are ports around unstable boundaries, not speculative
framework layers:

- `PopularQuery` value object: period, anchor date/window, page, and page size;
  test that day/week/month remain distinct and serializable.
- `YandePopularSource`/site adapter: maps a normalized query to the currently
  verified route, parses aggregate score/rating/media variants, and exposes
  pagination without leaking transport details into widgets.
- `ExploreController`: append pages without duplicates, preserve period/window
  on refresh and detail return, and surface loading/empty/error states.
- `PostDetailController`: maintain pager position, media variant selection, and
  download request independently of score/favorite state.
- `DownloadService`: resolve a variant, report progress/completion/duplicate/
  failure, and never call a remote vote or favorite operation.
- `ScoreService`: optional authenticated port with `read(postId) -> 0..3` and
  `write(postId, score: 0..3)`, including an auth-required result. It must not
  reuse the aggregate `Post.score` field.
- Semantics/widget tests: verify labels and 48 dp targets for period, download,
  sign-in, personal score, Safe Mode, and explicit-content states.

Acceptance scenarios:

1. On first launch, Explore shows Popular with Day selected, visible content by
   default, the masonry feed, and an explicit Safe Mode-off indicator.
2. Switching Day → Week → Month changes the query identity and visible anchor;
   loading another page does not clear existing cards or duplicate a post.
3. Opening a card shows its image, aggregate score, rating, and context. Swipe
   changes posts; back returns to the same period/window and feed position.
4. Downloading a chosen variant reports local progress and completion. A
   duplicate or failure is recoverable and does not change any remote score.
5. An anonymous user invoking Your score sees a sign-in explanation and no
   network mutation.
6. An authenticated user with current score 2 sees `Your score = 2`, can submit
   3, and can reset to 0. The post's aggregate score remains unchanged in the
   view model unless a fresh post response updates it.
7. A future favorite action, if enabled, has independent state and tests; a
   personal score of 3 is never rendered as a favorite.
8. Enabling Safe Mode hides explicit cards, prevents explicit detail/download,
   and leaves the selected Popular period and pagination context intact.

## 7. Open risks requiring authorized live verification

- **API route and period semantics:** the current yande.re help and old
  `/post.json` route were unavailable in today's check, while the web
  `popular_recent` page exposes only some period links. Verify the supported
  API/version, day/week/month query parameters, anchor-date semantics,
  pagination, rate limits, and score/rating fields with an authorized,
  non-destructive request before implementing the adapter.
- **Authentication and scoring contract:** obtain an authorized test account
  or documented credential flow to verify session establishment, current-vote
  lookup, 0–3 writes, reset behavior, CSRF/API-key requirements, and failure
  responses. Never put live credentials in source, fixtures, logs, or this
  document.
- **Favorite mapping:** verify whether the target site has a distinct favorite
  endpoint and how it is represented in post/account queries. The observed
  star endpoint is not evidence for a favorite mapping.
- **Media/download behavior:** verify redirects, original/sample/JPEG variant
  availability, content length, and Android MediaStore/duplicate behavior on
  the connected device in the planned device spike. Keep this separate from
  remote account mutations.
- **Policy behavior:** verify which rating values and explicit-content filters
  the live site returns. The product default remains explicit-visible with
  optional Safe Mode, but adapter filtering must be based on observed fields,
  not guessed tag names.

## 8. Owner decision after the learning session

On 2026-09-20 the owner selected the primary loop as **Popular by day/week/month
→ masonry exploration → image-first detail/pager → download**. Latte will keep
the selected period and anchor/window visible and restorable, preserve aggregate
rating/score metadata, and expose Popular and Newest as the primary discovery
modes.

The owner explicitly removed Safe Mode. All returned ratings, including
explicit, remain visible; Latte must not add a rating filter, policy toggle,
policy-conflict error, or query rewrite. Authenticated personal scoring from 0
through 3 is a later distinct slice and is not part of the current
implementation turn. This decision updates the active Spec Kit artifacts while
leaving the installed-app observations above intact as compatibility evidence.
