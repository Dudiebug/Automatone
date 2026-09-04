# Automatone Execution Strategy

## Authority and scope

The approved product/architecture plan defines what to build. This strategy and
AGENTS.md define how to check it. The human-approved proportional verification
policy of 2026-09-04 supersedes older task text requiring a full profile, a clean
checkout, or multiple independent rounds for every task. It changes verification
cadence, not product criteria, architecture, analyzer thresholds, or permission
to waive failures.

The parent selects scope, implements and repairs code, and confirms task
completion. Use `luna_old_coder` (GPT-5.6 Luna / Max) only for Old Coder test work.
Keep assignments small, reuse related test work, and avoid redundant reviewers
and handoffs. A milestone gets one fresh independent test verifier. A verifier
does not repair the code or tests it grades. Graphify is an advisory structural
sensor: use scoped queries when useful and update after meaningful changes.

## Task completion and milestone acceptance

Task states:

`PLANNED -> READY -> IMPLEMENTING -> COMPLETE`

A failing focused check returns the task to repair within IMPLEMENTING. Use
BLOCKED when a required assumption, dependency or measurement prevents progress;
report scope/architecture violations rather than working around them.

A task is COMPLETE when its focused acceptance checks pass, required behavior has
observable evidence, and the controller confirms scope and architectural
invariants. A dependent task in the same milestone may then start from that
candidate. Record milestone-wide checks still owed as PENDING. PENDING is an
intentional deferral, not a measurement and never PASS. Existing ACCEPTED task
records remain historical evidence; they do not impose the old cadence.

Milestone states are IN_PROGRESS and ACCEPTED (or BLOCKED). After the final task,
freeze a clean candidate commit and perform one fresh-context independent
verification of the complete applicable profile and milestone criteria. Let
that run supply the full-profile evidence; do not first run an identical profile
in the development tree. Acceptance requires all required checks/criteria to
pass or an explicit human-approved exception, with no unresolved scope or
architecture violation. A passing script run alone never changes task state or
accepts a milestone.

QUALITY-CLEANUP is the prerequisite acceptance gate before M2. Reuse its existing
focused regression, raw analyzer, runtime and gate-control evidence. Finish its
remaining obligations under this policy; do not restart completed verification
without a change or concrete concern. M2 product work stays behind that gate.

## During a task

1. Read the task and directly relevant contracts. Identify the changed behavior,
   affected callers and any concrete cross-cutting risk.
2. Select the smallest checks that demonstrate that behavior. Compile affected
   code when needed. A bug fix should retain or add a focused regression showing
   the defect and passing correction where practical; record a limitation if a
   practical RED is unavailable.
3. Implement the scoped change and run those checks. Retain useful regressions.
4. Repeat a passing check only if a later change could affect its result or new
   evidence raises a specific concern. Record why a repeated/broader run is needed.
5. Record changes, actual checks/results, reused evidence and PENDING milestone
   obligations in one concise record, then confirm task completion.

Do not routinely run the full unit suite, server GameTests, architecture suite,
SpotBugs, CPD, or every available layer after small edits. Earlier broad checks
are warranted for build/dependency changes, shared infrastructure, cross-cutting
refactors, concurrency, data loss, or server/client boundaries. Select only the
checks relevant to the particular risk; record the reason.

Mutation testing, coverage targets, property-based testing and multiple
independent review rounds are optional. Use them only for a specific risk that
simpler checks cannot establish. There is no mandatory gauntlet or report bundle.
Remove/consolidate tests only when their redundancy or implementation-mirroring
is demonstrated. Never delete a valid regression because it fails or takes time;
default to changing when tests run. Correct a test that contradicts the approved
contract only with the contradiction recorded.

## At the milestone gate

- Select the union of applicable profiles for the milestone, including risks
  introduced by earlier tasks. Do not select unrelated profiles or sensorAll by
  default. Profile definitions are in config/verification/profiles.json and
  .agents/verification/SENSOR_POLICY.yaml.
- Use one fresh verifier and a clean candidate. Run the complete applicable
  profile once, deduplicating overlapping tasks. Check milestone criteria and
  diff scope in that same verification.
- Repair a failure, then rerun the affected checks. Repeat broader checks only
  where the repair could invalidate their results. Preserve passing evidence
  with its candidate and scope; record why it still applies across a repair.
- An unavailable required measurement is UNVERIFIED and blocks acceptance. A
  deliberately deferred milestone check is PENDING and also prevents milestone
  acceptance. Neither becomes PASS through bookkeeping or a failed process.
- Keep baseline debt distinct from new findings. Exact exception rules and
  explicit human approval requirements remain in force. No baseline refresh,
  suppression, architecture relaxation or threshold change to hide a failure.

## Verification entry point

`scripts/workflow/Invoke-AutomatoneVerification.ps1` runs checks and writes one
JSON record with linked raw output. It does not implement products, dispatch
agents, commit, update STATE.yaml, or decide acceptance.

Task mode is the default and requires explicit `-GradleTasks`. `-TestFilter`
forwards JUnit selectors; broad checks require `-RiskReason`. `-Profile` identifies
milestone obligations to record as PENDING, not commands to run during a task.
For example:

```powershell
./scripts/workflow/Invoke-AutomatoneVerification.ps1 -TaskId M2.1 -Scope Task -GradleTasks test -TestFilter 'example.FocusedRegressionTest' -Profile default,runtime_minecraft
```

Replace the example selector with an existing relevant test. The controller
checks the task's acceptance criteria against this measured evidence before
marking COMPLETE; a passing selected-check verdict is not automatic completion.

Milestone mode requires `-FreshContext` (the independent verifier's attestation)
and a clean Git candidate, rejects focused overrides, and deduplicates the
explicitly selected applicable profiles. For QUALITY-CLEANUP:

```powershell
./scripts/workflow/Invoke-AutomatoneVerification.ps1 -TaskId QUALITY-CLEANUP -Scope Milestone -Profile default,architecture_sensitive,runtime_minecraft -FreshContext
```

FreshContext does not create an independent context; the controller assigns one.
Supplement profile measurements with task/milestone criteria in the same record.
A clean checkout alone does not imply independent verification or an accepted
baseline. Reports retain candidate identity and never invent an accepted baseline.

## Failures, evidence and integration

For a local defect, repair the smallest root cause and rerun affected checks.
For a harness defect, repair the harness and keep missing measurements UNVERIFIED.
For a scope/architecture violation or disproven assumption, stop that approach
and record the evidence and smallest needed decision. Repeated failure should
prompt reassessment; it does not require an automatic implementation/reviewer
restart or discard of unrelated passing work.

Use one concise task/milestone record: changed behavior, commands and results,
source/candidate identity, reused evidence with its applicability, failures and
PENDING/UNVERIFIED obligations. Link raw logs instead of repeating them in separate
specification, handoff, verdict and integration reports. The JSON runner record
may be that record; a separate duplicate prose report is not required.

A milestone may be integrated after acceptance evidence and scope are confirmed.
Human approval is required for product-plan/architecture changes, waiving required
checks, accepting baseline debt or weakening thresholds. Routine scoped repairs
and this explicitly approved cadence update do not require additional approval.
