# Approved disposition rule for retained baseline warnings

Status: APPROVED on 2026-09-04. Individual technical dispositions still require
independent verification before application.

Human authorization: "Yes. if you find bugs fix them it's simple as that. it's in
the workflow." This approves the rule below and continued prerequisite bug fixes;
it is not a blanket exception for the 84 findings.

## Approved decision

Permit narrowly documented exceptions for intentional API/ownership warnings
already present at `e81dbad7ab53647920c61d824fb927f15ef67a89`, only after a fresh
independent verifier confirms each exact exception meets all conditions below.
Keep demonstrated defects, uncertain findings, and new findings blocking.
This is not approval to waive the 84 retained findings as a group.

## Concrete conflict

The fresh main SpotBugs report includes these seven findings in `baritone.Baritone`:

| Pattern | Method | Contract that must be preserved |
| --- | --- | --- |
| EI_EXPOSE_REP | getBuilderProcess | Return the runtime's native process, not a detached replacement. |
| EI_EXPOSE_REP | getCustomGoalProcess | Goal/path requests must control this runtime's native process. |
| EI_EXPOSE_REP | getInputOverrideHandler | Input changes must reach the runtime's actual input handler. |
| EI_EXPOSE_REP | getMineProcess | The approved architecture explicitly requires the real native MineProcess. |
| EI_EXPOSE_REP | getPlayerContext | Return the exact supplied worker/host context. |
| EI_EXPOSE_REP | getSelectionManager | Return the runtime's live selection manager. |
| EI_EXPOSE_REP2 | constructor(IPlayerContext, Path) | Explicit host injection retains the actual host context. |

Source: `src/main/java/baritone/Baritone.java`, its public API contracts, and
the clean-worktree `build/reports/spotbugs/main.xml`. Returning/storing detached
copies merely to eliminate these warnings would change runtime ownership.
The separate static-executor exposure warning is not included in this example
set and still requires assessment.

## 