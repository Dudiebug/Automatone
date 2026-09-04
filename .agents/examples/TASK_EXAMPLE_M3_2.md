# Example only — M3.2 Progressive worker destruction

This is an example of the task-spec style. The bootstrap planner must regenerate the real M3.2 spec from the current approved plan and source references rather than treating this example as authoritative.

## Metadata

- **State:** `PLANNED`
- **Milestone:** M3
- **Depends on:** M3.1
- **Risk:** high
- **Sensor profiles:** `default`, `runtime_minecraft`, `architecture_sensitive`

## Objective

Make the worker controller perform real progressive server-side block destruction using 1.21.1 block/tool rules, normal durability, unbreakable behavior, and normal destruction/drop integration.

## Forbidden scope

- custom target scanner;
- custom A* or pathfinder;
- predicted loot injection;
- GUI/network work;
- bypassing native Automatone break intent.

## Example acceptance/evidence mapping

| ID | Acceptance criterion | Sensor |
| --- | --- | --- |
| AC-1 | normal positive-hardness block breaks progressively | NeoForge GameTest |
| AC-2 | appropriate tool is faster than empty hand | NeoForge GameTest |
| AC-3 | bedrock remains unbroken | NeoForge GameTest |
| AC-4 | selected tool durability is consumed appropriately | NeoForge GameTest |
| AC-5 | normal server-side destruction/drop behavior occurs | NeoForge GameTest |
| AC-6 | no forbidden consumer mining/pathing implementation added | ArchUnit/Graphify pre/post + verifier source check |
| AC-7 | no new Java/static-analysis regressions | default sensor profile |
