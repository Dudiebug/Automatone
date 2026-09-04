# QUALITY-CLEANUP — workflow bugs and duplicate code

## Current authority and scope

User request: "go through the workflow and fix all the bugs and duplicate code".

This is a dedicated cleanup of the current M1 candidate, not another product milestone. The user authorizes repairing the failing quality profile and regressions discovered during those repairs. Work from the existing dirty tree without discarding inherited changes. There is no accepted clean baseline; this cleanup does not approve existing debt or waive a sensor. Do not claim all possible bugs have been eliminated.

## Required result

- Reproduce the current required workflow before implementation and preserve the original source/report state.
- Repair actual defects flagged by SpotBugs and eliminate the seven reported CPD regions through shared responsibility, not detector evasion.
- Preserve existing observable behavior, public runtime/host contracts, native mining, cancellation/publication, cache shutdown, server-only architecture, and every valid existing test assertion.
- Rerun the default, architecture-sensitive, and runtime_minecraft profiles after the final source edit.
- Give an independent fresh-context Luna verifier the frozen candidate and executable spec, then report its actual result. No self-acceptance.

## Allowed changes

Minimal Java fixes in current source sets and focused regression tests; bounded test/evidence scripts using existing tools. Extract shared code only where duplication is demonstrated. Preserve signatures unless a concrete bug cannot otherwise be fixed; report material compatibility changes before implementation. No new dependency is planned.

## Forbidden changes

No M2 or later features, consumer workaround, alternate scanner/pathfinder/mining algorithm, global mixins, dependency upgrades, commits, staging, resets, user-world edits, sensor suppression/threshold relaxation, baseline refresh, or valid assertion weakening. Intentional live-owner references must not be replaced with detached copies just to silence an analyzer. An incompatible analyzer requirement must be reported explicitly, not gamed.

## Procedure

Use AGENTS.md, EXECUTION_STRATEGY.md, the M1 approved plan, installed Graphify/Ponytail/Old Coder skills, and the Luna Old Coder role. Old Coder execution is delegated to fixed Luna/Max agents. Parent owns coordination, Graphify, task metadata and evidence review. Implementers own disjoint files and may not redelegate. One Gradle owner at a time. Separate fresh verifier after source freeze; default two independent rounds.

Before production edits the designated implementer must persist SPEC.md, FAILURE_MODEL.md, baseline snapshots/reports and a runnable ENTRYPOINT.ps1 under `.agents/evidence/quality-cleanup-20260902/`. Spec approval must be recorded as not obtained (autonomous run); current user instructed autonomous implementation. Each behavioral fix requires an observed RED or a valid non-vacuous mutation control, followed by GREEN. CPD refactors preserve assertions and are measured before/after. Every skipped extended layer must be explicit.

## Initial evidence anchors (not final results)

- `.agents/evidence/M1.5/runtime-lifecycle-20260902/independent/round-2/report.json`
- `build/reports/spotbugs/{main,test,sensorTest}.xml`
- `build/reports/verification/cpd.xml`
- `graphify-out/graph.json`

Last measured profile: 126 main and 3 test SpotBugs findings, zero sensorTest findings, seven CPD regions; 39 unit tests, four architecture tests and four native server GameTests pass. Reproduce rather than inheriting these totals as fresh evidence.

## Acceptance boundary

All required sensors must pass for workflow acceptance. Fresh-context candidate-commit clean-checkout acceptance remains unavailable without an authorized candidate commit; do not silently replace that requirement with a dirty-tree rerun. A precise clean source snapshot may provide additional reproducibility evidence, but is not retroactive approval of a baseline or commit. Preserve unresolved findings and explain the exact remaining authority or measurement gap.
