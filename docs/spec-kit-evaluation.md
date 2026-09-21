# Spec Kit evaluation protocol

Latte's first milestone is also a bounded evaluation of GitHub Spec Kit. The
workflow is retained only if it prevents meaningful mistakes at acceptable cost.

## Baseline

Before Spec Kit, Latte had one research note, a combined project/architecture
spec, and a phase/task plan. They established useful boundaries but mixed
product requirements, technical decisions, open questions, and execution tasks.
They did not provide a formal feasibility verdict or cross-artifact analysis.

## Experiment

Run one complete feature through:

```text
assessment -> constitution -> specify -> clarify -> plan
-> requirements checklist -> tasks -> analyze -> implement -> converge
```

Record at each stage:

- elapsed working time;
- lines and files added or changed;
- new product or technical decisions;
- contradictions, omissions, or untestable requirements found before coding;
- questions that required owner input;
- downstream artifact rewrites caused by an earlier-stage miss.

Before the first clean `$speckit-analyze`, intentionally insert one safe,
document-only inconsistency between a requirement and task, confirm analysis
reports it, then remove it. Record whether the finding is precise enough to act
on. Never seed source code or leave the inconsistency committed.

## Decision rubric

| Outcome | Keep | Simplify | Remove |
| --- | --- | --- | --- |
| Pre-code defect detection | At least two material defects found before implementation | One material defect | None |
| Traceability | All functional requirements map to tasks with little manual repair | Useful but recurring manual repair | Mapping is noisy or misleading |
| Change propagation | A changed decision is reconciled across artifacts in one pass | Some duplicated editing | Artifacts frequently disagree |
| Agent execution | Tasks can be implemented without rediscovering scope | Occasional context recovery | Specs do not reduce rediscovery |
| Maintenance cost | Under 15% of milestone effort | 15-25% | Over 25% |

The final decision is not a vote count. Any constitutional or safety defect
caught early can justify keeping a smaller workflow, while high maintenance
cost can justify removing optional stages.

## Expected likely outcome

Assessment, constitution, specification, plan, tasks, and analysis are likely
valuable for Latte because the prior attempts suffered scope expansion.
Custom checklists and duplicate historical plans are the first candidates for
removal if they do not find additional defects.

## Planning-stage result (2026-09-20)

This is an interim result; implementation and convergence costs are not yet
measurable. The workflow has already found material defects before code:

1. HTTP failure cannot truthfully distinguish device-offline from every other
   transport failure, so the product state became `transportUnavailable`.
2. Android scoped storage returns a content URI, album, and display name rather
   than a stable filesystem path.
3. Save cancellation was promised without a cancel interaction and was removed
   from this milestone.
4. Explicit-content tag expressions needed a defined policy-conflict outcome.
5. Deterministic fake-boundary evidence had to be separated from actual
   process-death and MediaStore device evidence.
6. The 20-run performance sample needed a fixture-backed execution context.
7. MediaStore required its own blocking feasibility spike before Story 3.
8. Material Design was initially implicit and is now a product-level Material 3
   contract with theme, layout, accessibility, and visual acceptance criteria.

The seeded inconsistency check also worked: a temporary task required persistent
search history while FR-018 and the constitution explicitly deferred it. The
analysis located the contradictory task and both governing statements; the seed
was then removed. This was precise and actionable, although the check currently
depends on disciplined agent analysis rather than a standalone validator.

### Preliminary verdict

- **Keep** assessment, constitution, specification, plan, task traceability, and
  final analysis for milestone one.
- **Evaluate later** whether the separate custom checklist catches anything the
  spec review did not and whether maintaining historical plan files creates
  avoidable churn.
- **Do not claim success yet**: implementation rediscovery, requirement churn,
  and the maintenance-cost percentage can only be measured after convergence.

## 0.0.1 convergence status (2026-09-20)

The 0.0.1 implementation slice is complete locally, but owner acceptance and
live/device evidence remain open. The current implementation preserves the
reference Yande workflow and uses Material 3 for styling and platform
components; Material 3 is not treated as the product-design authority.

### Delivered scope

- Popular/Newest exploration with period selection, search, settings, column
  density, and thumbnail-only staggered browsing.
- Image-first detail paging with Android back, previous/next fallback controls,
  thumbnail-to-higher-quality replacement, two-finger zoom, metadata table,
  clickable tag queries, and a compact reference-derived action sheet.
- Best-quality background downloads through WorkManager with unique-task
  handling, restart/range resume, concurrent notifications, completion
  thumbnail/open action, native start/running feedback, and retry feedback for
  an already-saved image.
- Bounded prefetch of the next three loaded detail images using display-quality
  variants only; original media is never prefetched speculatively.
- Provider-neutral site registry and capability seams, with authenticated
  personal scoring explicitly retained for the roadmap.

### Evidence and remaining gaps

- `make check`: passed locally with 70 tests and the opt-in Yande probe
  available separately.
- Debug APK: built, installed, and launched on device `0cadf428` without a
  fatal launch exception.
- Live browse/download acceptance is incomplete because the connected device
  could not resolve `yande.re`; this is not evidence that the feature works
  end to end.
- The old PR check failed only the four Explore golden comparisons and ran
  before the latest implementation commits. A fresh CI run after this update
  is required.
- Android instrumentation, the 20-run fixture-backed journey, the owner
  launch acceptance, and the final owner visual/gesture acceptance remain open
  in T023-T032, T034, T044, and T048.

### Traceability corrections for the next review

- Ratings remain adapter/search metadata but are not rendered in the UI;
  authenticated scoring is roadmap-only.
- Explore cards show thumbnails only. Detail tags are selectable and launch an
  opaque tag search intent.
- The accepted launcher asset is the owner-provided PNG in the Android
  resources. The previously proposed SVG/vector conversion is not a product
  requirement after the owner rejected that redraw.
- The system notification owns background download progress and completion;
  the detail page does not remain blocked by a download button.

### Spec Kit status

For the next agent review, retain the assessment, constitution, active feature
specification, plan, task graph, contracts, and this convergence record. Keep
the generated `.specify/` workflow files until the review decides whether they
are worth maintaining. `docs/PROJECT-SPEC.md` remains historical and
superseded; it is not a second execution specification.

Spec Kit found eight material pre-code defects and made the seeded
inconsistency actionable. It has therefore earned a provisional **keep, with
smaller maintenance scope** decision for 0.0.1. Do not call the experiment a
full success yet: maintenance percentage, end-to-end acceptance, and whether
the custom checklist adds value still need the next review pass.

## Final 0.0.1 Milestone Verdict (2026-09-20)

Following the merge of PR #1 and the release of tag `v0.0.1`, the Spec Kit
evaluation for milestone 0.0.1 is complete.

### Decision Rubric Evaluation

| Outcome | Result | Verdict | Evidence |
| --- | --- | --- | --- |
| Pre-code defect detection | 8 boundary defects found early (transport offline distinction, MediaStore URI vs path, save cancellation removal, etc.) | **Keep** | Found material defects in system/API boundaries before code. |
| Traceability | FR-to-task mapping was thorough initially, but became brittle during live hardware testing. | **Simplify** | Manual repair was required once mobile usability feedback arrived. |
| Change propagation | High friction across 25+ files (`spec.md`, `constitution.md`, `contracts/`, `checklists/`, `tasks.md`). | **Simplify** | Changing a single UI decision forced edits across multiple spec layers. |
| Agent execution | Produced false certainty on UI/UX; paper invariants had to be abandoned on physical hardware. | **Remove (for UI)** | Tactile mobile ergonomics (transitions, gestures, safe mode) were undiscoverable on paper. |
| Maintenance cost | Over 25% of milestone effort was consumed by artifact maintenance and cross-checking. | **Remove / Simplify** | Exceeded the 15% budget; starved live hardware execution. |

### Final Decision: SIMPLIFY / DOWNSIZE

Spec Kit is **downsized to a lightweight, mobile-first workflow** for future milestones:

1. **What is retained**:
   - The project constitution (`.specify/memory/constitution.md`) as the high-level boundary and invariant guard.
   - A single feature `spec.md` with prioritized user stories, acceptance criteria, and clear architecture seams.
   - A flat `tasks.md` for execution tracking and red-green slices.
   - Deterministic contract tests and automated gates (`make check`).

2. **What is removed / prohibited**:
   - Multi-file artifact sprawl (separate contract fragments, custom requirements checklists, duplicate historical plans, multi-document analysis graphs).
   - Upfront negative UI invariants (e.g. forbidding Safe Mode or metadata tables before real device testing).

3. **Adopted Rule: Device-First Ergonomics**:
   - Tactile ergonomics, transitions, gesture expectations, and ambient safety cannot be finalized in markdown.
   - Every milestone must deliver a runnable APK to physical hardware early. Real-device receipts are required before closing UI journeys.

## 0.0.2 Account Manager Review Status (2026-09-21)

This review checks whether the downsized Spec Kit workflow was actually used for
the account-manager change and whether its status matches the repository.

### Evidence

- The downsized shape was used: one feature specification and one flat task
  record under `specs/002-account-manager/`, with the constitution retained as
  the project-level boundary.
- The task record drove source implementations for the plugin, secure-storage,
  Yande login, account UI, scoring, and Favorites slices. The corresponding
  unit tests were present, and `mise exec -- make check` passed locally before
  this review.
- The review found an unencrypted Keystore-failure fallback, public-user lookup
  used as credential evidence, direct plugin use in detail, Yande-specific
  query syntax in `ExploreViewModel`, ordinary cookie preferences, Safe Mode
  defaulting off in code, and rating/favorite failures surfaced through
  toasts. Those code issues are now repaired; the remaining test gaps are
  tracked explicitly in `tasks.md`.
- Pool browsing was discovered and delivered after the original task breakdown.
  It is now recorded as post-hoc work in `tasks.md` rather than being presented
  as an original requirement.
- The workflow is not complete for this milestone. A live check of the bundled
  prerequisite script resolved the default ignored pointer to
  `specs/001-yandere-core-journey`; an explicit 002 resolution correctly found
  `specs/002-account-manager/spec.md` and `tasks.md`, then stopped because
  `plan.md` is absent. Therefore no valid 002 `speckit-analyze` or
  `speckit-converge` receipt exists. This is consistent with the constitution's
  downsized single-spec/flat-task decision, but it means the full Spec Kit
  lifecycle was not run for 002 and the ignored feature pointer still needs
  local operator setup.
- T014 still lacks live login and process-restart persistence evidence, Pixiv
  is not implemented, secure-storage and pool-behavior tests remain open, and
  no 0.0.2 maintenance-cost measurement exists yet. The release gate and
  version bump are now complete in `v0.0.2`; that tag does not close the
  remaining acceptance gaps.

### Verdict

Spec Kit did useful work, but only partially. The downsized artifacts exposed
material scope and boundary defects and kept the post-hoc pool decision
traceable. The repository evidence also proves that the full 002 analyzer and
convergence lifecycle did not execute because its required plan artifact is
absent. Keep the downsized artifacts and mark 0.0.2 **RELEASED / PARTIALLY
VERIFIED**: `v0.0.2` is a real tagged release, but it is not hardware-complete
or fully spec-verified until T014, the remaining tests, and the final
acceptance tasks pass.

## 0.0.3 Pixiv implementation audit (2026-09-21)

The current `.specify/feature.json` points to
`specs/004-pixiv-illustration-experience`, and the prerequisite script resolves
that feature with no missing documents. The downsized workflow remains the
active shape: one feature spec, one implementation plan, one flat task record,
deterministic contract tests, and device receipts.

The audit reconciled the implementation with the artifacts after the device
pass. Author works are a bounded `/v1/user/illusts` feed reached from the
detail author row, not a full artist profile. Safe Mode is a global preference:
Yande uses its source query; Pixiv filters normalized `x_restrict == 0` results
and continues through opaque cursors after a fully filtered page. Cache
candidate retention and tri-state auth resolution are recorded as correctness
constraints rather than UI anecdotes.

`make check` passes with no unit-test failures and no Markdown issues. `make validate`
and the unlocked OnePlus 8 receipt cover the browser OAuth exchange, real
Popular/Following/Favorites images, author-work navigation, image-cache
re-entry, Pixiv Safe Mode reload, bookmark toggle round-trip, WorkManager save
completion, duplicate-save prevention, and Following detail-back position
restoration. Grid and detail image failures now expose a retryable state.
Pixiv display candidates prefer official medium/original URLs with the required
Referer, then try the path-preserving `i.pixiv.re` proxy and the `pixiv.cat`
ID/page fallback. The production Pixiv request seam also has a cancellation
regression test proving that a late response from a canceled job cannot update
feed state.
This 0.0.3 slice is **VERIFIED** for the scoped implementation; Konachan
remains postponed. The active branch is prepared for a 0.0.3 review; no remote
PR or push is implied by this record.

## 0.0.3 stale-document audit (2026-09-21)

The current-facing roadmap, feature spec, implementation plan, task evidence,
and research conclusion now agree on the Pixiv image candidate order:

```text
official i.pximg.net URL + Referer -> i.pixiv.re -> pixiv.cat ID/page
```

The dated code-review/parity note and the superseded Pixiv portions of
`specs/003-konachan-pixiv/` remain unchanged as historical records. Their
earlier proxy-first language is not an active implementation contract; the
active contract is `specs/004-pixiv-illustration-experience/`.
