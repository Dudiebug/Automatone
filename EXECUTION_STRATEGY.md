# Automatone Execution Strategy

## 1. Purpose

This document defines the durable execution flow for implementing the approved Automatone plan with coding agents. The plan defines **what** must be built. This document defines **how** autonomous work is selected, executed, measured, corrected, and accepted.

The workflow is intentionally closed-loop:

```text
Approved task/setpoint
        |
        v
   Orchestrator
    controller
        |
        v
Preflight sensors ----> repository state/context
        |
        v
 Implementer/Repair
     actuator
        |
        v
 Candidate change
        |
        v
 Independent verifier
        |
        v
 Normalized evidence
        |
        +---- PASS ------> clean verification -> ACCEPTED
        |
        +---- local FAIL -> targeted repair -> verify again
        |
        +---- design FAIL -> reject approach/re-plan
        |
        +---- assumption disproven -> BLOCKED
```

## 2. Authority boundaries

### Planner

May:
- inspect source, reference docs, Graphify, history, and the approved plan;
- distill the plan into durable task specs;
- define sensor profiles and acceptance mappings;
- propose ADRs/plan corrections.

May not:
- declare unverified implementation complete;
- silently change approved architecture while implementing a task.

### Mapper / Preflight agent

May:
- query/update Graphify;
- identify relevant symbols, callers, dependency paths, existing responsibilities, and likely impact;
- collect exact source locations and reference evidence.

May not:
- modify implementation code;
- decide task acceptance.

### Implementer

May:
- modify only the assigned task scope;
- add/modify tests required by the approved task spec;
- run sensors for development feedback.

May not:
- edit acceptance criteria or sensor policy to make its own work pass;
- approve its own task;
- expand into unrelated tasks/milestones.

### Verifier

May:
- execute required sensors;
- inspect the candidate and baseline;
- write evidence/reports;
- classify observed failures according to this strategy.

May not:
- modify production code;
- modify tests, build rules, task acceptance criteria, or sensor thresholds;
- repair the candidate it is verifying;
- convert missing evidence into a pass.

### Repair agent

May:
- receive the original task spec plus concrete verifier failures;
- make the smallest correction that addresses those failures;
- preserve already-passing behavior.

May not:
- redesign unrelated code;
- weaken the failing sensor/test;
- hide the finding with suppression unless the task explicitly permits it and evidence supports it.

### Integration reviewer

May:
- run fresh-context verification from a clean checkout/worktree;
- check diff scope, evidence completeness, and plan consistency;
- prepare/submit a PR only after acceptance criteria are satisfied.

May not:
- perform last-minute implementation fixes. Failures return to the controller.

## 3. Task lifecycle

Allowed states:

```text
PLANNED -> READY -> PREFLIGHT -> IMPLEMENTING -> VERIFYING
                                     ^              |
                                     |              v
                                     +-- REPAIRING <-+
                                                    |
                     +------------------------------+-------------------+
                     |                              |                   |
                     v                              v                   v
                  ACCEPTED                       REJECTED            BLOCKED
```

### PLANNED
Task exists but dependencies or specification are incomplete.

### READY
Dependencies are accepted and the task has:
- objective;
- scope and forbidden scope;
- source/reference anchors;
- acceptance criteria;
- required sensor profile(s).

### PREFLIGHT
The controller collects current-state evidence before actuation.

Minimum preflight:
- confirm clean accepted baseline;
- identify task dependency SHAs/states;
- run required Graphify queries when structural context is relevant;
- locate existing implementation responsibilities before creating new ones;
- identify expected touched areas;
- record known baseline sensor debt when applicable.

### IMPLEMENTING
One implementation agent receives one task. It should follow `old-coder` evidence-first behavior: state its intended verification/gauntlet before implementation and preserve auditable evidence.

### VERIFYING
An independent verifier executes the task's required sensor profile and task-specific acceptance checks.

### REPAIRING
A targeted repair agent receives:
- original task spec;
- current candidate;
- normalized failure report;
- raw sensor evidence needed to reproduce the failure.

The repair prompt must name observed failures, not simply say "try again".

### ACCEPTED
All required evidence passes and a final clean-state verification reproduces the pass.

### REJECTED
The candidate approach violated architecture/scope or accumulated enough failed repairs that the controller discards it. The task may return to READY for a fresh implementation from the accepted baseline.

### BLOCKED
Evidence shows the task cannot be completed under current approved assumptions, dependencies, or environment. A blocker must identify the disproven assumption and supporting source/runtime evidence.

## 4. Preflight policy

Before implementation, the mapper should answer only questions relevant to the assigned task. Typical questions:

- What existing classes/methods already own this responsibility?
- What calls the interface/process being changed?
- What dependencies will this change cross?
- Is similar functionality already present elsewhere?
- Which architecture constraints are at risk?
- Which task-specific tests can directly observe the desired behavior?

For Graphify-Labs Graphify:
- if a graph exists, prefer scoped `query`, `path`, and `explain` operations;
- after meaningful code changes, update the graph incrementally;
- avoid using broad graph output as a substitute for exact source evidence.

## 5. Sensor routing

Sensor definitions live in `.agents/verification/SENSOR_POLICY.yaml`.

Every task receives:
- the `default` profile;
- zero or more task-specific profiles such as `runtime_minecraft`, `architecture_sensitive`, `dependency_change`, `persistence`, `network_security`, or `performance_sensitive`;
- explicit task-specific acceptance checks.

Sensors have status:

- `PASS` — executed and satisfied its configured criterion;
- `FAIL` — executed and violated a required criterion;
- `WARN` — non-blocking finding requiring visibility;
- `UNVERIFIED` — required measurement could not be obtained;
- `SKIPPED` — intentionally not applicable according to policy/task profile.

A required `UNVERIFIED` sensor prevents acceptance.

## 6. Failure classification and controller action

### Class A — Local implementation defect

Examples:
- one GameTest fails;
- SpotBugs finds a new high-confidence defect in changed code;
- implementation forgot durability accounting.

Action:
- keep candidate;
- dispatch targeted repair;
- rerun affected fast sensors, then full required profile.

### Class B — Verification/configuration defect

Examples:
- sensor command is unavailable because bootstrap is incomplete;
- test harness cannot start for an environment reason unrelated to the candidate.

Action:
- mark `UNVERIFIED`, not PASS;
- repair the harness through a dedicated workflow/tooling task or block the candidate until the sensor is restored;
- implementation agents do not bypass the missing sensor.

### Class C — Scope or architecture violation

Examples:
- consumer introduces a second ore scanner/pathfinder;
- server package gains client-only dependencies;
- implementation crosses a forbidden module boundary to make a test pass.

Action:
- reject the violating approach;
- return to the accepted baseline or last clean candidate before the violation;
- re-plan the task implementation.

### Class D — Repeated failed repair / model fixation

Default budget:
- up to 2 targeted repair cycles on one candidate;
- if substantially the same blocking failure remains, discard candidate;
- allow 1 fresh implementation attempt from the accepted baseline, providing prior failure evidence but not the failed patch;
- if the fresh attempt still cannot satisfy the same required condition, mark task `BLOCKED` or require human/planner intervention.

These are defaults; the human/controller may adjust them for unusually expensive or trivial tasks.

### Class E — Approved assumption disproven

Examples:
- required native API does not provide a behavior assumed by the task;
- exact NeoForge version makes an acceptance condition impossible without changing architecture.

Action:
- stop implementation;
- create a blocker/ADR proposal containing exact source/runtime evidence, smallest viable plan change, and downstream impact;
- do not silently redesign the project.

## 7. Baselines and isolation

- Each task starts from the most recent **accepted** baseline, not from an arbitrary failed candidate.
- Failed attempts must not contaminate unrelated future tasks.
- Pre-existing sensor debt must be explicitly baselined if the project cannot initially be made fully green.
- New findings in changed code are never hidden inside a baseline refresh.
- Baseline updates are controller/planner actions, not implementer actions.

## 8. Verification sequence

Recommended order balances fast feedback with expensive runtime checks:

1. repository cleanliness / scope check;
2. compile/type checks;
3. focused unit tests;
4. lint/static analyzers/architecture/duplication checks;
5. full unit suite;
6. task-specific runtime/GameTests;
7. dependency/security/performance profiles if selected;
8. Graphify update + post-change structural query for architecture-sensitive changes;
9. evidence normalization;
10. fresh-context clean verification before acceptance.

The verifier may short-circuit expensive later checks after a clear blocking failure, but the report must mark unexecuted required checks `UNVERIFIED`, not PASS. Before final acceptance, every required check must have executed successfully.

## 9. Fresh-context acceptance

A green development worktree is not sufficient.

Before `ACCEPTED`:
- create/use a clean checkout/worktree at the candidate commit;
- restore dependencies from declared project configuration;
- execute the full required verification profile without relying on prior process state;
- regenerate the final evidence report from this run;
- confirm the diff is confined to the assigned task or documented prerequisite tooling work.

Only the fresh run is the final acceptance evidence.

## 10. PR policy

A PR may be prepared/submitted only when:
- task state is `ACCEPTED`;
- final evidence report exists;
- all required sensors are PASS or have explicit human waivers;
- acceptance criteria map to concrete evidence;
- no unapproved plan/architecture changes are hidden in the diff.

PR description should include:
- task ID and objective;
- concise change summary;
- acceptance criteria/evidence matrix;
- exact verification commands/results;
- residual risks or explicit warnings;
- source/reference decisions that materially affected implementation.

## 11. Human intervention points

Human/planner approval is required for:
- modifying the approved product/architecture plan;
- modifying global architecture invariants;
- permanently weakening a required sensor or threshold;
- accepting a required sensor waiver;
- baselining known static-analysis/security debt;
- resolving a blocker that requires scope change.

Routine local repairs do not require human approval when they remain inside the approved task.
