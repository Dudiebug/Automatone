# {{TASK_ID}} — {{TITLE}}

## Metadata

- **Task state:** `PLANNED | READY | IMPLEMENTING | COMPLETE | BLOCKED`
- **Milestone:** {{MILESTONE}}
- **Depends on:** {{DEPENDENCIES}}
- **Risk:** `low | medium | high | critical`
- **Applicable milestone profiles:** {{PROFILES}} (PENDING until the milestone gate)
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

## Proportional verification

Inherited profiles are defined in `.agents/verification/SENSOR_POLICY.yaml`.

Focused checks required for this task (including bug regression and affected compilation where practical):

- ...

Earlier broad checks, only if a concrete risk requires them: {{CHECK_AND_REASON_OR_NONE}}.
Reuse passing evidence unless a subsequent change could invalidate it; state that reason before repeating checks.
Deferred milestone checks: {{PENDING_CHECKS}}. These are not PASS and do not block the next task after focused acceptance passes.

## Expected touched areas

Advisory only; preflight may refine this list.

- ...

Unexpected broad changes should be treated as a scope signal during verification.

## Failure / blocker conditions

Stop and report rather than inventing architecture if:

- ...

## Completion evidence

Use one concise record, not separate duplicate reports:

- candidate identity;
- changed files;
- acceptance criterion -> evidence mapping;
- exact commands/checks executed;
- sensor statuses;
- deferred milestone checks as PENDING;
- remaining warnings/uncertainty;
- any approved waivers.

The controller confirms COMPLETE after focused acceptance. One fresh independent clean-candidate verification supplies the full applicable profile at milestone completion; only then may the milestone become ACCEPTED. Do not require mutation, coverage targets, property-based tests or multiple review rounds by default.
