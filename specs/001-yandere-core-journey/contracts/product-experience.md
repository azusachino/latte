# Contract: product experience

## Information architecture

```text
Explore
├── Discovery feed
├── Tag search results
└── transient loading/empty/failure states
    └── Post detail
        └── Save sheet/progress/result
```

There is no account, library-history, settings, pools, or site-switcher screen in
this milestone.

Safe Mode is an opt-in content filter, not a separate destination. It is off by
default, so explicit-rated posts remain visible to the owner unless the Material
3 Safe Mode filter is selected.

## Explore screen

- Material 3 medium top app bar: Latte title and one labelled search action.
- Search uses Material 3 `SearchAnchor`/`SearchBar` behavior; submitting replaces
  content intent while keeping discovery state available for clear/back.
- Body: edge-to-edge image grid selected from available width rather than device
  class. Compact windows start at two columns; cards preserve image aspect ratio
  and use theme surface roles with no metadata overlay competing with artwork.
- Next-page progress appears after the last content row and never replaces
  already visible posts.
- Initial empty/failure states occupy the content region with one concise reason
  and one valid next action.
- The Explore and Search surfaces expose a labelled Material 3 Safe Mode filter
  with a clear selected/unselected state. Selecting it restarts the active query
  from the first page and removes explicit-rated results.

## Detail screen

- The selected inspection image dominates the first viewport.
- A compact facts row contains rating, dimensions, score, and source when known.
- Tags wrap below the facts and remain selectable as text; tag-to-search is
  deferred unless separately specified.
- Media quality and save action remain reachable without obscuring the image.
- Safe Mode never opens or saves an explicit-rated post; default mode does not
  hide it.
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
- Reading and focus order follows search → content → pagination, then image →
  facts → tags → quality → save in detail.
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
