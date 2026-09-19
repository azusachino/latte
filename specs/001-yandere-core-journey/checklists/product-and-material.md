# Product and Material 3 review checklist

**Purpose**: Owner review of product scope, content policy, Material 3 design,
and evidence boundaries before implementation

**Created**: 2026-09-20

**Feature**: [spec.md](../spec.md)

## Product fit

- [ ] CHK001 The browse → inspect → save journey is the right first useful
  product, with search as the only additional milestone capability. [Scope]
- [ ] CHK002 Android API 29+ and yande.re-only are acceptable first support
  claims. [Assumption]
- [ ] CHK003 Allowing safe and questionable content while rejecting explicit
  content matches the intended owner experience. [Spec §FR-004]
- [ ] CHK004 Rejecting explicit-content query conflicts is preferable to
  silently rewriting the owner's expression. [Spec §FR-009]
- [ ] CHK005 Authentication, favorites, pools, uploads, history, background
  queues, second sites, and non-Android targets are correctly deferred.
  [Spec §FR-018]

## Material 3 experience

- [ ] CHK006 Material 3 is the visual and interaction baseline rather than only
  a Flutter implementation default. [Spec §FR-021]
- [ ] CHK007 The quiet, image-led theme direction gives artwork priority and
  avoids decorative gradients, heavy shadows, and arbitrary rounding.
  [Product contract §Material 3 design language]
- [ ] CHK008 Explore, SearchBar, image grid, Detail, variant bottom sheet, and
  save progress form a coherent component hierarchy. [Product contract]
- [ ] CHK009 Compact and expanded layouts preserve the same information
  architecture without adding speculative navigation. [Spec §FR-022]
- [ ] CHK010 Light/dark appearance, 200% text, reduced motion, semantics, focus
  states, and 48-by-48 targets are explicit acceptance dimensions.
  [Spec §FR-022–FR-023]
- [ ] CHK011 Golden baselines complement behavioral widget tests and require
  owner review rather than replacing semantic assertions. [Spec §SC-009]

## Evidence and TDD

- [ ] CHK012 HTTP transport, Android build/run, and MediaStore publication are
  correctly separated into blocking feasibility gates. [Plan §Constitution Check]
- [ ] CHK013 Public-seam tests precede implementation in every story and avoid
  mocking Latte-owned collaborators. [Constitution §II]
- [ ] CHK014 Fixture-backed timing and behavior are deterministic while live
  yande.re timing remains a recorded observation. [Spec §SC-002]
- [ ] CHK015 A content URI plus album and display name is an acceptable save
  result; no filesystem path or user cancel action is promised. [Spec §FR-014]
- [ ] CHK016 The Spec Kit evaluation captures both defects prevented and the
  workflow's maintenance cost before deciding whether to retain it. [Spec §SC-008]

## Notes

- Items intentionally remain unchecked until owner review.
- Rejected items require updates to the active spec, plan, contracts, and tasks
  before implementation begins.
