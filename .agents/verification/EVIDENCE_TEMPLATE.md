# Evidence — {{TASK_ID}} {{TITLE}}

## Verdict

`PASS | FAIL | INCOMPLETE | BLOCKED_RECOMMENDED`

- **Candidate commit:** `...`
- **Accepted baseline:** `...`
- **Verifier:** `...`
- **Fresh-context run:** `yes/no`
- **Timestamp:** `...`

## Scope check

- Expected touched areas: ...
- Actual changed files: ...
- Unexpected changes: `none | list`

## Acceptance matrix

| Criterion | Expected | Measurement | Result | Evidence |
| --- | --- | --- | --- | --- |
| AC-1 | ... | ... | PASS/FAIL/UNVERIFIED | report/test/log reference |

## Sensor results

| Sensor | Required? | Command/method | Status | New findings | Notes |
| --- | ---: | --- | --- | ---: | --- |
| compile | yes | `...` | PASS | 0 | |
| unit_tests | yes | `...` | PASS | 0 | |

## Blocking failures

For each blocking failure:

### {{FAILURE_ID}}

- **Classification:** `LOCAL_DEFECT | HARNESS_OR_SENSOR_DEFECT | SCOPE_VIOLATION | ARCHITECTURE_VIOLATION | REPEATED_FAILURE | ASSUMPTION_DISPROVEN | ENVIRONMENT_FAILURE`
- **Sensor/check:** ...
- **Expected:** ...
- **Observed:** ...
- **Reproduction:** ...
- **Relevant files/symbols:** ...
- **Recommended controller action:** targeted repair / reject approach / fresh attempt / block

## Warnings / advisory findings

- ...

## Unverified items

Anything that did not execute must appear here. Required entries make the overall result INCOMPLETE.

- ...

## Baseline debt comparison

- Known pre-existing findings: ...
- New findings introduced by candidate: ...

## Graph / architecture observations

Advisory unless mapped to a deterministic task gate.

- ...

## Final statement

State exactly what the evidence proves and what it does not prove. Do not claim broader correctness than the configured sensors and approved specification support.
