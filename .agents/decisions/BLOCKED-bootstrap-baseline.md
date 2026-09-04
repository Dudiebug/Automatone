# Bootstrap baseline is not yet accepted

Status: OPEN; no waiver or plan change approved.

Affected tasks: bootstrap acceptance and readiness of M1.1-M6.4. This is not a request to redo completed work automatically.

The installed workflow requires an accepted baseline and independent evidence before product tasks are READY/ACCEPTED. The current branch has substantial pre-existing uncommitted M1.1-M1.4 changes. The HEAD commit is not the tested working tree. Before-product hashes and git status are captured in `.agents/evidence/bootstrap/before.json`; subsequent tooling measurements describe the candidate, not an accepted baseline.

Current source still has client references in `IPlayerContext`, `IPlayerController`, and `CalculationContext`; see `.agents/evidence/bootstrap/MAP.md`. Existing `MineProcessLifecycleTest` does not instantiate a real server or exercise world mining. M1.5 remains unimplemented and reserved for the user. These facts prevent inferring runtime safety from earlier completion statements.

Smallest next decision after bootstrap evidence: explicitly choose a candidate snapshot and disposition of each reported finding. Resolve genuine server-boundary/runtime defects in separately authorized work; do not baseline away a defect that invalidates the target runtime. Commit/snapshot only with appropriate authority. Re-run required sensors and independent fresh verification before recording accepted_baseline.

Alternatives rejected: treating HEAD as the dirty candidate; turning missing GameTests into pass; suppressing client coupling; rewriting historical milestone code during tooling setup. No candidate is discarded and no user files are removed.

Later-task impact: generated specs remain PLANNED, not READY. The user still controls milestone starts. This record does not accept debt, change the approved architecture, or authorize M1.5 work.
