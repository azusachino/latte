# Latte Constitution

## Core Principles

### I. Product Slice First

Every milestone MUST deliver one owner-visible journey that can be accepted on
its own. Work MUST be ordered by product value, not by architectural layers.
Features outside the active specification MUST remain absent rather than appear
as placeholders, dormant flags, or empty adapters. This keeps Latte finishable
and prevents a repeat of Dreamland's broad pre-validation surface.

### II. Test-First at Public Seams (NON-NEGOTIABLE)

Every behavior change MUST begin with a failing test through an approved public
seam, followed by the smallest implementation that passes it. Tests MUST assert
observable behavior rather than internal calls or private mapping structure.
Remote HTTP, filesystem access, time, and device services MAY be replaced at
their system boundaries; Latte-owned collaborators MUST NOT be mocked merely to
make implementation easier. Each red-green slice MUST leave `make check` green.

### III. Site Policy Stays in Adapters

The product flow MUST use site-neutral values for site-qualified post identity,
opaque queries, continuation, media variants, and structured failures. Protocol
wire types, base URLs, content defaults, authentication, throttling, browser
routes, and undocumented behavior MUST remain owned by the concrete site
adapter. Widgets and feature controllers MUST NOT import Moebooru or yande.re
implementation modules. Shared code is extracted only from observed behavior,
never from imagined future sites.

### IV. Evidence Before Capability Claims

A capability or platform MUST NOT be advertised until deterministic contract
tests and the required live/device acceptance receipt both pass. Fixtures MUST
record endpoint family, capture date, redactions, and the behavior they prove.
Live tests MUST be opt-in, bounded, and read-only unless the owner separately
authorizes a reversible mutation. Unsupported or unverified behavior MUST be
absent or reported honestly; it MUST NOT be simulated as success.

### V. Simplicity Is a Gate

Latte is a native Android application built with Kotlin and Jetpack Compose. It
MUST remain a single cohesive Android module (`app`) until a concrete second
production component proves a split is necessary. Standard Kotlin, Coroutines,
Jetpack Compose facilities, and small explicit types take precedence over
external frameworks and speculative abstractions.

### VI. Device-First Ergonomics Over Paper Invariants

For client and mobile applications, tactile ergonomics, gestures, thumb reach,
screen density, transition choreography, and ambient safety (such as Safe Mode)
are empirical discoveries that emerge from physical hardware interaction. They
MUST NOT be locked as negative invariants on paper before running code. An
executable thin slice (a runnable APK installed on a physical test device) MUST
be deployed early in each milestone to validate usability before finalizing UI
specifications.

## Product and Safety Constraints

- The first supported product target is Android (Kotlin + Jetpack Compose) and
  the first remote site is yande.re; other targets remain unclaimed until
  separately accepted.
- The first milestone is anonymous Popular/Newest browse, visible day/week/month
  period selection, tag-expression search, post detail, media selection, and
  local save via WorkManager. Authenticated personal scoring, favorites, pools,
  uploads, and persistent history require later specifications.
- Remote JSON and media responses MUST be treated as untrusted input. Allowed
  origins, schemes, sizes, and destination ownership MUST be validated at the
  adapter or storage boundary.
- Credentials, cookies, private media, and raw response dumps MUST NOT enter
  source control, fixtures, logs, ordinary preferences, or UI state.
- Safe Mode MUST be supported as a user-configurable setting (defaulting to safe
  content) that filters or rewrites queries to protect the owner in shared
  environments.
- Owner-facing Android UI MUST use Material 3 components and semantic theme
  roles as the product design baseline. Custom components require an unmet
  product need, accessibility evidence, and widget or screenshot acceptance;
  Material styling MUST NOT leak into domain or site-adapter contracts.
- Post detail MUST provide a structured metadata table (rating, score, dimensions,
  source) and enlarged, thumb-friendly clickable tag chips.
- Interactive UI MUST support standard touch expectations: smooth tab
  transitions with stable app bar height, pull-to-refresh on paged feeds, and
  quiet inline error states instead of intrusive toasts.
- Layouts MUST respond to available window width, text scaling, system light or
  dark mode, and reduced-motion preferences without losing the core journey.

## Spec-Driven Delivery (Downsized for Mobile)

Spec Kit is streamlined for mobile client development to eliminate
synchronization overhead and avoid document drift:

1. **Lightweight Feature Artifacts**: Each feature uses a single `spec.md`
   (defining user stories, acceptance criteria, and architectural boundaries)
   paired with a flat `tasks.md`. Multi-file document sprawl (redundant custom
   checklists, separate contract fragments, duplicate historical plans) is
   prohibited for client features.
2. **Early Hardware Slice**: Implementation begins with a thin runnable slice
   deployed to physical hardware to verify gestures and tactile ergonomics
   before expanding the task graph.
3. **Empirical UX Validation**: Hardware acceptance receipts on a physical device
   are required before declaring user journeys complete.
4. **Automated Quality Gates**: Each feature must pass unit/contract tests,
   `make check`, and final convergence before release.

## Governance

This constitution supersedes other Latte planning conventions. Amendments
require an explicit owner decision, a semantic version change, a rationale, and
updates to affected active specifications or plans. A major version removes or
redefines a principle, a minor version adds or materially expands governance,
and a patch clarifies wording without changing obligations.

Every review MUST verify constitution compliance. Any exception MUST be written
in the active plan's Complexity Tracking table with the simpler alternative and
why it fails. Repeated or permanent exceptions require a constitution amendment
rather than silent drift.

**Version**: 2.0.0 | **Ratified**: 2026-09-20 | **Last Amended**: 2026-09-20

Version 2.0.0 amends the constitution following the 0.0.1 release: it reflects
the native Android (Kotlin/Compose) stack, adds Principle VI (Device-First
Ergonomics), recognizes Safe Mode and metadata tables as core product
constraints, and downsizes Spec Kit to a lightweight, single-spec mobile
workflow.
