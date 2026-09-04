# Bootstrap Goal — Install the Closed-Loop Workflow Into Automatone

Use the installed **old-coder** and **graphify** skills for this goal.

## Objective

Integrate the repository's closed-loop autonomous development scaffold without implementing any product milestone yet.

The repository already has an approved NeoForge 1.21.1 worker plan at:

`docs/NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md`

Treat that document as the product/architecture plan unless the repository contains a newer explicitly approved replacement.

## Required work

### 1. Map the current repository

Use Graphify against the codebase and relevant local documentation.

If `graphify-out/graph.json` already exists, update/query it instead of blindly rebuilding it.

Produce a focused mapping of:
- current modules/source sets;
- Automatone/Baritone runtime ownership;
- `MineProcess` entry points and dependencies;
- controller/input abstractions;
- Fabric/Quilt/Cardinal Components coupling;
- client/server package boundaries;
- current tests/GameTests/build tooling;
- relevant duplication or overlapping responsibilities.

Do not modify product implementation during this step.

### 2. Validate and distill the plan into task specs

Read the entire approved milestone plan.

Generate one durable `.agents/tasks/<TASK-ID>.md` file for every implementation task using `.agents/tasks/TASK_TEMPLATE.md`.

Preserve the plan's task IDs, dependencies, architectural boundaries, "done when" conditions, completion gates, and explicit non-goals.

For every task:
- cite exact plan section(s);
- add source/document references only when verified;
- assign risk level;
- assign sensor profiles from `.agents/verification/SENSOR_POLICY.yaml`;
- map each acceptance criterion to the sensor/evidence capable of observing it;
- identify unresolved assumptions as `UNKNOWN` rather than inventing facts.

Do not reinterpret the plan into extra product tasks merely to make the workflow look cleaner.

### 3. Inspect the real Java/Gradle/NeoForge toolchain

Determine from repository files:
- Java version;
- Gradle version;
- NeoForge/Minecraft versions;
- existing test dependencies/plugins;
- existing CI commands;
- existing static analysis/lint/coverage/security tooling.

Then resolve tool versions compatible with the actual repository. Prefer official/current documentation and plugin sources. Do not blindly paste version numbers from this scaffold.

Target sensor stack unless compatibility evidence justifies an alternative:
- JUnit / existing test framework;
- Checkstyle;
- Error Prone;
- SpotBugs;
- ArchUnit;
- PMD CPD or an equivalent deterministic duplication detector;
- JaCoCo;
- OWASP Dependency-Check or an equivalent dependency vulnerability scanner;
- NeoForge GameTests/runtime smoke tests;
- Graphify pre/post structural checks.

Keep overlapping tools only when they measure meaningfully different failure modes.

### 4. Configure durable Gradle verification entry points

Prefer aggregate Gradle tasks with these stable conceptual names unless an existing convention is better:

- `sensorCheck` — compile + unit + lint/static/architecture/duplication checks appropriate for normal code changes;
- `sensorIntegration` — NeoForge GameTests/dedicated-server runtime verification;
- `sensorAll` — complete required repository gauntlet, including dependency/security checks when configured.

If a tool cannot be configured safely yet, do not fake success. Document it as `UNVERIFIED` in the bootstrap evidence and create the smallest explicit tooling blocker/task needed to finish it.

### 5. Encode project-specific architecture sensors

Add deterministic architecture checks where feasible for existing approved invariants, especially:
- server implementation must not depend on `net.minecraft.client..`;
- the NeoForge server mining path must not regain Fabric/Quilt/Cardinal Components runtime ownership;
- consumer worker/product code must not grow a second pathfinder/scanner/mining engine;
- client GUI/network presentation must not become the authority for world mutation or authorization.

Do not implement brittle package rules when the current package layout cannot express the invariant honestly. In that case, document the limitation and propose a better observable check.

### 6. Establish baseline policy

Run the configured sensors against the untouched/current accepted baseline before treating them as merge gates.

If the repository has pre-existing findings:
- record them explicitly;
- distinguish baseline debt from new findings;
- do not silently suppress them;
- do not refresh a baseline after implementation in a way that hides new debt.

### 7. Generate helper-agent prompts

Adapt `.agents/templates/` into runnable helper-agent definitions for the orchestration environment available in this repository/tooling.

Create roles for:
- mapper/preflight;
- implementer;
- independent verifier;
- targeted repair;
- integration reviewer.

Preserve authority boundaries from `EXECUTION_STRATEGY.md`.

### 8. Prove the workflow itself

Run one **non-product dry run** of the controller flow against a harmless verification-only task, such as verifying the current baseline/build and producing an evidence report.

The dry run must demonstrate:
- task selection;
- preflight context;
- sensor routing;
- normalized evidence;
- `UNVERIFIED` behavior for unavailable checks;
- no implementation milestone code changed.

## Required deliverables

- populated `.agents/tasks/` task specs;
- configured sensor toolchain and aggregate Gradle tasks where feasible;
- project-specific architecture checks;
- helper-agent definitions derived from templates;
- baseline verification report;
- any necessary ADR/tooling-blocker documents;
- concise bootstrap evidence report using `.agents/verification/EVIDENCE_TEMPLATE.md`.

## Hard constraints

- Do not begin M1.1 or any product implementation task.
- Do not weaken the existing product plan.
- Do not treat a missing sensor as pass.
- Do not introduce large infrastructure/framework dependencies without evidence they are justified.
- Do not replace native Automatone behavior with consumer-side implementations.
- Do not modify tests solely to manufacture a green baseline.

Finish by reporting what is operational, what remains `UNVERIFIED`, and the exact command/controller entry point that should be used to start M1.1 through the closed loop.
