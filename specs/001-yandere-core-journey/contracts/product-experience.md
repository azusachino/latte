# Contract: product experience

## Information architecture

```text
Explore
├── Popular (Day | Week | Month)
│   └── readable anchor/window rail
├── Newest feed
├── Tag search results
└── transient loading/empty/failure states
    └── Post detail/pager
        └── Save sheet/progress/result
```

There is no account, library-history, pools, or site-switcher screen in this
milestone. A global Settings screen is available from the Explore app-bar
action for app-wide appearance and grid-density choices; it is not a
replacement for the reference discovery destinations. Material 3 is the
implementation language for the rework; the Yande-derived component
composition, workflow, and dataflow remain the experience source of truth.

Ratings remain adapter/search metadata, but are not rendered in the Latte UI.
There is no Safe Mode filter, policy toggle, or rating-driven query rewrite. The
aggregate community score is read-only in this milestone; authenticated `Your
score` from 0 through 3 is a later distinct capability.

The selected site comes from a site registry. Explore, detail, and download
consume the required site adapter contract only; optional site actions appear
only when the selected adapter exposes the matching capability port. This keeps
Yande.re first without making it the shared product model for future platforms.

## Explore screen

- Material 3 top app bar: Latte title, one labelled search action, and a
  labelled Settings action. The app bar may modernize the reference treatment,
  but it keeps the same discovery roles.
- A horizontal Material tab row keeps Popular and Newest as the primary modes.
- Search uses Material 3 `SearchAnchor`/`SearchBar` behavior; submitting replaces
  content intent while keeping discovery state available for clear/back.
- A Material FAB opens the Popular period/date surface. It offers Day, Week, and
  Month plus readable anchor/window navigation without changing the selected
  query identity. The controls are progressive disclosure, not a second primary
  navigation row.
- Body: an edge-to-edge, aspect-preserving staggered image feed selected from
  available width rather than device class. Compact windows start at two
  columns; Settings offers automatic or explicit 2/3/4-column density for the
  current session. Cards use theme surface roles without metadata overlays
  competing with artwork.
- Next-page progress appears after the last content row and never replaces
  already visible posts.
- Initial empty/failure states occupy the content region with one concise reason
  and one valid next action.
- Exploration cards render only image thumbnails. Aggregate score is available
  in the detail sheet when known and is never treated as a personal vote.

## Detail screen

- The selected inspection image dominates the first viewport and supports
  horizontal swipe plus previous/next pager navigation within the active feed.
  Arrow controls remain available as an accessible alternative.
- A compact context line keeps Popular period, anchor/window, and pager position
  readable while the image is open.
- A peekable Material bottom sheet groups the current post's metadata, tags, and
  available actions. It may be expanded without leaving the image-first pager.
- A compact facts row contains dimensions, score, and source when known.
- Tags wrap below the facts and remain selectable as text; tag-to-search is
  deferred unless separately specified.
- The image first shows the loaded thumbnail, then replaces it with the selected
  higher-quality detail variant when that transfer completes. The peek row uses
  the reference's compact core operation layout: Download in the center and
  expand/inspect at the trailing edge. Authenticated scoring is omitted until
  its roadmap capability exists.
- Download saves the best available media variant through the Android MediaStore
  boundary by default, without overwriting an existing `Pictures/Latte` item.
- Two-finger pinch scales the detail image up to inspect it; one-finger
  horizontal swipes remain pager navigation.
- Back restores prior feed/query and scroll position.

## Save interaction

- The primary Download action selects the best available variant without an
  extra quality picker.
- Download starts a native background task and returns the detail UI to its
  normal interactive state immediately. Android's native download notification
  reports transfer, completion, duplicate, and failure states while the detail
  image remains visible. Each active save owns its progress notification;
  concurrent saves remain separate entries in the Latte notification group.
- Completion reports the public Latte album and provides a system-supported
  open/view action.
- Existing-file, retryable, and terminal outcomes use distinct copy.
- Duplicate detection is authoritative across restart: query published
  `Pictures/Latte` MediaStore entries before transfer and re-check before
  publication; show `Already saved` with the existing content URI/album/name and
  never overwrite bytes.

## Material 3 rework boundary

- `MaterialApp` owns one light and one dark `ThemeData`; components consume
  semantic `ColorScheme`, `TextTheme`, shape, elevation, spacing, and motion
  roles rather than feature-local raw color or typography values.
- Material 3 may rework palette, typography, surfaces, and motion while artwork
  remains dominant. It must not replace the reference-derived toolbar roles,
  Popular/Newest hierarchy, period/date affordance, staggered feed, image pager,
  or detail action-sheet workflow.
- SDK Material components are preferred: top app bar, search, cards, chips,
  bottom sheet, buttons, progress, snackbars, and dialogs. A custom component
  needs a behavior the SDK component cannot express and its own state tests.
- Compact layout is single-pane. At expanded width, detail may place image and
  metadata side by side; the navigation model remains Explore → Detail and no
  speculative navigation rail or destination is introduced.
- Android platform back from detail is an in-app return to Explore. Horizontal
  pager swipe changes posts; it must never be interpreted as app exit.
- Motion uses Material defaults and communicates continuity only. Reduced-motion
  mode removes nonessential transitions without removing progress or state.
- Golden baselines are reviewed artifacts, not the only assertion: widget tests
  also verify semantics, actions, overflow, and state.

## Accessibility and motion

- Every actionable element has a semantic label and a minimum 48 by 48 logical
  pixel target.
- Reading and focus order follows app bar → discovery mode → period/anchor →
  content → pagination, then image/pager → actions → facts → tags in detail.
- Text and meaningful icons meet WCAG AA contrast against their surfaces.
- The experience remains complete with animation disabled; loading never relies
  on motion alone.
- Images use concise post-aware semantics when meaningful metadata exists and
  are marked decorative when none exists.

## State copy vocabulary

- `No posts yet`: valid discovery returned none.
- `No matches`: a tag search returned none.
- `Can't reach Yande.re`: transport is unavailable; retry is owner initiated.
- `Yande.re asked us to slow down`: throttled; show retry timing when known.
- `Yande.re returned an unreadable response`: malformed remote data.
- `Yande.re is unavailable`: bounded retry ended or remote failure is terminal.
- `Already saved`: target exists and was not changed.
