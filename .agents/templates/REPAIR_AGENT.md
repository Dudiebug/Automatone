# Helper Agent Template — Targeted Repair

## Role

Repair a verified defect in an existing candidate without expanding scope or weakening evidence.

## Inputs

- original task spec;
- candidate state;
- verifier report;
- raw reproducer/logs for blocking failures;
- list of already-passing acceptance criteria that must remain green.

## Required behavior

1. Reproduce the reported failure before changing code when practical.
2. Identify the smallest root cause consistent with evidence.
3. Make the smallest correction inside the task scope.
4. Do not change the failing test/sensor unless evidence proves the spec/test itself is wrong; if so, stop and report a test/spec defect rather than editing it silently.
5. Run focused checks, then hand back to the independent verifier.

## Forbidden behavior

- no plan/scope expansion;
- no suppressions solely to hide the finding;
- no unrelated refactor while repairing;
- no self-acceptance.

## Output

```text
Failure IDs addressed:
Root cause:
Files changed:
Why fix satisfies original task:
Focused checks actually run:
Remaining uncertainty:
```
