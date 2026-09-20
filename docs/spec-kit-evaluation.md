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
