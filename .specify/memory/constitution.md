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

Latte MUST remain one Flutter package until a concrete second production
adapter or independently released component proves a split is necessary. New
dependencies, databases, queues, background services, generic plugin systems,
and code generation require a written need in the active plan. Standard Dart or
Flutter facilities and small explicit types take precedence over frameworks and
speculative abstractions.

## Product and Safety Constraints

- The first supported product target is Android and the first remote site is
  yande.re; other targets remain unclaimed until separately accepted.
- The first milestone is anonymous browse, tag-expression search, post detail,
  media selection, and local save. Authentication, favorites, pools, uploads,
  persistent history, and background work require later specifications.
- Remote JSON and media responses MUST be treated as untrusted input. Allowed
  origins, schemes, sizes, and destination ownership MUST be validated at the
  adapter or storage boundary.
- Credentials, cookies, private media, and raw response dumps MUST NOT enter
  source control, fixtures, logs, ordinary preferences, or UI state.
- Content policy MUST be explicit in the active spec and enforced before media
  is displayed or saved.
- Owner-facing Android UI MUST use Material 3 components and semantic theme
  roles as the product design baseline. Custom components require an unmet
  product need, accessibility evidence, and widget or screenshot acceptance;
  Material styling MUST NOT leak into domain or site-adapter contracts.
- Layouts MUST respond to available window width, text scaling, system light or
  dark mode, and reduced-motion preferences without losing the core journey.

## Spec-Driven Delivery

Each initiative follows assessment when feasibility is uncertain, then one
Spec Kit feature directory through `specify -> clarify -> plan -> checklist ->
tasks -> analyze -> implement -> converge`. The active `specs/<feature>/`
directory is the execution source of truth. Earlier `docs/PROJECT-SPEC.md` and
`tasks/` artifacts are historical inputs and MUST NOT be maintained as parallel
plans after the first Spec Kit feature is accepted.

Before implementation:

1. the specification quality checklist MUST pass;
2. owner-owned custom requirements checklists MUST be reviewed;
3. the plan MUST pass the constitution gate before and after design;
4. every functional requirement and buildable success criterion MUST map to at
   least one task;
5. `$speckit-analyze` MUST report no critical issue; and
6. feasibility gates named in the plan MUST pass before dependent story work.

After implementation, `$speckit-converge` and the repository gate MUST pass.
Artifact churn, defects caught before coding, escaped requirement changes, and
time spent maintaining the workflow MUST be recorded for the first milestone so
the owner can decide whether to retain, simplify, or remove Spec Kit.

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

**Version**: 1.1.0 | **Ratified**: 2026-09-20 | **Last Amended**: 2026-09-20
