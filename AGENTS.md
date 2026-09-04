# Automatone Agent Rules

These are repository-wide invariants for autonomous development. They apply to planners, implementers, repair agents, reviewers, and verifiers unless a more restrictive nested `AGENTS.md` applies.

## Required reading

Before modifying implementation code, read:

1. the approved project plan (`docs/NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md` unless superseded by an explicitly approved plan);
2. `EXECUTION_STRATEGY.md`;
3. the assigned task spec under `.agents/tasks/`;
4. directly referenced architecture/source documentation and ADRs.

Do not load unrelated documentation merely to increase context.

## Source-of-truth order

When sources conflict, use this precedence:

1. explicit current human instruction;
2. approved product/architecture plan;
3. these repository invariants;
4. assigned task specification;
5. approved ADRs and pinned source/reference documentation;
6. current implementation/source code;
7. generated Graphify output and other advisory analysis.

Graphify is a structural sensor and navigation aid. It does not override source code or an approved architecture decision.

## Closed-loop requirement

A task is not complete because an implementation agent says it is complete.

A task may become `ACCEPTED` only when:

- its required acceptance criteria have observable evidence;
- every required sensor has `PASS` or an explicitly approved waiver;
- no required sensor is `UNVERIFIED`;
- no unresolved scope or architecture violation remains;
- fresh-context verification succeeds from a clean state.

Never convert `UNVERIFIED`, `SKIPPED`, or "could not run" into `PASS`.

## Skills

Use the installed `graphify` skill for structural preflight, impact queries, and graph updates when the task benefits from repository topology or duplicate-responsibility awareness.

Use the installed `old-coder` skill for evidence-first implementation/verification: define the test/gauntlet plan before coding and produce evidence from checks that actually ran.

### Required Old Coder routing

- Route all `old-coder` skill execution to the project custom subagent `luna_old_coder`, defined in `.codex/agents/luna_old_coder.toml`, using `gpt-5.6-luna` with `max` reasoning.
- The parent coordinates, assigns scope, and reviews evidence; it must not perform Old Coder work itself or silently substitute another model. Other roles that do not invoke `old-coder` keep their existing model selection.
- Implementation and targeted repair use this agent with their respective role templates. If a verifier invokes `old-coder`, use a separate fresh Luna invocation in verification-only mode, never the agent context that built or repaired the candidate.
- If the host cannot load the custom agent, use an explicit Luna/Max subagent request carrying the same role instructions when supported. If neither route is available, or delegation is prohibited, report the blocker and do not substitute parent execution. This policy does not authorize subagents in contexts that prohibit them.
- Never change the model or fall back automatically when Luna fails or is unavailable; ask the user before changing this routing policy.

Do not copy third-party skill text into repository output. Invoke/reference the installed skills.

## Graphify rules

When `graphify-out/graph.json` exists:

- prefer `graphify query`, `graphify path`, or `graphify explain` for scoped structural questions;
- do not rebuild the graph from scratch merely because it is dirty;
- after meaningful code changes, run `graphify update .` to refresh it;
- use graph findings as advisory context unless a deterministic sensor converts the finding into a project rule.

When the graph does not exist and the assigned task requires graph preflight, build it using the installed Graphify skill before implementation.

## Task isolation

- Work on one assigned task at a time.
- Respect task dependencies.
- Begin from the last accepted clean baseline.
- Do not start a later milestone to work around a failure in the current task.
- Do not silently expand scope.
- Do not alter the master plan, `EXECUTION_STRATEGY.md`, sensor policy, or these rules merely to make an implementation pass.

If the approved plan appears wrong or impossible, stop the implementation path and create a decision/blocker report with source evidence.

## Verification integrity

Never:

- weaken or delete a valid test because the implementation fails it;
- relax a static-analysis rule solely to make the current candidate green;
- suppress a new finding without documenting the reason and obtaining the authority required by `EXECUTION_STRATEGY.md`;
- claim a command ran when it did not;
- treat pre-existing technical debt as newly introduced debt, or newly introduced debt as pre-existing;
- use an LLM review as a substitute for deterministic required sensors.

Tests may be corrected only when the verifier can demonstrate that the test contradicts the approved specification. That is a spec/test defect and must be reported explicitly.

## Automatone architectural invariants

Unless the approved plan explicitly changes them:

- Automatone owns path calculation, target discovery performed by native processes, movement decisions, native process cancellation, and pathing/mining process behavior.
- The consumer worker mod owns worker adaptation, inventory hosting, product state, GUI/menu, networking, authorization, persistence, and user-facing status.
- Do not implement a second scanner, pathfinder, ore target queue, movement engine, or mining algorithm in the consumer to hide a native Automatone failure.
- Server code must not depend on client-only Minecraft classes.
- Server/world mutation and authorization remain server authoritative.
- Do not reintroduce Fabric, Quilt, or Cardinal Components runtime ownership into the NeoForge server mining path.

## Completion output

Every implementation or repair attempt must leave enough evidence for an independent verifier to determine:

- what changed;
- which acceptance criterion each change addresses;
- which checks were run;
- which checks were not run;
- remaining uncertainty;
- whether any task assumption was disproven.

The verifier, not the implementer, assigns the final task verdict.
