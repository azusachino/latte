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

There is no account, library-history, settings, pools, or site-switcher screen in
this milestone.

All returned ratings, including explicit, remain visible as metadata. There is no
Safe Mode filter, policy toggle, or rating-driven query rewrite. The aggregate
community score is read-only in this milestone; authenticated `Your score` from
0 through 3 is a later distinct capability.

## Explore screen

- Material 3 medium top app bar: Latte title, Popular/Newest navigation, and one
  labelled search action.
- Search uses Material 3 `SearchAnchor`/`SearchBar` behavior; submitting replaces
  content intent while keeping discovery state available for clear/back.
- Body: visible Day/Week/Month segmented control and a readable anchor/window
  rail above an edge-to-edge image-first masonry feed selected from available
  width rather than device class. Compact windows start at two columns; cards
  preserve image aspect ratio and use theme surface roles with no metadata
  overlay competing with artwork.
- Next-page progress appears after the last content row and never replaces
  already visible posts.
- Initial empty/failure states occupy the content region with one concise reason
  and one valid next action.
- Cards and detail expose rating and aggregate score semantics without treating
  either as a personal vote.

## Detail screen

- The selected inspection image dominates the first viewport and supports
  previous/next pager navigation within the active feed.
- A compact context line keeps Popular period, anchor/window, and pager position
  readable while the image is open.
- A compact facts row contains rating, dimensions, score, and source when known.
- Tags wrap below the facts and remain selectable as text; tag-to-search is
  deferred unless separately specified.
- Media quality and save action remain reachable without obscuring the image.
- Back restores prior feed/query and scroll position.

## Save interaction

- A Material 3 modal bottom sheet presents available labelled variants.
- One foreground linear progress surface reports transfer state and disables
  duplicate submission without removing context.
- Completion reports the public Latte album and provides a system-supported
  open/view action.
- Existing-file, retryable, and terminal outcomes use distinct copy.

## Material 3 design language

- `MaterialApp` owns one light and one dark `ThemeData`; components consume
  semantic `ColorScheme`, `TextTheme`, shape, elevation, spacing, and motion
  roles rather than feature-local raw color or typography values.
- The palette is quiet and neutral so artwork remains dominant. Primary color
  marks action and focus, not decoration; gradients, glass surfaces, heavy
  shadows, and uniformly oversized rounding are outside the visual language.
- SDK Material components are preferred: top app bar, search, cards, chips,
  bottom sheet, buttons, progress, snackbars, and dialogs. A custom component
  needs a behavior the SDK component cannot express and its own state tests.
- Compact layout is single-pane. At expanded width, detail may place image and
  metadata side by side; the navigation model remains Explore → Detail and no
  speculative navigation rail or destination is introduced.
- Motion uses Material defaults and communicates continuity only. Reduced-motion
  mode removes nonessential transitions without removing progress or state.
- Golden baselines are reviewed artifacts, not the only assertion: widget tests
  also verify semantics, actions, overflow, and state.

## Accessibility and motion

- Every actionable element has a semantic label and a minimum 48 by 48 logical
  pixel target.
- Reading and focus order follows discovery mode → period/anchor → search →
  content → pagination, then image/pager → facts → tags → quality → save in
  detail.
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
