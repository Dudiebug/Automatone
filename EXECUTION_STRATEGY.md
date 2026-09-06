# Automatone Execution Strategy

## Authority and scope

### Human-owned in-game testing

The 2026-09-06 human instruction assigns **all in-game testing to the human**.
The agent supplies a concise, numbered list of actions and expected results with
the matching build. Do not launch or control Minecraft clients, GameTest servers,
or Minecraft restart probes for acceptance unless the human explicitly requests
that specific run. This environment is not the human's in-game test environment.
This supersedes earlier instructions below to run GameTests autonomously.

Continue code review, compilation, unit tests and applicable static/architecture
checks. Hand over the implementation and package when those checks are complete;
do not delay delivery trying to perform the human's tests. Record in-game results
as `PENDING — HUMAN TESTING` until the human reports them. Previously measured
results remain historical evidence; pending tests never become PASS by default.
Implementation completion and delivery do not claim in-game milestone acceptance.

The approved product/architecture plan defines what to build. This strategy and
AGENTS.md define how to check it. The human-approved proportional verification
policy of 2026-09-04 supersedes older task text requiring a full profile, a clean
checkout, or multiple independent rounds for every task. It changes verification
cadence, not product criteria, architecture, analyzer thresholds, or permission
to waive failures.

The 2026-09-06 human instruction to loosen the workflow supersedes older
mandatory test-author routing, per-fix regression/RED requirements, and immediate
graph/evidence-update cadence, including those in older implementation plans and
task specifications. Use the small-fix path below. Milestone acceptance and
architecture requirements remain in force.

The subsequent 2026-09-06 human-approved test-authoring workflow makes Astra
responsible for the testing foundation and specs, with Luna Max as the default
author for substantial feature tests and Astra reviewing the results. This
supersedes older optional-only routing and blanket bans on Astra editing tests.
Small repairs remain direct; independent milestone verification is unchanged.

Astra is the primary implementer and controller. Work directly by default on
production and small repairs; use the test-authoring workflow below for
substantial feature tests. Implementation/repair helpers remain optional.
Follow AGENTS.md's delegation
and escalation rules. Astra selects models, takes over stalled work, reviews
helper changes and evidence, and approves satisfactory work autonomously. Helpers
escalate directly to Astra, never through an automatic agent chain or to the user.
A milestone gets one fresh independent verifier selected by Astra; that verifier
does not repair the code or tests it grades. Graphify is advisory: use scoped
queries when useful; batch updates at milestone boundaries or after structural
changes that materially invalidate the graph. Skip refreshes for small local fixes.

The 2026-09-05 user instruction supersedes mandatory Luna routing and model-change
approval requirements. Routine implementation, testing, delegation, review and
integration decisions require no user participation. Run automated GameTests
locally; ask the user only for actual manual checks, missing access/input, or
unresolved decisions outside the approved product scope. This routing revision
does not waive required checks or change the product architecture.

The subsequent 2026-09-05 user authorization expands the M2 gate repair scope
to dependency and toolchain remediation, including necessary version-pin and
workflow changes. Do not ask again for routine changes within that scope.
The controller may correct exact, evidence-proven dependency identity/version
false positives or findings whose required vulnerable feature is proven
unreachable in the supported execution path, retaining raw findings and independent review of each
disposition. This does not authorize blanket suppressions, lower thresholds,
acceptance of affected dependencies as debt, or advancement into M3. Preserve
the single milestone verifier and rerun checks invalidated by the repairs.

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

### Test foundation, assignments and review

Astra defines the testing foundation from the approved project outline: map
acceptance criteria to observable behavior, select unit tests or server GameTests,
establish fixture cleanup/reset and timing rules, define allowed mocks, and supply
a few reference tests. Reuse the existing tooling. Tests stay within feature tasks;
do not build a separate testing architecture or prewrite the entire project suite.

For each substantial feature-test batch:

1. Astra resolves contract ambiguity and specifies requirement references, inputs,
   expected outcomes, failure cases, allowed mocks, owned files, relevant interfaces,
   focused run commands and escalation conditions in the task or assignment.
2. GPT-5.6 Luna at max reasoning writes and runs the assigned tests. Helpers may
   inspect relevant source, but derive expected behavior from the approved spec.
   They change only assigned tests/fixtures and report contradictions or missing
   interfaces to Astra; they do not change production behavior to satisfy tests.
3. Astra reviews assertions for plausible defect detection, coverage of the real
   integration boundary, isolation, deterministic timing and duplication. Avoid
   copying implementation logic into the expected result. Use focused behavioral
   evidence appropriate to risk; do not impose RED/GREEN or mutation on every test.
4. Astra repairs, returns or integrates the batch after reviewing actual results.
   Record acceptance-criterion coverage, checks run, unresolved issues and deferred
   milestone checks in the existing concise evidence record.

For example, an exact-quantity mining GameTest must observe three actual requested
block destructions with five targets available and no later extra destruction;
a mocked counter reaching three does not prove native mining stops correctly.

Astra may author the foundation/reference tests, make small test repairs directly,
or take over/reassign when delegation stops adding value. Luna assignments remain
bounded within the active task; multiple helpers need disjoint ownership. Routine
assignments do not require Old Coder unless the task's risk or human request calls
for it. Astra's test review never substitutes for fresh independent milestone
verification, and test authors must not grade their own milestone candidate.

### Small, clear fixes

When the cause and correction are evident from a narrow source path or an
existing reproduction, implement directly. Compile affected code when needed;
use an existing focused check if it adds useful confidence. Do not require a new
test, a separate test author, a forced RED/GREEN cycle, or a new independent
review. A new regression is worthwhile for a plausible recurrence or unresolved
behavioral question, not as ceremony. Stop once sufficient evidence is available.

This path does not cover unresolved authorization, persistence/data-loss,
concurrency, or server/client behavior. Use focused behavioral checks for those
risks. A known failing check still requires repair or an explicit human exception.

Reuse the plan/context already read. Record the change and actual validation in
a few lines in the existing evidence record or commit/PR description. No new task
specification, report, repeated state update, or graph refresh is needed merely
because a small repair occurred. Update state when scope, acceptance, or a blocker
actually changes.

### Other changes

1. Read the task and directly relevant contracts. Identify the changed behavior,
   affected callers and any concrete cross-cutting risk.
2. Select the smallest checks that demonstrate that behavior. Compile affected
   code when needed. Add focused regressions when they address a real behavioral
   risk; do not delay an evident fix solely to manufacture a failing test first.
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

The 2026-09-06 human instruction removes source-file hash checks from warning
approvals. Do not require file hashes or hash revalidation after source edits.
The controller checks whether the approved finding and contract still apply;
unrelated edits in the same file do not invalidate an approval. The human also
approved recording exact new source-line bounds for unchanged findings in the
approval manifest. Pattern, class, method/signature, field/signature, source path
and message remain exact, and unrecorded line changes still fail. New findings
or changed contracts still require the existing approval process. Analyzer
thresholds and the frozen eligible-warning inventory remain unchanged.

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
