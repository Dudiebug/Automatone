# M2 entry gate - current candidate is not accepted

Status: OPEN. No sensor waiver, warning baseline, or architecture change approved.

Continuation: the user has now approved the narrow warning-disposition policy in
`PROPOSED-M2-entry-warning-policy.md` and instructed that discovered bugs be fixed.
Prerequisite repair and independent per-finding review are in progress. No
individual warning is automatically exempted and baseline acceptance is still pending.

## Request and candidate

The user approved and requested implementation of
`docs/M2_WORKER_IMPLEMENTATION_PLAN.md` on 2026-09-04. Its first gate requires an
accepted clean baseline before M2.1. Implementation order remains M2.1 through
M2.4, with acceptance between tasks.

Candidate: `e81dbad7ab53647920c61d824fb927f15ef67a89` on
`plan/neoforge-1.21.1-server-worker`. Unlike the historical dirty-tree records,
this is a committed candidate. It has not been independently accepted.

A fresh Luna/Max verifier ran from the clean detached checkout at
`C:/Users/devadmin/.codex/worktrees/m2-baseline-e81dbad7/automatone`.
The parent changed only M2 planning/task metadata in the original checkout.

## Current measurements

Evidence directory: `.agents/evidence/M2-entry-20260904/independent/`.

- `sensorCheck.raw.log`: compilation, unit tests, architecture, Error Prone,
  Checkstyle, and duplication emitted PASS. Required `spotbugsMain` failed.
- The verifier counted 84 main SpotBugs findings, matching the historical
  post-cleanup type multiset; test and sensorTest reports have zero findings.
  Matching type totals alone is not a per-instance approved debt baseline.
- The clean-checkout CPD XML contains zero duplication regions.
- `sensorIntegration.raw.log`: all 13 required dedicated-server GameTests passed.
- These checks exercise the existing library. No M2 worker implementation or
  M2 acceptance evidence exists.
- Historical cleanup Q2/Q4 extended coverage gaps are not silently promoted by
  this sensor rerun; see the verifier report for remaining uncertainty.

The verifier report in the evidence directory records the measurements and
remaining uncertainty. Per-finding disposition is not complete and no finding
is approved as baseline debt. Raw logs are retained where a required sensor failed.

## Why implementation stops

`EXECUTION_STRATEGY.md` requires accepted dependencies for READY, a clean accepted
baseline for implementation, and explicit authority for a required sensor waiver
or debt baseline. `.agents/verification/SENSOR_POLICY.yaml` makes SpotBugs a
required default sensor; a required FAIL prevents acceptance.

Some retained findings concern exposed live runtime/host objects, while others
concern concurrency, null handling, and legacy code. Do not turn the entire
inventory into a waiver, alter public/live-owner semantics to appease an analyzer,
or label an unverified warning harmless.

## Required resolution

1. Triage the fresh finding reports to select bounded prerequisite repairs for
   demonstrated defects, preserving existing tests and public contracts.
2. For intentional contract findings that cannot be eliminated without breaking
   the approved design, obtain explicit approval of their individual disposition
   or a narrowly scoped warning baseline. No such approval is implied by the M2
   implementation request.
3. Resolve remaining required acceptance evidence, rerun the required sensors
   from a clean candidate, and let independent verification determine acceptance.
4. Record the accepted baseline before making M2.1 READY. Then use the saved M2
   plan and task specs without repeating planning.

No production code, tests, build configuration, sensor policy, or historical
acceptance verdict was changed to bypass this gate. No candidate was discarded.
