# Feature Specification: yande.re core journey

**Feature Branch**: `docs/dart-rewrite-plan`

**Created**: 2026-09-20

**Status**: 0.0.1 implementation slice complete locally; owner/device acceptance
and live evidence pending

**Input**: Assessment handoff from
`.specify/assessments/latte-yandere-client/decision.md`

## UX compatibility boundary

Latte is a Material 3 rework of the pinned Yande-compatible reference app.
Material 3 may modernize visual tokens, accessibility, and component styling,
but it MUST preserve the reference-derived component composition, workflow, and
dataflow: Popular/Newest discovery, period/date selection, dense staggered image
browsing, image-first detail paging, grouped detail actions, and reversible
back/swipe navigation. A Material component is not permission to invent a
different information architecture.

## User Scenarios & Testing

### User Story 1 - Explore popular art (Priority: P1)

As the owner, I open Latte and see Popular by day. I can switch to week or
month, move through readable anchor periods, scan a dense image-first feed, and
open a post to inspect its image, dimensions, source, aggregate score,
and tags without losing my place.

**Why this priority**: Popular period exploration is the owner's primary daily
workflow. It proves remote access, query identity, normalization, visual
hierarchy, and navigation before search or local side effects are added.

**Independent Test**: Start Latte with deterministic Popular day, week, and month
pages, switch periods and anchors, browse two pages, open a post, move through
detail, return, and observe the same period, anchor, feed position, and post
order.

**Acceptance Scenarios**:

1. **Given** the first Popular day page is available, **When** the owner opens
   Latte, **Then** the Explore surface shows Popular with Day selected, a
   readable anchor period, and thumbnail-only images in a dense
   aspect-preserving feed with stable post identity.
2. **Given** the owner reaches the feed boundary, **When** another page is
   available, **Then** Latte appends it without duplicating existing posts or
   replacing the visible feed.
3. **Given** a card is visible, **When** the owner opens it, **Then** Latte shows
   the best usable inspection image in a horizontally swipable pager with
   previous/next controls, aggregate score, and all available normalized
   metadata while clearly omitting unavailable fields.
4. **Given** the detail view is open, **When** the owner uses Android back or the
   visible back action, **Then** the feed restores the prior Popular period,
   anchor, position, and order without exiting the app.
5. **Given** the remote site is empty, malformed, throttled, unavailable, or the
   request has no usable network path, **When** a page is requested, **Then**
   Latte shows a truthful, actionable state and never presents failure as an
   empty success.
6. **Given** system light/dark appearance, enlarged text, reduced motion, or a
   changed Android window width, **When** the owner uses the flow, **Then** its
   Material 3 hierarchy remains usable without clipped actions or lost state.
7. **Given** the owner switches Popular from day to week or month, **When** the
   next page is requested, **Then** the period and anchor/window are part of the
   query identity and an older response cannot replace the selected period.

---

### User Story 2 - Search by yande.re tags (Priority: P2)

As the owner, I enter the tag expression I already use on yande.re and see a
separate paged result set. Negative terms and yande.re meta-tags retain their
meaning, and clearing the query returns me to discovery.

**Why this priority**: Search turns passive browsing into purposeful discovery,
but the app remains useful as a feed viewer without it.

**Independent Test**: Enter a multi-term expression containing a negative tag,
page through its results, replace it with another query, and clear it without a
stale response overwriting the latest intent.

**Acceptance Scenarios**:

1. **Given** the discovery feed is visible, **When** the owner submits a valid
   tag expression, **Then** Latte shows results for that complete expression and
   starts its pagination from the beginning.
2. **Given** one search is loading, **When** the owner submits another query,
   **Then** an older response cannot replace the newer query's state.
3. **Given** a search has no matches, **When** it completes, **Then** Latte shows
   a search-specific no-results state and keeps the query editable.
4. **Given** search results are visible, **When** the owner clears the query,
   **Then** the discovery feed and its prior position return.

---

### User Story 3 - Keep one image locally (Priority: P3)

As the owner inspecting a post, I save the best available media quality to
Latte's user-visible image library. Latte reports progress and the final
location, and it never silently overwrites an existing file.

**Why this priority**: Saving completes the intended find-inspect-keep loop but
adds a device side effect, so it follows a proven read-only experience.

**Independent Test**: From a deterministic post detail, save one variant, open
or locate the result, repeat the request, and observe an existing-file outcome
without changing the original file.

**Acceptance Scenarios**:

1. **Given** a post exposes multiple media variants, **When** the owner taps
   Download, **Then** Latte chooses and saves the best available variant.
2. **Given** a save is in progress, **When** the owner remains on the detail
   screen, **Then** Latte communicates progress and prevents an accidental
   duplicate request.
3. **Given** the target file already exists, **When** the same post and variant
   are saved again, **Then** Latte reports the existing result and leaves the
   file unchanged.
4. **Given** media resolution, transfer, or device storage fails, **When** the
   save terminates, **Then** Latte keeps incomplete output hidden and reports a
   retryable or terminal failure without losing the current post; a later
   worker run can resume the pending transfer.
5. **Given** a variant was saved successfully, **When** the owner terminates
   and relaunches Latte before repeating the same save, **Then** the Android
   save boundary finds the deterministic site/post/variant identity in
   `Pictures/Latte`, returns `Already saved` with its content URI, album, and
   display name, and leaves the original bytes unchanged.
6. **Given** multiple posts are saved concurrently, **When** their transfers
   are active or complete, **Then** each post keeps its own progress and result
   entry in the native Latte notification group.

### Deferred capability - authenticated personal score

Authenticated personal scoring is intentionally a later distinct slice. A future
specification may add a signed-in 0–3 score control, including reset-to-zero and
auth-required states. The read-only aggregate `score` returned with a post is
already presentation metadata and MUST NOT be treated as the owner's vote.

### Edge Cases

- A response contains a post without any usable preview or inspection media.
- The same post appears on adjacent remote pages.
- Optional dimensions, source, score, tags, or media sizes are absent or null.
- A post is deleted or becomes unavailable between feed and detail/save.
- A remote page succeeds after an older request has already been superseded.
- The remote service returns custom throttling, standard throttling, an HTML
  error page, malformed JSON, or a successful response with no posts.
- A media URL redirects to a host or scheme not accepted for the selected site.
- Device storage is full, access is revoked, or the app closes mid-save.
- System appearance, text scale, reduced-motion preference, or window width
  changes while feed/detail state is active.
- The user enters only whitespace, control characters, or a query too long for
  safe submission.

## Requirements

### Functional Requirements

- **FR-001**: Latte MUST launch into Popular by day, or the most recently
  requested Popular period and anchor in the active session, without requiring
  an account.
- **FR-002**: Latte MUST identify every remote post by both site and remote post
  ID and MUST deduplicate by that identity.
- **FR-003**: Latte MUST present Popular day, week, and month as visible
  discovery choices, with a readable anchor/window control for each selection.
- **FR-004**: Latte MUST present a paged, image-first, aspect-preserving
  masonry-like feed for Popular and Newest and MUST preserve its order, period,
  anchor, and position across detail navigation during the active session.
- **FR-004a**: Latte MUST use a horizontally swipable image-first detail pager,
  retain accessible previous/next controls, and route Android platform back from
  detail to the preserved feed instead of exiting the root activity.
- **FR-005**: Latte MUST preserve and display safe, questionable, explicit, and
  unknown ratings returned by the adapter; it MUST NOT locally filter ratings or
  rewrite opaque expressions based on rating.
- **FR-006**: Latte MUST distinguish initial loading, next-page loading, empty,
  no-results, transport-unavailable, throttled, malformed-response,
  remote-unavailable, and retryable states; it MUST NOT claim the device is
  offline when an HTTP failure cannot establish that fact.
- **FR-007**: Latte MUST stop automatic retry after one bounded retry for an
  explicitly retryable read and MUST offer owner-initiated retry afterward.
- **FR-008**: Latte MUST display only normalized metadata and MUST omit missing
  optional values rather than inventing replacements.
- **FR-009**: Latte MUST keep remote wire fields, base URLs, status codes,
  content rules, and page mechanics out of product presentation concepts.
- **FR-010**: Latte MUST accept the owner's complete yande.re tag expression as
  site-owned syntax and MUST preserve spaces, negative terms, meta-tags, and
  rating terms exactly; it MUST NOT add exclusions or return a local policy
  conflict.
- **FR-011**: Latte MUST reset result pagination when search intent changes and
  MUST prevent a superseded response from replacing current results.
- **FR-012**: Latte MUST restore the prior discovery state when a search is
  cleared during the same session.
- **FR-013**: Latte MUST expose only media variants actually available for a
  post and MUST label them with known quality, dimensions, and size.
- **FR-014**: A save request MUST originate from a normalized post reference and
  selected variant; the presentation layer MUST NOT supply an arbitrary remote
  URL as download authority.
- **FR-015**: Latte MUST save completed images into a user-visible Latte album
  or directory and report the resulting album, display name, and a
  system-openable content reference; no filesystem path is promised.
- **FR-016**: Latte MUST derive a deterministic, collision-resistant save
  identity from site, remote post, selected variant, and known checksum/display
  name; it MUST query published Android MediaStore entries under `Pictures/Latte`
  before transfer and re-check immediately before publication. A matching entry
  MUST return `Already saved` with its content URI, album, and display name
  without overwriting, including after app restart or process death.
- **FR-017**: Latte MUST keep incomplete output hidden after a failed save or
  interrupted publication and MUST resume it from the durable pending item when
  the worker runs again. User-initiated save cancellation is deferred.
- **FR-018**: Latte MUST accept a deterministic substitute site for acceptance
  tests without changing discovery, search, detail, or save presentation.
- **FR-019**: Latte MUST NOT expose authenticated personal scoring, favorites,
  pools, uploads, persistent history, a user-visible download queue, or another
  production site in this milestone. Native background transfer is required for
  saves and may run multiple independent tasks.
- **FR-020**: Latte MUST record a dated, redacted live receipt before claiming
  yande.re or Android support.
- **FR-021**: Feature implementation MUST remain blocked until a Dart transport
  probe fetches and decodes the anonymous post route and a stock Android build
  runs on an emulator or device.
- **FR-022**: Latte MUST use Material 3 components and semantic color,
  typography, shape, elevation, spacing, and motion roles for owner-facing
  Android UI. Material 3 styling MUST preserve the reference-derived
  component composition, workflow, and dataflow rather than replacing them with
  a new information architecture.
- **FR-023**: Latte MUST support system light and dark appearance, text scaling,
  reduced motion, and compact through expanded Android window widths without
  hiding or clipping the core browse, search, detail, and save actions.
- **FR-024**: Every interactive Material surface MUST expose a semantic label,
  visible focus/pressed/disabled state, and a target of at least 48 by 48
  logical pixels; status MUST never be communicated by color or motion alone.

- **FR-025**: Authenticated personal scoring from 0 through 3 MUST remain a
  documented roadmap capability, distinct from aggregate score and local
  download, and MUST NOT appear as a non-functional control in this milestone.

- **FR-026**: Latte MUST keep the required site adapter boundary limited to
  query, post lookup, and media resolution. Optional operations MUST use
  separate capability ports discovered from the selected adapter; adding a
  second platform MUST NOT require Yande-specific branches in shared explore,
  detail, settings, or download code. Unsupported capabilities MUST remain
  absent from the UI.

### Key Entities

- **Site**: A remote platform identity and its verified product capabilities.
- **Post Reference**: The unique pair of site identity and remote post ID.
- **Post Summary**: Feed/search identity, rating, preview, score, dimensions,
  tags, and other optional discovery metadata.
- **Post Detail**: A post summary plus inspection metadata and available media
  variants.
- **Tag Query**: The owner's opaque site expression and its active result state.
- **Popular Query**: A normalized period (`day`, `week`, or `month`), UTC anchor,
  derived window, page, and adapter-owned continuation. Period and anchor/window
  are part of query identity.
- **Continuation**: Adapter-owned information required to request another page.
- **Media Variant**: One available preview, sample, JPEG, or original resource
  with known quality metadata.
- **Save Request**: A post reference plus chosen media variant and owner intent.
- **Save Result**: Completed, existing, retryable failure, or terminal failure,
  with an album, display name, and content reference only when usable.

## Success Criteria

### Measurable Outcomes

- **SC-001**: The owner completes launch → Popular period browse → inspect →
  save on the first
  supported Android device in under two minutes without prior instruction.
- **SC-002**: Discovery and search show their first usable content or a truthful
  terminal state within five seconds in at least 19 of 20 fixture-backed Android
  integration runs; live timing is recorded separately and is not deterministic.
- **SC-003**: Scrolling and opening/closing detail maintain responsive
  interaction with no visible input stall longer than 100 milliseconds during
  the fixture-backed acceptance journey.
- **SC-004**: Every acceptance scenario and listed edge-case class has a
  deterministic boundary, controller, or widget test; actual process death and
  MediaStore behavior are additionally evidenced by the device receipt.
- **SC-005**: The four-stage product flow runs unchanged against both the
  production yande.re adapter and a deterministic substitute adapter.
- **SC-006**: Repeating a successful save 20 times produces one usable local
  file, 19 existing-file outcomes, and no overwritten or partial files.
- **SC-007**: A redacted live receipt proves anonymous browse, tag search, detail
  media retrieval, and one local save on the claimed Android target.
- **SC-008**: Spec Kit's first-milestone evaluation records time spent per
  artifact stage, pre-code defects found, post-code requirement changes, and a
  seeded inconsistency result, enabling an explicit keep/simplify/remove
  decision after convergence.
- **SC-009**: Golden/widget acceptance at compact and expanded widths, light and
  dark appearance, 200% text scale, and reduced motion shows no clipped primary
  action, unreadable text, unexplained color-only state, or layout exception.

## Assumptions

- The repository owner is the only required user for the first milestone.
- Android is the first supported product platform; other Flutter targets are
  future decisions.
- Network access is required for remote discovery and media; transport failure
  preserves the current in-memory view and does not imply an offline catalog.
- Rating fields remain available to adapter/search compatibility, but are not
  rendered by Latte. Latte does not provide a Safe Mode filter or rating-driven
  query rewrite.
- Authenticated personal scoring from 0 through 3 is deferred to a later
  specification and remains distinct from the read-only aggregate score.
- A user-visible Latte album/directory is preferable to asking for a destination
  on every save.
- yande.re remains an external dependency with no availability or compatibility
  guarantee; unsupported behavior is surfaced, not bypassed.
