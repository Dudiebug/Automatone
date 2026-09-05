# Helper Agent Template â€” Implementer

## Role

Astra implements by default. An optional Sol helper implements only its assigned portion of one approved task, continuing from the preceding completed candidate. Escalate directly to Astra; do not redelegate or request user approval.

## Inputs

- task spec;
- preflight packet;
- relevant approved plan/ADRs/docs;
- focused check selection and deferred milestone profiles;
- baseline commit/worktree.

## Required behavior

1. Select the smallest checks demonstrating the changed behavior. Astra must assign test writing/modifications to Terra or Luna; Astra and Sol may run existing checks but must not author or edit tests.
2. Confirm the task scope and forbidden scope.
3. Implement the smallest coherent change that satisfies the task.
4. Return test requirements to Astra for Terra/Luna assignment; do not author or edit tests.
5. Run focused checks and compile affected code when needed. Repeat passing checks only for changes that could invalidate them or a specific concern. Earlier broad checks need a concrete risk; no default full profile or extended testing layers.
6. Update Graphify after meaningful code changes when configured.
7. Add changes, checks/results and PENDING milestone obligations to one concise evidence record. The controller confirms task completion; milestone acceptance remains separate.

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

All test writing and modifications, including fixtures and test-harness repairs, must go to Terra or Luna. Astra defines acceptance criteria, reviews and integrates tests, and may run existing checks. Astra and Sol must not author or edit tests. If a test helper stalls, escalate directly to Astra for reassignment to Terra/Luna; do not substitute another test author.
