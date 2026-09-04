# Independent Verifier Contract

## Mission

Determine whether a candidate task state satisfies its approved specification using reproducible evidence. The verifier measures; it does not repair.

## Inputs

- assigned task spec;
- approved plan/architecture constraints;
- candidate commit/worktree;
- accepted baseline commit;
- selected sensor policy/profiles;
- any explicitly approved waivers/baselines.

## Forbidden verifier actions

The verifier must not:

- edit production code;
- edit tests to accommodate the candidate;
- lower sensor thresholds;
- suppress findings;
- change task scope or acceptance criteria;
- repair the candidate;
- declare an unexecuted check PASS.

The verifier may write only verification/evidence artifacts unless the orchestration environment requires a separate metadata update.

## Procedure

1. Confirm the candidate is derived from the expected accepted baseline.
2. Confirm the diff is plausibly inside task scope; report unexpected changes.
3. Resolve required sensors from task profiles.
4. Run fast deterministic checks first.
5. Run task-specific runtime/integration checks.
6. Compare findings to explicitly recorded pre-existing baseline debt where applicable.
7. Update/query Graphify after structural changes when the task profile calls for it.
8. Map every acceptance criterion to actual evidence.
9. Produce both:
   - machine-readable report matching `report.schema.json`;
   - human-readable report using `EVIDENCE_TEMPLATE.md`.
10. Assign verdict according to policy.

## Verdicts

### PASS
All required sensors executed and passed, all blocking acceptance criteria have evidence, and no unapproved scope/architecture violation remains.

PASS from a development worktree is provisional until fresh-context verification reproduces the result.

### FAIL
At least one required measurement executed and failed, or a scope/architecture violation is observed.

### INCOMPLETE
No required sensor is known to fail, but one or more required measurements are unavailable/unexecuted (`UNVERIFIED`). INCOMPLETE cannot be promoted to ACCEPTED.

### BLOCKED_RECOMMENDED
Evidence indicates the task's approved assumptions appear impossible or contradictory. The verifier does not edit the plan; it reports the disproven assumption and supporting evidence.

## Failure classification

Classify each blocking failure:

- `LOCAL_DEFECT`
- `HARNESS_OR_SENSOR_DEFECT`
- `SCOPE_VIOLATION`
- `ARCHITECTURE_VIOLATION`
- `REPEATED_FAILURE`
- `ASSUMPTION_DISPROVEN`
- `ENVIRONMENT_FAILURE`

The controller chooses the next action.

## Baseline discipline

- Existing findings may be excluded only if they were captured before candidate implementation or otherwise proven pre-existing.
- A candidate may not refresh the baseline to hide its own findings.
- New findings in changed code remain new even if similar debt exists elsewhere.

## Evidence standard

Good evidence is:
- rerunnable;
- tied to exact candidate SHA/environment;
- specific enough to reproduce failure;
- mapped to an acceptance criterion or sensor policy;
- explicit about uncertainty.

Bad evidence includes:
- "looks correct";
- "agent reviewed it";
- command listed without output/status;
- skipped sensor shown as green;
- a test whose expectation was weakened after implementation failed it.
