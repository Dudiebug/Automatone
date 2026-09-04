# QUALITY-CLEANUP — workflow bugs and duplicate code

## Proportional verification — approved 2026-09-04

The current human instruction explicitly authorizes simplifying workflow policy
and orchestration before resuming this prerequisite gate. AGENTS.md and
EXECUTION_STRATEGY.md now govern cadence: focused checks during repairs, one
fresh independent clean-candidate gate, no identical preliminary full run or
default second review. Parent implements code; Luna handles only small Old Coder
test assignments. Keep one concise evidence record and reuse valid existing
measurements. No product implementation is authorized by the workflow update.

Existing evidence remains available under `.agents/evidence/quality-cleanup-20260904/`:
25 server GameTests and GameTest style passed; focused defects have regression
evidence; exact-gate fixture controls passed. The raw analyzer has 75 findings
and no analysis errors/missing classes. The latest direct comparison found an
eligibility metadata defect in row 81 (`BC_IMPOSSIBLE_CAST` was recorded with
extra child-node text). The extraction metadata is corrected from the pinned
original XML and its focused regression passes. Independent exact dispositions
remain pending. This is not a warning waiver or cleanup acceptance.

Remaining prerequisite obligations are PENDING: resolve the exact warning
dispositions, then one clean independent gate using default,
architecture_sensitive and runtime_minecraft profiles plus relevant gate controls.
Reuse existing focused evidence; workflow-only edits do not justify repeating
unchanged Java behavior tests. M2 stays behind this prerequisite acceptance.

## Current continuation - 2026-09-04

The user approved `.agents/decisions/PROPOSED-M2-entry-warning-policy.md` and
explicitly instructed fixing bugs found during the workflow. Resume this
prerequisite on committed source `e81dbad7ab53647920c61d824fb927f15ef67a89` before
M2. Latest evidence is `.agents/evidence/M2-entry-20260904/independent/report.json`:
84 main SpotBugs findings; other default sensors, 67 unit tests, and 13 server
GameTests pass. Q2/Q4 historical evidence gaps remain to be resolved by targeted
checks, not renamed or waived.

This continuation supersedes historical statements below forbidding all warning
exceptions or candidate commits. Only independently reviewed, exact-match
exceptions satisfying the approved rule are allowed; actual defects and uncertain
findings remain blocking. Local candidate commits and clean verification worktrees
are part of the authorized implementation/acceptance flow. No push is requested.

The positive/negative-height scanner evidence also exposed a concrete native
`WorldScanner.scanChunk` coordinate mismatch: its cutoff compares section-relative
block Y with absolute player Y, while the radius entry point converts the player
Y to the same section-relative frame. The current authorization to fix discovered
bugs covers the minimal coordinate correction after a runtime RED. Preserve the
historical parity evidence, document the correction against IWorldScanner's
height-threshold contract, and retain unrelated ordering/max/repack assertions.

Implementation owns the minimal prerequisite fixes, focused regression/evidence
checks, and exact reviewed-warning gate wiring. A separate fresh Luna reviewer
owns technical exception classification; the implementer cannot self-exempt.
No dependency upgrades, public-contract breakage, unrelated product changes, or
M2 implementation before prerequisite acceptance. Evidence for this continuation
goes under `.agents/evidence/quality-cleanup-20260904/`.

## Current authority and scope

User request: "go through the workflow and fix all the bugs and duplicate code".

This is a dedicated cleanup of the current M1 candidate, not another product milestone. The user authorizes repairing the failing quality profile and regressions discovered during those repairs. Work from the existing dirty tree without discarding inherited changes. There is no accepted clean baseline; this cleanup does not approve existing debt or waive a sensor. Do not claim all possible bugs have been eliminated.

## Required result

- Reproduce the current required workflow before implementation and preserve the original source/report state.
- Repair actual defects flagged by SpotBugs and eliminate the seven reported CPD regions through shared responsibility, not detector evasion.
- Preserve existing observable behavior, public runtime/host contracts, native mining, cancellation/publication, cache shutdown, server-only architecture, and every valid existing test assertion.
- Run the applicable default, architecture-sensitive and runtime_minecraft profiles once at the clean prerequisite gate, reusing still-valid results after scoped repairs.
- Give an independent fresh-context Luna verifier the frozen candidate and executable spec, then report its actual result. No self-acceptance.

## Allowed changes

Minimal Java fixes in current source sets and focused regression tests; bounded test/evidence scripts using existing tools. Extract shared code only where duplication is demonstrated. Preserve signatures unless a concrete bug cannot otherwise be fixed; report material compatibility changes before implementation. No new dependency is planned.

## Forbidden changes

No M2 or later features, consumer workaround, alternate scanner/pathfinder/mining algorithm, global mixins, dependency upgrades, resets, user-world edits, unapproved sensor suppression/threshold relaxation, blanket baseline refresh, or valid assertion weakening. Local candidate commits/clean worktrees are authorized for the gate. Intentional live-owner references must not be replaced with detached copies just to silence an analyzer.

## Procedure

Use AGENTS.md, EXECUTION_STRATEGY.md and relevant Graphify/Ponytail guidance. Parent owns implementation; fixed Luna/Max handles only test work. Keep one Gradle owner and one concise evidence record. For actual fixes retain a focused regression with practical RED/GREEN evidence; no default mutation requirement or separate report bundle. The single fresh milestone verifier supplies final gate evidence after source freeze.

## Initial evidence anchors (not final results)

- `.agents/evidence/M1.5/runtime-lifecycle-20260902/independent/round-2/report.json`
- `build/reports/spotbugs/{main,test,sensorTest}.xml`
- `build/reports/verification/cpd.xml`
- `graphify-out/graph.json`

Last measured profile: 126 main and 3 test SpotBugs findings, zero sensorTest findings, seven CPD regions; 39 unit tests, four architecture tests and four native server GameTests pass. Reproduce rather than inheriting these totals as fresh evidence.

## Acceptance boundary

All required prerequisite checks must pass or receive explicit human exceptions before M2. Use one authorized clean-candidate independent verification. PENDING or UNVERIFIED obligations and unresolved findings prevent acceptance; focused passing results do not erase them.
