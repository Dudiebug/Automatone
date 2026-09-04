# {{TASK_ID}} — {{TITLE}}

## Metadata

- **State:** `PLANNED | READY | PREFLIGHT | IMPLEMENTING | VERIFYING | REPAIRING | ACCEPTED | REJECTED | BLOCKED`
- **Milestone:** {{MILESTONE}}
- **Depends on:** {{DEPENDENCIES}}
- **Risk:** `low | medium | high | critical`
- **Sensor profiles:** {{PROFILES}}
- **Approved plan reference:** {{PLAN_SECTION}}

## Objective

State one observable task outcome. Do not restate an entire milestone.

## Why this task exists

Explain the product/architecture reason in 1–3 paragraphs, grounded in the approved plan.

## Source and documentation references

Use only verified references.

| Type | Repository / source | Ref / version | Path / symbol | Why relevant |
| --- | --- | --- | --- | --- |
| plan | local | current | `docs/...` | task authority |
| source | owner/repo | commit SHA | `path#symbol` | observed implementation behavior |
| docs | official docs | version | section/page | version-specific API contract |

Label uncertain claims `UNKNOWN` or `INFERRED`; do not turn guesses into source facts.

## Allowed scope

- ...

## Explicitly forbidden / out of scope

- ...

Include milestone-level non-goals that are especially tempting for this task.

## Preflight questions

The mapper should answer before coding:

1. What current symbols/classes already own this responsibility?
2. What callers/dependencies are affected?
3. Does similar functionality already exist?
4. Which architecture boundaries can this task accidentally cross?
5. What runtime/test fixture can directly observe success?

Add task-specific questions here.

## Implementation constraints

- ...

## Acceptance criteria and evidence mapping

Every criterion needs an observable measurement.

| ID | Acceptance criterion | Required sensor / evidence | Blocking? |
| --- | --- | --- | --- |
| AC-1 | ... | JUnit / GameTest / ArchUnit / source assertion / etc. | yes |
| AC-2 | ... | ... | yes |

Avoid acceptance criteria such as "code looks good" when a deterministic observation can be defined.

## Required sensor profile

Inherited profiles are defined in `.agents/verification/SENSOR_POLICY.yaml`.

Task-specific required checks:

- ...

## Expected touched areas

Advisory only; preflight may refine this list.

- ...

Unexpected broad changes should be treated as a scope signal during verification.

## Failure / blocker conditions

Stop and report rather than inventing architecture if:

- ...

## Completion evidence

The final verifier report must record:

- accepted candidate commit SHA;
- changed files;
- acceptance criterion -> evidence mapping;
- exact commands/checks executed;
- sensor statuses;
- fresh-context verification result;
- remaining warnings/uncertainty;
- any approved waivers.
