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
