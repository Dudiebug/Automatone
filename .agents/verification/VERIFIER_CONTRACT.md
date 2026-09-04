# Independent Test Verification Contract

The verifier measures the assigned scope; it does not repair code, tests, build
rules or thresholds while grading them. Follow AGENTS.md and EXECUTION_STRATEGY.md.
Use Luna only for Old Coder test work, with small assignments.

## Focused task checks

Run the smallest checks relevant to the changed behavior and necessary affected
compilation. Retain practical bug regressions. A passing focused run can support
COMPLETE after the controller maps the task criteria to evidence. Record wider
milestone obligations as PENDING, not PASS. No fresh checkout, broad profile,
mutation, coverage target, property-based test or additional reviewer is required
by default during each task. A concrete risk may justify specific broader checks.

## One milestone gate

After the final task, one fresh independent context verifies a clean candidate.
Run the complete applicable profile once, deduplicating overlapping tasks, and
check milestone criteria and diff scope in that same verification. Avoid an
identical preliminary development run. Earlier results may establish focused
regressions without being rerun; after repairs reuse still-valid gate results and
repeat only checks whose outcome could be affected. Record source identities and
why reused evidence remains applicable. A fresh-context flag is an attestation,
not proof that the runner created a new context.

QUALITY-CLEANUP is the prerequisite gate before M2. Reuse its existing evidence;
missing or uncertain exact warning dispositions remain blocking. Neither a clean
candidate commit nor focused task completion is an accepted milestone baseline.

## Results and integrity

- PASS: the measured scope passed. A selected-check runner result alone does not
  accept task/milestone criteria it did not measure or change STATE.yaml.
- FAIL: a required measurement failed or a scope/architecture violation exists.
- INCOMPLETE: a required measurement is unavailable/unexecuted (UNVERIFIED).
- BLOCKED_RECOMMENDED: approved assumptions appear contradictory or impossible;
  give evidence and return the decision to the controller.

PENDING describes a deliberately deferred milestone obligation outside a focused
run; it is not a passing sensor. Milestone acceptance requires all applicable
required checks/criteria PASS or explicit human exceptions. Retained/new debt
must remain distinct; no automatic baseline refresh or fabricated success.

Classify observed failures as LOCAL_DEFECT, HARNESS_OR_SENSOR_DEFECT,
SCOPE_VIOLATION, ARCHITECTURE_VIOLATION, REPEATED_FAILURE, ASSUMPTION_DISPROVEN or
ENVIRONMENT_FAILURE. A failed process cannot be hidden by earlier PASS markers.

Write one concise record with source identity, actual checks/results, criterion
mapping, reused evidence, failures and PENDING/UNVERIFIED obligations. Link raw
output. Use report.schema.json for machine-readable records; a duplicate prose
report or blind-review/handoff bundle is not required. Never claim a command ran
when it did not, weaken a valid failing regression, or use opinion in place of a
required deterministic check.
