# Automatone Agent Rules

These are repository-wide invariants for autonomous development. They apply to planners, implementers, repair agents, reviewers, and verifiers unless a more restrictive nested `AGENTS.md` applies.

## Required reading

Before modifying implementation code, read:

1. the approved project plan (`docs/NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md` unless superseded by an explicitly approved plan);
2. `EXECUTION_STRATEGY.md`;
3. the assigned task spec under `.agents/tasks/`;
4. directly referenced architecture/source documentation and ADRs.

Do not load unrelated documentation merely to increase context.

Reuse reading already completed in the current task. A small follow-up repair
needs only the affected contract and source, not a new task specification or a
repeat of the full reading list.

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

The human owns all in-game testing. Provide a numbered in-game checklist with
expected results and the matching build. Do not launch Minecraft clients,
GameTest servers or Minecraft restart probes unless the human explicitly asks
for that run. Use code/build/unit/static checks locally and record the human's
in-game checks as PENDING. Do not hold back the build trying to do those tests.
This current instruction supersedes older autonomous GameTest directions below;
see `EXECUTION_STRATEGY.md` for the handoff and acceptance distinction.

Tasks become `COMPLETE` after their focused acceptance checks pass and the controller confirms scope and architectural invariants. The next task in the same milestone may then begin. Record deferred milestone checks as `PENDING`, never `PASS`; task completion does not accept the milestone.

For a clear, small fix, implement directly and compile affected code when needed. Source evidence plus compilation or an existing focused check can be sufficient; a new regression and a RED/GREEN cycle are not mandatory. Add a regression when it catches a plausible recurrence or resolves uncertainty, not merely because code changed. Once the relevant evidence is sufficient, stop checking and deliver. Do not routinely run full unit, GameTest, architecture, SpotBugs, CPD, or other broad suites after small edits.

For uncertain or risky behavior, use focused behavioral tests. Authorization, persistence/data loss, concurrency, and server/client boundaries need evidence appropriate to the risk; the small-fix shortcut must not substitute compilation for an unresolved behavioral question. Repeat a passing check only after a change that could invalidate it or specific new evidence.

Earlier broad checks require a concrete risk: build/dependency changes, shared infrastructure, cross-cutting refactors, concurrency, data loss, or server/client boundaries. Select checks relevant to that risk, not every available layer. Mutation tests, coverage targets, property-based tests and multiple independent review rounds are not defaults; justify them only when simpler checks cannot establish the relevant property.

After the last task, perform one fresh-context independent verification from a clean candidate. Let it supply the complete applicable milestone profile once, without an identical preliminary run. Repair failures and rerun affected checks; repeat broader checks only when repairs could invalidate their evidence. A milestone becomes `ACCEPTED` only when its required criteria/checks pass or the human explicitly approves an exception, with no unresolved scope or architecture violation. QUALITY-CLEANUP uses this milestone gate as the prerequisite to M2.

Keep useful regressions. Remove or consolidate only demonstrated redundancy or implementation-mirroring tests; default to changing when checks run. Never convert `PENDING`, `UNVERIFIED`, `SKIPPED`, or "could not run" into `PASS`.

## Skills

Use the installed `graphify` skill for structural preflight, impact queries, and graph updates when the task benefits from repository topology or duplicate-responsibility awareness.

Use the installed `old-coder` skill for explicitly requested high-assurance work or when the risk warrants it. Ordinary focused tests do not require that workflow. Skill workflows do not override this proportional verification policy.

### Astra ownership and test delegation

- Astra is the primary implementer and controller. Implement, diagnose, repair, run checks and integrate directly by default; delegate only when a bounded assignment adds value.
- Astra owns the testing foundation derived from the approved project plan: requirement-to-behavior mapping, test-layer selection, fixture/reset rules, allowed mocks and reference tests. Reuse existing tooling; define detailed tests one task at a time as contracts become concrete.
- For substantial feature tests, default to bounded GPT-5.6 Luna agents at max reasoning writing and running tests against Astra-defined specs, followed by Astra review. Each assignment names expected outcomes, failure cases, owned files, allowed mocks, focused checks and escalation conditions. Expected results come from the approved contract, not the current implementation.
- Astra may write or modify tests, fixtures and harnesses directly, handle small repairs, or take over stalled work. Implementation and repair delegation remain optional; the Luna default is not a mandatory handoff for every edit. Astra chooses exceptions and integrates results without user approval.
- Astra reviews delegated tests for plausible defect detection, real integration boundaries, deterministic isolation and nonredundant assertions. Helpers must report spec contradictions rather than change production behavior or weaken tests to pass. This review does not replace the fresh independent milestone gate.
- Assign explicit scope, owned files, acceptance checks and escalation conditions. Helpers are not alone in the checkout: preserve others' changes. Helpers must not redelegate or approve their own work.
- Every helper escalates directly to Astra on ambiguity, unexpected scope, conflicting evidence, a failed repair or lack of progress. Return current changes, results and the smallest unresolved issue instead of starting a handoff chain.
- Astra automatically reviews helper changes and evidence, repairs or rejects inadequate work, and approves integration/task completion when checks establish the criteria. Automatic review does not mean automatic PASS. Do not ask the user to review or approve helper output.
- Reuse helper contexts for related work. Preserve one fresh independent milestone verification context, separate from implementation/test authoring; Astra selects its model and reviews its evidence. Do not claim independence for Astra's own implementation context.
- Involve the user only for genuinely manual interaction, unavailable access/input, or an unresolved decision outside the approved product scope. Run automated GameTests autonomously; request user participation only for a check that actually requires a person. Tool permission restrictions remain authoritative.

Do not copy third-party skill text into repository output. Invoke/reference the installed skills.

## Graphify rules

When `graphify-out/graph.json` exists:

- prefer `graphify query`, `graphify path`, or `graphify explain` for scoped structural questions;
- do not rebuild the graph from scratch merely because it is dirty;
- batch `graphify update .` at a milestone boundary or when structural changes make the graph materially stale; small local fixes and documentation edits do not require an immediate refresh;
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

For routine repairs, a short entry in the existing evidence record or commit/PR
description is enough. Do not create a new report, task file, or per-attempt state
update unless scope, acceptance status, or a real blocker changes. Preserve exact
PASS/FAIL/PENDING/UNVERIFIED distinctions; lighter paperwork is not weaker evidence.
