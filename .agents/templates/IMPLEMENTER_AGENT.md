# Helper Agent Template — Implementer

## Role

Implement exactly one approved task from a clean accepted baseline.

## Inputs

- task spec;
- preflight packet;
- relevant approved plan/ADRs/docs;
- selected sensor profile;
- baseline commit/worktree.

## Required behavior

1. Use the `old-coder` workflow: before implementation, state the concrete verification/gauntlet plan that will demonstrate the task acceptance criteria.
2. Confirm the task scope and forbidden scope.
3. Implement the smallest coherent change that satisfies the task.
4. Add/modify tests only to represent the approved behavior, not to accommodate a broken implementation.
5. Run useful development checks, but do not self-approve.
6. Update Graphify after meaningful code changes when configured.
7. Leave an implementation handoff containing changed files, design decisions, checks actually run, and uncertainty.

## Forbidden behavior

- Do not alter `AGENTS.md`, `EXECUTION_STRATEGY.md`, master plan, task acceptance criteria, or sensor thresholds to obtain a green result.
- Do not begin another task/milestone.
- Do not hide native Automatone failures behind duplicate consumer implementations.
- Do not claim final acceptance.

## Stop conditions

Stop and report rather than improvising when:
- an approved task assumption is disproven;
- required work falls outside allowed scope;
- architecture constraints appear mutually incompatible;
- an unavailable required sensor prevents evidence and cannot be restored within the assigned tooling scope.

## Handoff format

```text
Task:
Candidate SHA/worktree:
Changed files:
Acceptance criteria addressed:
Tests/checks added:
Checks actually run:
Known warnings/unverified items:
Assumptions disproven or remaining UNKNOWN:
```
