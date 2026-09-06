# Automatone NeoForge server-worker task specifications

This directory is the controller-facing compilation of the approved plan in
docs/NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md. The plan remains the product
and architecture authority. The task files preserve its original 26 task IDs;
they do not add product work.

## Current authority

The approved `docs/M5_GUI_CONTROLLER.md` replaces the historical M5 inventory
with M5.1-M5.8 task files and rebases conflicting M6 assumptions. The human has
authorized implementation through that plan. M4 is ACCEPTED; see STATE.yaml.

Use .agents/STATE.yaml for current status; the planning caveats below are historical.
The approved 2026-09-06 addition docs/M4_JOBS_AND_CHUNK_LOADING.md adds M4.5
after M4.4 and supersedes no-loading/no-auto-resume assumptions in this index.

## Planning status and caveats

- All 26 task specifications are PLANNED. None is READY or ACCEPTED.
- The accepted bootstrap baseline is still null in .agents/STATE.yaml. A task
  cannot move to READY until bootstrap establishes the required baseline and
  sensor entry points.
- The plan contains historical Implemented notes for M1.2, M1.3, and M1.4.
  Those notes are claims pending independent fresh-context verification; they
  are not acceptance evidence and do not change any task state.
- The authoritative sensor policy is
  .agents/verification/SENSOR_POLICY.yaml. This assignment does not copy or
  edit that policy.
- The worktree was already dirty before these task specifications were added.
  Existing implementation, test, build, and documentation changes are
  preserved and are not treated as accepted product work.
- Future consumer worker package paths, exact NeoForge GameTest fixture paths,
  and any future menu/network package paths are UNKNOWN. Preflight must locate
  them; the specs do not invent current source locations.
- The plan states no task-level dependency edges. Dependency entries labelled
  INFERRED below are conservative scheduling edges derived from the plan's
  Recommended implementation order, not explicit product dependencies.
- M1.5 is reserved by the user for their own execution. This bootstrap/planning
  assignment creates its durable spec only; it does not authorize M1.5 or any
  product task to run.
- The controller must handle one product task per explicit request, then
  stop and report. It must not automatically progress to dependent tasks.
- GUI work is forbidden before Milestone 3 passes. The M5 files are therefore
  scheduled after the M4 proof even where an individual M5 feature could be
  designed earlier.

## Exact task inventory

| ID | Plan task | Depends on | Risk | Sensor profiles | Plan section | State |
| --- | --- | --- | --- | --- | --- | --- |
| M1.1 | Lock the source baseline | none | medium | default, architecture_sensitive | Milestone 1 / Tasks / M1.1 | PLANNED |
| M1.2 | Build and mechanically port to NeoForge 1.21.1 | INFERRED: M1.1 | high | default, architecture_sensitive, dependency_change | Milestone 1 / Tasks / M1.2 | PLANNED |
| M1.3 | Remove Fabric/Quilt/CCA runtime ownership | INFERRED: M1.2 | high | default, architecture_sensitive, runtime_minecraft | Milestone 1 / Tasks / M1.3 | PLANNED |
| M1.4 | Minimize mixins and restore native MineProcess | INFERRED: M1.3 | critical | default, architecture_sensitive, runtime_minecraft | Milestone 1 / Tasks / M1.4 | PLANNED |
| M1.5 | Dedicated-server smoke tests | INFERRED: M1.4 | high | default, architecture_sensitive, runtime_minecraft | Milestone 1 / Tasks / M1.5 | PLANNED |
| M2.1 | Create the minimal worker and inventory host | INFERRED: M1.5 | high | default, architecture_sensitive, runtime_minecraft | Milestone 2 / Tasks / M2.1 | PLANNED |
| M2.2 | Attach one explicit Automatone runtime | INFERRED: M2.1 | high | default, architecture_sensitive, runtime_minecraft | Milestone 2 / Tasks / M2.2 | PLANNED |
| M2.3 | Make direct server-side movement reliable | INFERRED: M2.2 | high | default, architecture_sensitive, runtime_minecraft | Milestone 2 / Tasks / M2.3 | PLANNED |
| M2.4 | Navigation GameTests | INFERRED: M2.3 | high | default, architecture_sensitive, runtime_minecraft | Milestone 2 / Tasks / M2.4 | PLANNED |
| M3.1 | Implement worker break-state and targeting semantics | INFERRED: M2.4 | high | default, architecture_sensitive, runtime_minecraft | Milestone 3 / Tasks / M3.1 | PLANNED |
| M3.2 | Implement real progressive destruction | INFERRED: M3.1 | critical | default, architecture_sensitive, runtime_minecraft | Milestone 3 / Tasks / M3.2 | PLANNED |
| M3.3 | Integrate cancellation with native Automatone mining | INFERRED: M3.2 | critical | default, architecture_sensitive, runtime_minecraft | Milestone 3 / Tasks / M3.3 | PLANNED |
| M3.4 | Native one-block mining proof | INFERRED: M3.3 | critical | default, architecture_sensitive, runtime_minecraft | Milestone 3 / Tasks / M3.4 | PLANNED |
| M4.1 | Add the minimal mining session and progress callback | INFERRED: M3.4 | high | default, architecture_sensitive, runtime_minecraft | Milestone 4 / Tasks / M4.1 | PLANNED |
| M4.2 | Implement finite and unlimited execution | INFERRED: M4.1 | high | default, architecture_sensitive, runtime_minecraft | Milestone 4 / Tasks / M4.2 | PLANNED |
| M4.3 | Make Stop simple, synchronous, and idempotent | INFERRED: M4.2 | critical | default, architecture_sensitive, runtime_minecraft | Milestone 4 / Tasks / M4.3 | PLANNED |
| M4.4 | Quantity/cancellation GameTests | INFERRED: M4.3 | high | default, architecture_sensitive, runtime_minecraft | Milestone 4 / Tasks / M4.4 | PLANNED |
| M4.5 | Chunk loading and automatic resume | EXPLICIT: M4.4 | critical | default, architecture_sensitive, runtime_minecraft | docs/M4_JOBS_AND_CHUNK_LOADING.md | PLANNED |
| M5.1 | Controller item and binding | EXPLICIT: M4.5 | high | default, architecture_sensitive, runtime_minecraft | Milestone 5 / Tasks / M5.1 | PLANNED |
| M5.2 | Minimal one-screen GUI and block picker | INFERRED: M5.1 | medium | default, architecture_sensitive, runtime_minecraft | Milestone 5 / Tasks / M5.2 | PLANNED |
| M5.3 | Start/Stop payloads and centralized server validation | INFERRED: M5.2 | critical | default, architecture_sensitive, runtime_minecraft, network_security | Milestone 5 / Tasks / M5.3 | PLANNED |
| M5.4 | Authoritative status/progress updates | INFERRED: M5.3 | high | default, architecture_sensitive, runtime_minecraft, network_security | Milestone 5 / Tasks / M5.4 | PLANNED |
| M5.5 | Networking tests and manual GUI acceptance | INFERRED: M5.4 | critical | default, architecture_sensitive, runtime_minecraft, network_security | Milestone 5 / Tasks / M5.5 | PLANNED |
| M6.1 | Persist ownership, binding, and product state | INFERRED: M5.5 | critical | default, architecture_sensitive, runtime_minecraft, persistence, network_security | Milestone 6 / Tasks / M6.1 | PLANNED |
| M6.2 | Define simple restart and unload semantics | INFERRED: M6.1 | critical | default, architecture_sensitive, runtime_minecraft, persistence | Milestone 6 / Tasks / M6.2 | PLANNED |
| M6.3 | Add typed native failure reporting and user error mapping | INFERRED: M6.2 | high | default, architecture_sensitive, runtime_minecraft | Milestone 6 / Tasks / M6.3 | PLANNED |
| M6.4 | Full MVP security, persistence, and failure matrix | INFERRED: M6.3 | critical | default, architecture_sensitive, runtime_minecraft, network_security, persistence | Milestone 6 / Tasks / M6.4 | PLANNED |

There are exactly 27 rows above: M1 has 5, M2 has 4, M3 has 4, M4 has 5,
M5 has 5, and M6 has 4.

## Approved source baseline ledger

The following references are copied from the locked M1.1 ledger in plan lines
28-56. They are immutable planning anchors, not permission to merge upstream
wholesale:

- Fork source baseline: Dudiebug/Automatone at
  545c552d6f3c333e32a256dd82227004954dd5c3, the 1.20 branch for Minecraft
  1.20.1.
- Planning baseline: Dudiebug/Automatone at
  d555e41d0beb96194de5ff7c0a8dac7b701d0581.
- Upstream behavioral reference: minefortress-mod/automatone at
  58d090edaa3b8fb6cfdd3b1fecacf41f60ab8d4c, main.
- The upstream reference changes 94 files relative to the fork source
  baseline. It is a behavioral reference, not a merge source.

The approved behaviors to preserve are:

- General inventory access in IEntityContext, InventoryBehavior, MineProcess,
  movement, and ToolSet; adapt it to the worker host's vanilla Container and
  selected-slot access, and do not port IMinefortressEntity.
- LivingEntity-based controller, placement, tool, and input paths; pass the
  host entity and controller directly, with no fake-player bridge.
- Non-player-safe movement calculation, including tool/effect/entity-dimension
  inputs; derive permissions and inventory from the explicit host contract.
- Generic finite-quantity inventory iteration in MineProcess through the
  host's public container API.
- The negative-height movement correction from
  d718f3abab42f9dff930c1d80871ef7124e8475f, with scanner section offsets
  validated separately during M1.4.
- A runtime-local world-provider reference in Baritone, constructed or
  injected directly in M1.3 rather than retained as a CCA key lookup.

The ledger explicitly rejects MineFortress-specific APIs and ownership hooks;
fake-player networking and client fake-player types; player-behavior,
advancement, sleep, chunk-tracking, and testmod fake-player changes; global
mob MoveControl, LookControl, or JumpControl mixins; MineFortress dummy
controller block/fluid/scaffolding and task behavior; command, renderer,
notification, metadata, build-migration, test-deletion, and publication
changes; and wholesale upstream merges or cherry-picks. Mechanical 1.21.1
updates are reimplemented against NeoForge/Mojang mappings in M1.2.

Later upstream code may be added only if a compile or runtime failure proves
it necessary for the listed native server mining path, and the exception must
be added to the approved ledger with commit, behavior, and reason before
implementation.

## Global architecture contract

Unless the approved plan is changed by the required authority:

- Automatone owns path calculation, movement decisions, target discovery
  performed by MineProcess, goal generation, inherent tool-selection
  decisions, break/place input intent, native process cancellation, and
  process-level failure reasons.
- Automatone remains a normal dependency. Do not relocate or shade it into the
  consumer mod, and do not recreate Cardinal Components on NeoForge.
- The consumer owns the worker entity, worker inventory exposed to Automatone,
  worker-specific controller, controller binding, GUI/menu/screen, NeoForge
  networking, ownership/authorization, exact source-block quantity accounting,
  persisted product state, and user-facing status/error presentation.
- The consumer must not contain a second scanner, pathfinder, target-position
  queue, movement engine, or mining algorithm.
- Server/world mutation and authorization are server authoritative.
- Server code must not depend on client-only Minecraft classes.
- Do not reintroduce Fabric, Quilt, or Cardinal Components runtime ownership
  into the NeoForge server mining path.

## Quantity semantics

For this MVP, controller quantity means source blocks successfully mined, not
inventory items or drops. Native Automatone finite MineProcess quantity is
inventory/item based and is not the product limit when Fortune, Silk Touch,
pre-existing inventory, or multi-drop blocks are involved.

- Start native mining without using its inventory-count quantity as the
  product limit.
- Increment server-authoritative progress only after a matching source block
  is actually destroyed.
- When progress reaches the requested amount, immediately call native
  getMineProcess().cancel().
- Unlimited omits that finite stop condition.
- This is a stop condition around native Automatone, not a second mining
  implementation.

## Explicitly out of scope through Milestone 6

Do not add storage automation; chest assignment or deposit behavior; chunk
tickets or loading; offline mining; work areas or exclusion zones; dashboards
or telemetry history; worker fleets; multiple workers per controller; job
queues; roaming or frontier extensions; shared ore knowledge; custom
long-range scanners; custom pathfinding; packed-worker lifecycle;
cross-dimension dispatch; or automatic retry systems.

If a milestone appears to require one of these, the implementer must first
prove that it is necessary for that milestone's acceptance test. The task
specification must not be expanded silently.

## GUI gate and first implementation slice

Do not start the GUI until Milestone 3 passes. M1-M4 task specs therefore
forbid GUI, menu, and product-network work unless the plan's explicit
milestone gate is already satisfied; M5 is scheduled after the M4 proof.

The smallest first implementation slice contains no worker GUI and no product
mining logic:

    NeoForge 1.21.1 + Java 21 Automatone
      -> dedicated server boots
      -> real runtime constructs
      -> runtime ticks
      -> real getMineProcess() is available
      -> runtime cancels/disposes cleanly

The first functional worker slice after that is only WorkerEntity,
WorkerEntityController, one isolated target ore, and
getMineProcess().mine(targetBlock). The project does not proceed to controller
UI or network product work until that target ore is reliably found and broken
by native Automatone on the dedicated server.

## Milestone completion gates

These gates are copied from the plan and remain higher-level than any one task.

### Milestone 1 gate

- NeoForge 1.21.1 dedicated server boots.
- Java 21 build passes.
- Fabric/Quilt/CCA runtime dependencies are gone from the server mining path.
- Every retained mixin applies.
- Runtime construction, ticking, and disposal are stable.
- getMineProcess() returns the real native process.
- Dedicated-server smoke tests pass consistently.

### Milestone 2 gate

- one worker == one runtime;
- Native Automatone moves the worker to a simple goal.
- simple jump/step traversal works;
- no fake client/player input layer exists;
- cancellation/removal leaves the worker idle and runtime disposed;
- movement GameTests pass consistently.

### Milestone 3 gate

- one direct native mine(block) call finds the target without consumer
  coordinates;
- worker walks to and faces/reaches it;
- normal positive-hardness progressive breaking succeeds;
- normal destruction/drop behavior occurs;
- mid-break cancel works;
- consumer has no second mining/search/pathing engine;
- mining GameTest passes consistently.

### Milestone 4 gate

- finite N means exactly N requested source blocks;
- unlimited continues until Stop/native failure/exhaustion;
- Stop prevents future successful target breaks;
- progress is server-authoritative;
- one worker has at most one active session;
- quantity/cancel tests pass.

### Milestone 5 gate

- controller binds to one worker;
- one-screen searchable GUI works;
- finite/unlimited Start is server-authoritative;
- Stop is server-authoritative;
- target/progress/status/error are authoritative;
- packet tampering tests pass;
- client has no direct authority over worker state.

### Milestone 6 gate: MVP complete

The player can:

1. own one worker;
2. bind one controller;
3. open one GUI;
4. select one block;
5. request N source blocks or unlimited;
6. Start;
7. watch server-authoritative progress;
8. Stop;
9. receive a clear failure/status when something goes wrong;
10. save/restart without losing identity, ownership, binding, configuration, or
    progress;
11. observe that interrupted work never silently resumes.

No storage automation, chunk loading, work zones, dashboards, multi-worker
dispatch, or scanner enhancements are part of this gate.

## Sensor and evidence conventions

Sensor profile names below are exactly the names in
.agents/verification/SENSOR_POLICY.yaml:

- default
- runtime_minecraft
- architecture_sensitive
- dependency_change
- network_security
- persistence
- performance_sensitive
- coverage_sensitive

The default profile requires compile, unit_tests, checkstyle, error_prone,
spotbugs, archunit, and duplication. A task-specific profile adds only the
checks listed in the task front matter. Required unavailable measurements are
UNVERIFIED, never PASS. WARN is visible but non-blocking unless a task
explicitly promotes it. Final acceptance requires a fresh-context clean run
with every required sensor PASS or an explicitly approved waiver.

Every verifier report must record the accepted candidate SHA, changed files,
criterion-to-evidence mapping, exact commands and checks, sensor statuses,
fresh-context result, warnings/UNKNOWNs, and approved waivers. Because these
specifications are PLANNED, their completion evidence is a future verifier
deliverable, not a claim about the current worktree.
