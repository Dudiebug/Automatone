# Automatone Closed-Loop Agent Workflow Bundle

This bundle turns a repository plan into a durable, sensor-driven execution workflow for coding agents.

It is designed around a control-system model:

- **Setpoint:** approved project plan + task acceptance criteria
- **Controller:** orchestration policy in `EXECUTION_STRATEGY.md`
- **Actuator:** implementation / repair agents
- **Plant:** repository + Gradle + Minecraft/NeoForge runtime
- **Sensors:** tests, analyzers, architecture checks, graph queries, runtime GameTests
- **Feedback:** normalized verification evidence used to accept, repair, retry, or block a task

## Intended usage

1. Put the contents of this bundle at the repository root.
2. Keep the existing project plan as the authoritative product plan. For Automatone, the current plan is expected at:
   `docs/NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md`
3. Install or expose the `old-coder` and `graphify` skills to the coding agent.
4. Give the coding agent `BOOTSTRAP_GOAL.md` as a one-time setup goal.
5. Review the generated task specs and sensor configuration before implementation begins.
6. Execute one task at a time through the closed loop defined in `EXECUTION_STRATEGY.md`.

## What this bundle intentionally does not do

- It does not copy or vendor third-party skills.
- It does not invent dependency/tool versions. The bootstrap agent must resolve versions compatible with the repository's actual Java, Gradle, NeoForge, and Minecraft versions.
- It does not mark unavailable sensors as passing. Required checks that cannot run are `UNVERIFIED` and block acceptance until resolved or explicitly waived by the human/controller.
- It does not replace the product plan with generic agent instructions.

## Core files

- `AGENTS.md` — invariants every agent must obey.
- `EXECUTION_STRATEGY.md` — controller state machine, retry policy, task lifecycle, and role boundaries.
- `.agents/verification/SENSOR_POLICY.yaml` — sensor catalog and profile routing.
- `.agents/tasks/TASK_TEMPLATE.md` — durable task specification format.
- `.agents/templates/` — helper-agent role templates.
- `.agents/verification/VERIFIER_CONTRACT.md` — independent verifier rules.
- `.agents/verification/EVIDENCE_TEMPLATE.md` — human-readable evidence report.
- `.agents/verification/report.schema.json` — normalized machine-readable verifier result.
- `BOOTSTRAP_GOAL.md` — one-time goal to integrate this scaffold into the real project.

## Philosophy

The implementation agent is never the authority on whether its own task is complete. Completion is a controller decision based on independent evidence against the approved task specification.
