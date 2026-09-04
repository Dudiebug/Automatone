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

## Proportional verification

Tasks become `COMPLETE` after their focused acceptance checks pass and the controller confirms scope and architectural invariants. The next task in the same milestone may then begin. Record deferred milestone checks as `PENDING`, never `PASS`; task completion does not accept the milestone.

During a task, run the smallest checks relevant to the change and compile affected code when needed. For a bug fix, retain or add a focused regression demonstrating the defect and correction where practical. Repeat a passing check only after a change that could invalidate it or specific new evidence. Do not routinely run full unit, GameTest, architecture, SpotBugs, CPD, or other broad suites after small edits.

Earlier broad checks require a concrete risk: build/dependency changes, shared infrastructure, cross-cutting refactors, concurrency, data loss, or server/client boundaries. Select checks relevant to that risk, not every available layer. Mutation tests, coverage targets, property-based tests and multiple independent review rounds are not defaults; justify them only when simpler checks cannot establish the relevant property.

After the last task, perform one fresh-context independent verification from a clean candidate. Let it supply the complete applicable milestone profile once, without an identical preliminary run. Repair failures and rerun affected checks; repeat broader checks only when repairs could invalidate their evidence. A milestone becomes `ACCEPTED` only when its required criteria/checks pass or the human explicitly approves an exception, with no unresolved scope or architecture violation. QUALITY-CLEANUP uses this milestone gate as the prerequisite to M2.

Keep useful regressions. Remove or consolidate only demonstrated redundancy or implementation-mirroring tests; default to changing when checks run. Never convert `PENDING`, `UNVERIFIED`, `SKIPPED`, or "could not run" into `PASS`.

## Skills

Use the installed `graphify` skill for structural preflight, impact queries, and graph updates when the task benefits from repository topology or duplicate-responsibility awareness.

Use the installed `old-coder` skill for focused test work under this policy. Its optional extended testing layers do not override the proportional verification policy above.

### Required Old Coder routing

- Route all `old-coder` skill execution to the project custom subagent `luna_old_coder`, defined in `.codex/agents/luna_old_coder.toml`, using `gpt-5.6-luna` with `max` reasoning.
- The parent implements and repairs production/workflow code, assigns small test scopes, and reviews evidence. Use Luna only for Old Coder test work; no implementation or classification swarm, redundant reviewers, or automatic handoff chain.
- Reuse a test agent for related focused work. At the milestone gate use one fresh Luna context for independent test verification, separate from the context that authored the tests. The parent must not silently substitute another model for Old Coder test work.
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
- Begin a milestone from its accepted prerequisite; within it, continue from the preceding completed task's candidate.
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

Keep one concise evidence record: changes, checks/results, relevant reused evidence, and deferred milestone checks. The controller confirms task completion; the independent verifier supplies milestone acceptance evidence. Do not create separate reports repeating the same information.
