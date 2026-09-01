# Automatone NeoForge 1.21.1 Server Worker Rebuild Plan

## Purpose

This document defines the gated implementation plan for turning Automatone into the pathing/mining library behind a small NeoForge 1.21.1 server-side worker mod.

The product goal is deliberately narrow:

- One server-side worker entity.
- One `Automatone Controller` item bound to that worker.
- A GUI with a searchable block picker, finite block quantity or unlimited, Start, Stop, target, progress, and error state.
- Every action is validated and executed by the server.
- Mining is driven by Automatone's native `getMineProcess().mine(...)` and `cancel()` behavior.
- The consumer mod must not implement its own pathfinder, mining target scanner, or parallel mining engine.

The rebuild should use the current `minefortress-mod/automatone` repository as the newer behavioral reference where it has advanced beyond this fork, but implementation work remains in this repository.

## Branch and baseline

Planning branch:

`plan/neoforge-1.21.1-server-worker`

This branch is based on this fork's `1.20` branch because it is the closest existing Minecraft-version branch in the repository. Before implementation begins, reconcile the relevant server-side changes from `minefortress-mod/automatone:main` rather than assuming this branch already contains every newer server-worker change.

## Architecture contract

Keep two clear responsibilities.

### Automatone library/module

Automatone owns:

- path calculation;
- movement decisions;
- block target discovery used by `MineProcess`;
- goal generation;
- tool-selection decisions already inherent to Automatone;
- block break/place input intent;
- process cancellation;
- process-level failure reasons.

Automatone must be usable as an ordinary library by a consumer mod. It must not require the consumer to relocate/shade Baritone classes or recreate Cardinal Components on NeoForge.

### Consumer worker mod/module

The consumer owns:

- the worker entity;
- the worker inventory exposed to Automatone;
- the worker-specific `IPlayerController` implementation;
- controller item binding;
- GUI/menu/screen code;
- NeoForge networking;
- authorization and ownership;
- finite source-block counting;
- persisted product state;
- user-facing status/error presentation.

The consumer does **not** own a second scanner, pathfinder, target selector, movement engine, or mining algorithm.

## Quantity semantics

Automatone's native finite `MineProcess` quantity represents matching items in inventory, not necessarily the number of source blocks successfully broken. That can diverge because of pre-existing inventory, Fortune, Silk Touch, or multi-drop blocks.

The MVP UI requirement is interpreted as **source blocks mined**.

Therefore:

- finite source-block jobs call native mining without a native inventory quantity stop condition;
- the worker's successful target-block break callback increments authoritative source-block progress;
- when progress reaches the requested block quantity, the consumer immediately calls `getMineProcess().cancel()`;
- unlimited jobs simply omit the finite stop check.

This is a stop condition around Automatone, not a reimplementation of mining.

## Scope exclusions through Milestone 6

Do not add any of the following until all six milestones are complete:

- storage automation;
- chest assignment/deposit behavior;
- chunk tickets or chunk loading;
- offline operation;
- work areas or exclusion zones;
- dashboards or telemetry history;
- worker fleets;
- multiple workers per controller;
- job queues;
- roaming/frontier extensions;
- shared ore knowledge;
- long-range scan enhancements;
- a second scan executor;
- custom pathfinding;
- packed-worker lifecycle;
- cross-dimension dispatch;
- automatic retry policy.

If a task seems to require one of these, first prove that it is genuinely required for the current milestone's acceptance test.

---

# Milestone 1 — Port Automatone to NeoForge 1.21.1

## Goal

A NeoForge 1.21.1 dedicated server can load the real Automatone library, construct a real Baritone/Automatone runtime, tick it, and obtain its native `MineProcess` without Fabric, Quilt, or Cardinal Components runtime dependencies.

## Required code/modules

Primary work stays inside the Automatone library/module:

- API packages needed by server pathing and mining;
- `Baritone` / provider/runtime construction;
- behaviors;
- pathing/goals/movement;
- `MineProcess` and its dependencies;
- block/world scanning used by `MineProcess`;
- server-side input override handling;
- the minimum required accessors/mixins;
- dedicated-server GameTest setup.

Do not port client rendering, HUD features, command UX, schematica integration, fake-player conveniences, or unrelated MineFortress integrations unless a compile/runtime dependency proves they are needed.

## Major Fabric/Quilt-to-NeoForge replacements

- Java 17 -> Java 21.
- Fabric Loom / Quilt tooling -> NeoForge ModDevGradle or the selected NeoForge-supported Gradle path.
- Yarn/Quilt names -> Minecraft 1.21.1 Mojang/NeoForge mappings.
- `fabric.mod.json` / Quilt metadata -> `META-INF/neoforge.mods.toml`.
- Fabric/Quilt mod initializer -> NeoForge mod initialization.
- Fabric/Quilt registration -> NeoForge registry/event APIs where registration is actually required.
- Cardinal `ComponentKey<IBaritone>` -> explicit runtime ownership/lookup.
- Cardinal server ticking component -> explicit server/runtime tick.
- Cardinal world component -> explicit per-`ServerLevel` world-provider ownership.
- Cardinal controller component -> explicit controller injection/ownership.
- Client-only integration -> removed from the server library or isolated behind client-only source sets.

## Tasks

### M1.1 — Establish the exact source baseline

- [ ] Compare this fork's `1.20` branch with `minefortress-mod/automatone:main` for the server-worker changes relevant to `Baritone`, `MineProcess`, entity context, input handling, fake/server-player controllers, world scanning, and mixins.
- [ ] Produce a short `PORT_NOTES.md` or section in this document identifying which newer upstream files/behaviors must be brought forward before remapping.
- [ ] Avoid wholesale merges of unrelated client/UI/MineFortress features.
- [ ] Record the upstream commit SHA used as the behavioral reference so later debugging has a stable comparison point.

**Task acceptance:** reviewers can answer "what exact upstream behavior are we porting?" without guessing.

### M1.2 — Create the NeoForge 1.21.1 build skeleton

- [ ] Set Minecraft to 1.21.1.
- [ ] Set the supported NeoForge 21.1.x version.
- [ ] Set Java toolchain/source/target to 21.
- [ ] Replace Fabric/Quilt Gradle plugins and dependency declarations.
- [ ] Add NeoForge mod metadata.
- [ ] Configure server run and GameTest run profiles.
- [ ] Keep library and consumer separation possible in the Gradle layout; do not force product code into the Automatone core.

**Task acceptance:** `./gradlew tasks` and a minimal NeoForge server run configuration work before the full source port compiles.

### M1.3 — Perform the mechanical 1.20.x -> 1.21.1 mapping/API migration

- [ ] Move imports/types from Yarn/Quilt naming to Mojang/NeoForge naming.
- [ ] Fix registry/resource-location changes.
- [ ] Fix block state, fluid, inventory, item, entity, world, and chunk API signature changes.
- [ ] Fix Java 21 compilation warnings/errors that indicate stale assumptions.
- [ ] Do not change pathfinding behavior while doing mechanical renames unless the old API no longer has a semantic equivalent.
- [ ] Keep mechanical port commits separate from behavioral fixes where practical.

**Task acceptance:** core pathing/process packages compile against Minecraft 1.21.1.

### M1.4 — Remove Cardinal Components from the public/runtime model

- [ ] Remove CCA inheritance from `IBaritone`.
- [ ] Remove CCA inheritance/key lookup from `IPlayerController`.
- [ ] Remove CCA key lookup from entity/world context paths.
- [ ] Replace component factories with ordinary constructors/factories.
- [ ] Introduce the smallest explicit runtime lookup needed by Automatone internals, such as a provider/registry keyed by living entity and a provider keyed by server level.
- [ ] Ensure registration/unregistration is deterministic and does not retain removed entities or unloaded levels.
- [ ] Do not write a generic "NeoForge CCA clone."

**Task acceptance:** no CCA artifact is present in the runtime dependency graph and no `ComponentKey`-style lookup remains in the server mining path.

### M1.5 — Define explicit server lifecycle and ticking

- [ ] Define how an Automatone instance is constructed for a host entity.
- [ ] Define one explicit server tick entrypoint.
- [ ] Ensure a runtime is ticked exactly once per game tick.
- [ ] Define cancellation/disposal when a host entity is removed or unloaded.
- [ ] Define per-level world/cache provider creation and release.
- [ ] Verify background path/scan work cannot publish into a disposed runtime.

**Task acceptance:** a test host can be created, ticked for hundreds of ticks, disposed, and garbage-collected/recreated without duplicate registration or duplicate ticks.

### M1.6 — Audit every mixin/accessor

For each existing mixin/accessor, classify it as:

1. required by the server pathing/mining core;
2. replaced by a public/protected 1.21.1 API;
3. consumer-worker responsibility;
4. client/player-only and removable;
5. obsolete.

- [ ] Port only category 1.
- [ ] Remove broad mob/player behavior mixins when the worker class can implement the behavior locally.
- [ ] Update mixin targets/descriptors for 1.21.1.
- [ ] Add startup validation so failed required mixins are caught immediately.
- [ ] Document why each retained mixin exists.

**Task acceptance:** the retained mixin list is intentionally minimal and every retained mixin has a documented server-side reason.

### M1.7 — Restore the native `MineProcess` dependency chain

- [ ] Make `Baritone#getMineProcess()` construct and return the actual native process.
- [ ] Port `WorldScanner` and only the cache/world-data behavior `MineProcess` actually uses.
- [ ] Confirm block lookup/filter types work with 1.21.1 registries.
- [ ] Confirm asynchronous scan/path execution respects server-thread world ownership.
- [ ] Remove desktop notification/client rendering dependencies from the server execution path.
- [ ] Ensure cancellation clears process state safely.

**Task acceptance:** `MineProcess` can become active/inactive in an isolated server test without a worker product layer.

### M1.8 — Add dedicated-server smoke tests

Add tests that:

- [ ] boot the dedicated GameTest server;
- [ ] construct a test Automatone runtime;
- [ ] retrieve `getMineProcess()`;
- [ ] tick the runtime for a fixed period;
- [ ] start and immediately cancel a harmless process state where possible;
- [ ] dispose the runtime;
- [ ] assert no client-only classloading or Fabric/Quilt/CCA class is required.

## Runnable acceptance test

```bash
./gradlew clean test
./gradlew runGameTestServer
./gradlew build
```

The GameTest server must boot and the Automatone smoke tests must pass without client initialization.

## Risks / unknowns

- 1.20.x -> 1.21.1 method/signature drift.
- Mixin target changes.
- Registry and data-component migration.
- Chunk/cache API changes.
- Tool/enchantment calculation changes.
- Hidden CCA assumptions in world/entity lookup.
- Background world-scanner thread-safety assumptions.
- Accidental client-class references in server code.

## Completion gate

Milestone 1 is complete only when:

- [ ] NeoForge 1.21.1 dedicated server boots.
- [ ] Java 21 build passes.
- [ ] No Fabric/Quilt API runtime dependency remains.
- [ ] No Cardinal Components runtime dependency remains.
- [ ] Every retained mixin applies successfully.
- [ ] A server-side Automatone runtime can be constructed and ticked.
- [ ] `getMineProcess()` returns the real native process.
- [ ] Dedicated-server smoke tests pass repeatedly.

Do not begin GUI or product networking work before this gate is green.

---

# Milestone 2 — Attach Automatone to one server-side worker entity

## Goal

One non-player server-side worker entity owns one Automatone runtime and can move under Automatone control without fake-client input infrastructure.

## Required code/modules

Consumer module only, except for minimal host interfaces that clearly belong in the Automatone library:

- `WorkerEntity`;
- worker inventory/selected-slot support;
- `WorkerEntityController` shell;
- Automatone runtime ownership on the worker;
- worker lifecycle/tick integration;
- worker-specific suppression of conflicting vanilla AI controls;
- movement-focused GameTests.

## Major replacements/integration changes

- Replace upstream CCA automatic attachment with explicit worker-owned runtime construction.
- Replace generic living-entity dummy controller lookup with the worker's explicit controller instance.
- Prefer worker-local AI/control suppression over global `Mob`/`MoveControl` mixins.
- Expose inventory through a small host contract rather than MineFortress-specific product abstractions.

## Tasks

### M2.1 — Create the smallest worker entity

- [ ] Register one worker entity type.
- [ ] Give it only the attributes required for movement and survival in tests.
- [ ] Disable idle wandering, combat, following, and unrelated goals.
- [ ] Give it a deterministic server tick path.
- [ ] Add a test-only spawn command/item or GameTest helper rather than product placement UX.

**Task acceptance:** worker spawns/despawns correctly on a dedicated server with no Automatone attached yet.

### M2.2 — Define the worker host/inventory contract

- [ ] Decide whether to retain a cleaned-up version of `IMinefortressEntity` or replace it with a generic Automatone host interface.
- [ ] Expose inventory.
- [ ] Expose selected hotbar/tool slot semantics required by tool switching.
- [ ] Expose held-stack access expected by break/place helpers.
- [ ] Stub only genuinely unused hunger/bucket behavior; do not invent product features.
- [ ] Add unit tests for selected-slot and inventory consistency.

**Task acceptance:** Automatone can query the worker inventory/held item without casting to a player or MineFortress-specific entity.

### M2.3 — Make runtime ownership explicit

- [ ] Construct exactly one Automatone/Baritone runtime for each loaded worker.
- [ ] Give the runtime the worker's entity context and controller.
- [ ] Register the runtime with the library provider only if internal lookups require it.
- [ ] Tick it exactly once per worker server tick.
- [ ] Cancel/unregister it when the worker is removed/unloaded.
- [ ] Ensure reloading a worker creates one fresh transient runtime, not two.

**Task acceptance:** runtime identity remains one-to-one with the loaded worker across spawn/remove/reload tests.

### M2.4 — Integrate direct server-side movement inputs

- [ ] Confirm Automatone `InputOverrideHandler` updates the worker's forward/sideways movement correctly in 1.21.1.
- [ ] Confirm jump input causes an actual jump.
- [ ] Confirm sneak/state changes do not get overwritten.
- [ ] Confirm look/rotation updates are visible to ray tracing and movement.
- [ ] Confirm movement speed attributes are respected.

**Task acceptance:** low-level tests can force forward/jump/look input and observe the worker move without packets or fake clients.

### M2.5 — Prevent vanilla control systems from fighting Automatone

- [ ] Identify exactly which vanilla goals, navigation, `MoveControl`, `LookControl`, or `JumpControl` code can overwrite Automatone decisions.
- [ ] Disable/replace those controls in `WorkerEntity` while Automatone is active.
- [ ] Avoid global mixins unless worker-local control is impossible.
- [ ] Define idle behavior when Automatone is inactive: stand still.
- [ ] Confirm cancel returns the worker to idle instead of retaining stale movement.

**Task acceptance:** the worker follows a deterministic path without jitter, oscillation, or vanilla navigation taking control.

### M2.6 — Add a trivial navigation acceptance GameTest

- [ ] Create a flat test chamber.
- [ ] Spawn one worker.
- [ ] Give Automatone a simple reachable goal 5-8 blocks away using the native goal process.
- [ ] Tick until completion/timeout.
- [ ] Assert the worker reaches tolerance around the goal.
- [ ] Repeat with one simple jump/step obstacle.
- [ ] Remove the worker and assert runtime cancellation/disposal.

## Runnable acceptance test

`./gradlew runGameTestServer`

A non-player worker must move to a simple target using Automatone on the dedicated server.

## Risks / unknowns

- Entity tick ordering versus Automatone input publication.
- Vanilla `Mob` controls zeroing movement after Automatone sets it.
- Player-sized pathing assumptions.
- Rotation/raycast differences for custom mobs.
- Worker hitbox/step height changing path validity.
- Runtime lookup recursion between entity context and provider.

## Completion gate

- [ ] One loaded worker == one loaded Automatone runtime.
- [ ] Worker moves under Automatone control.
- [ ] Worker can jump a simple obstacle where the path requires it.
- [ ] No fake client/player input layer exists.
- [ ] Cancel/remove leaves the worker idle and runtime disposed.
- [ ] Dedicated-server movement GameTests pass.

---

# Milestone 3 — Demonstrate the worker mining one requested block

## Goal

A direct call to the worker's native `getMineProcess().mine(targetBlock)` autonomously discovers, paths to, faces, and successfully breaks one normal positive-hardness block on the dedicated server.

This is the core feasibility proof. Do not build the GUI before this passes.

## Required code/modules

- real `WorkerEntityController` block-breaking behavior;
- progressive break state;
- server-authoritative tool usage;
- break/reset/abort handling;
- normal block destruction/drop behavior;
- one isolated mining GameTest.

## Major integration replacements

Upstream's generic dummy entity controller is insufficient for normal mining because it does not implement full progressive survival block breaking. Upstream's real server-player controller delegates to the server player's interaction manager, which a custom worker does not have.

The consumer therefore needs one worker-specific controller that fulfills Automatone's `IPlayerController` contract using server-side world/entity APIs.

## Tasks

### M3.1 — Specify worker break-state semantics before coding them

Define a small state machine for:

- [ ] idle;
- [ ] start breaking block X from face F;
- [ ] continue progress each server tick;
- [ ] complete break;
- [ ] abort/reset;
- [ ] target changed;
- [ ] target became air/invalid;
- [ ] worker moved out of reach.

Document which `IPlayerController` methods transition the state.

**Task acceptance:** controller behavior can be reviewed against Automatone's `BlockBreakHelper` expectations before implementation.

### M3.2 — Implement reach and ray-trace correctness

- [ ] Use the worker's actual eye position/rotation.
- [ ] Define survival-like block reach used by Automatone.
- [ ] Reject breaking when target is no longer reachable.
- [ ] Confirm `getSelectedBlock()` reports the same block Automatone intends to break.
- [ ] Add focused tests for straight-ahead, above, and below targets.

**Task acceptance:** selected-block/raytrace tests are stable and match the worker controller's reach rules.

### M3.3 — Implement progressive block breaking

- [ ] Calculate per-tick destroy progress using Minecraft 1.21.1 block/tool semantics rather than fixed timers.
- [ ] Honor unbreakable blocks.
- [ ] Track/reset progress when the target changes.
- [ ] Expose `hasBrokenBlock`/equivalent completion signaling expected by Automatone.
- [ ] Send/update break-progress visuals only if useful; visuals are not an MVP requirement.
- [ ] Keep all world mutation on the server thread.

**Task acceptance:** an isolated controller test can break stone with an appropriate tool over multiple ticks and cannot break bedrock.

### M3.4 — Integrate tool selection and durability

- [ ] Confirm Automatone switches the worker's selected slot to the expected tool.
- [ ] Calculate speed from the actually selected `ItemStack`.
- [ ] Apply durability/usage through ordinary Minecraft item behavior where appropriate.
- [ ] Confirm an empty hand is slower than a proper tool.
- [ ] Confirm broken tools are reflected in inventory.

**Task acceptance:** controlled tests show expected relative break-time behavior for hand versus tool and inventory remains authoritative.

### M3.5 — Preserve ordinary block destruction and drops

- [ ] Use server-side block destruction APIs that respect block entity removal and loot rules.
- [ ] Ensure normal item drops spawn into the world rather than being predicted/injected directly into inventory.
- [ ] Preserve relevant NeoForge break hooks/events where supported by the chosen API path.
- [ ] Confirm Silk Touch/Fortune does not affect source-block progress semantics later.
- [ ] Keep resource pickup outside this milestone unless ordinary entity pickup already handles it.

**Task acceptance:** breaking a test ore creates normal server-side drop behavior with no direct "award target item" shortcut.

### M3.6 — Wire cancellation/reset into the break controller

- [ ] `MineProcess.cancel()` must clear any held click/break state.
- [ ] Worker removal must abort break state.
- [ ] Path revalidation/target changes must not leave ghost progress on an old block.
- [ ] Add a test that cancels halfway through a slow block and verifies it remains unbroken.

**Task acceptance:** no block completes after a clean mid-break cancel unless it had already completed synchronously before cancel.

### M3.7 — Add the direct native mining proof

GameTest structure:

- [ ] one worker;
- [ ] traversable floor;
- [ ] one isolated target ore normal hardness;
- [ ] sufficient clearance;
- [ ] no other instances of that target in the test area.

Test flow:

- [ ] call `worker.getBaritone().getMineProcess().mine(targetBlock)` directly;
- [ ] do not provide target coordinates to consumer code;
- [ ] wait for a bounded timeout;
- [ ] assert worker moved toward target;
- [ ] assert target was broken;
- [ ] assert the native mine process becomes inactive when no target remains or is explicitly cancelled.

### M3.8 — Add a "no consumer mining engine" review test/check

- [ ] Consumer module contains no A* implementation.
- [ ] Consumer module contains no ore/world target scan implementation.
- [ ] Consumer module contains no target-position queue created by a custom scanner.
- [ ] The only mining start path delegates to native `MineProcess`.

This can be a review checklist rather than an automated code scan.

## Runnable acceptance test

`./gradlew runGameTestServer`

The isolated ore must disappear because native `MineProcess` found it and drove the worker to break it.

## Risks / unknowns

- Recreating server-player progressive break behavior for a non-player entity.
- Correct mining speed APIs in 1.21.1.
- Tool/enchantment/event behavior.
- Raytrace disagreement between look behavior and break controller.
- `MineProcess` scan/cache behavior after the 1.21.1 port.
- Thread-safe publication of scan results.

## Completion gate

- [ ] One direct native `mine(block)` call locates the target without consumer coordinates.
- [ ] Worker walks to it.
- [ ] Worker faces/reaches it.
- [ ] Worker progressively breaks a normal positive-hardness block.
- [ ] Normal server-side destruction/drop behavior occurs.
- [ ] Mid-break cancel works.
- [ ] Dedicated-server mining GameTest passes consistently.

---

# Milestone 4 — Support exact source-block quantities and cancellation

## Goal

The consumer can request exactly N source blocks or unlimited mining while still delegating target discovery/pathing/mining to native Automatone.

## Required code/modules

- `MiningSession` or equivalently small product-state object;
- successful target-block break callback;
- finite/unlimited stop condition;
- explicit cancellation handling;
- progress-focused GameTests.

## Major behavior decision

Do not use native inventory-count quantity as the UI's source-block quantity. Run the native mine process and stop it after N authoritative successful target-block breaks.

## Tasks

### M4.1 — Define the minimal mining-session state model

Suggested fields:

- [ ] target block registry ID;
- [ ] finite requested source-block count or unlimited flag;
- [ ] successful source blocks broken;
- [ ] state enum: `IDLE`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`;
- [ ] last error code/message placeholder.

Do not add job IDs, queues, history, work zones, or reservations.

**Task acceptance:** state model can represent every MVP screen state without adding runtime abstractions.

### M4.2 — Add a successful target-block break callback

- [ ] Emit the callback only after a block is actually destroyed successfully.
- [ ] Include the original block identity and position for validation/debugging.
- [ ] Do not count attempted hits.
- [ ] Do not count item pickups or drops.
- [ ] Do not count unrelated obstruction blocks Automatone may break for pathing.
- [ ] Count only blocks matching the active requested target filter.

**Task acceptance:** obstruction-breaking and target-breaking tests prove only requested source blocks increment progress.

### M4.3 — Implement finite quantity stop

- [ ] Start native mining without native inventory quantity semantics.
- [ ] On each successful matching source block, increment progress synchronously.
- [ ] When progress reaches N, set session `COMPLETED` and immediately call `getMineProcess().cancel()`.
- [ ] Ensure no fourth block can be started/completed after a request for three because of delayed polling.
- [ ] Define N validation bounds in consumer code.

**Task acceptance:** with five available targets and requested quantity 3, exactly three matching source blocks are destroyed.

### M4.4 — Implement unlimited mode

- [ ] Unlimited starts the same native `MineProcess` path.
- [ ] Unlimited continues beyond normal finite thresholds.
- [ ] Unlimited ends only through explicit stop, process failure, worker removal/unload, or exhaustion/failure reported by Automatone.
- [ ] Progress still increments for display.

**Task acceptance:** unlimited passes at least three successful target breaks and remains active until explicitly cancelled in the test.

### M4.5 — Make stop/cancel synchronous and idempotent

- [ ] One public product stop method.
- [ ] It calls native `MineProcess.cancel()`.
- [ ] It clears worker break/input state.
- [ ] Repeated Stop calls are harmless.
- [ ] Stop while idle is harmless.
- [ ] Stop during path calculation prevents stale path/scan results from restarting work.

**Task acceptance:** after Stop and a 100-tick observation window, no additional requested target block is broken.

### M4.6 — Define target replacement behavior

For MVP, keep it simple:

- [ ] starting while already running is rejected, or performs a synchronous stop then clean start; choose one and document it;
- [ ] do not allow two simultaneous mining sessions per worker;
- [ ] old callbacks/results cannot update new-session progress.

Prefer **reject while busy** for the first implementation because it is easier to reason about and test.

### M4.7 — Add quantity/cancellation GameTests

Required tests:

- [ ] finite 1 of 3 available;
- [ ] finite 3 of 5 available;
- [ ] unlimited continues;
- [ ] cancel after first successful break;
- [ ] cancel halfway through a slow second block;
- [ ] unrelated obstruction block does not increment target progress;
- [ ] pre-existing matching inventory does not complete a source-block-count job;
- [ ] multi-drop/Fortune behavior does not increment by item count.

## Runnable acceptance test

`./gradlew runGameTestServer`

All finite/unlimited/cancel cases must pass on a dedicated server.

## Risks / unknowns

- Exact callback point for "successful source block destroyed."
- Automatone may break matching blocks as path obstructions before the normal target callback path; the callback must classify by actual block and active session.
- Stale asynchronous calculation after cancellation.
- One-tick stale input after process cancellation.

## Completion gate

- [ ] Request N -> exactly N requested source blocks break.
- [ ] Unlimited continues until stopped/fails.
- [ ] Cancel prevents future successful target breaks.
- [ ] Progress is authoritative server state.
- [ ] No second mining/scanning/pathing engine exists.
- [ ] Quantity and cancellation GameTests pass.

---

# Milestone 5 — Add controller item, GUI, and server-authoritative packets

## Goal

A player can bind one controller item to one worker, open a GUI, select a block and finite/unlimited quantity, start mining, stop mining, and see current target/progress/status. The server validates every state-changing action.

## Required code/modules

- `AutomatoneControllerItem`;
- controller-binding item data component;
- menu and client screen;
- searchable block picker;
- Start/Stop payloads;
- authoritative status/update payload;
- server-side validation service kept small and local to the consumer mod.

## Major NeoForge replacements/integration APIs

- Item binding uses 1.21.1 `ItemStack` data components.
- GUI opens from the logical server through NeoForge/vanilla menu APIs.
- Client screen registration stays client-only.
- Custom packets use NeoForge custom payload registration/handlers.
- World/entity mutation occurs on the server/main game thread.

## Tasks

### M5.1 — Add the controller item and binding data component

- [ ] Register `Automatone Controller` item.
- [ ] Define a versioned binding data component containing at minimum worker UUID; optionally dimension ID for diagnostics/lookup.
- [ ] Right-click/interact with owned worker to bind.
- [ ] Rebinding behavior must be explicit and simple.
- [ ] Binding writes only on the server.
- [ ] Client tooltip may display bound/unbound state from synchronized component data.

**Task acceptance:** save/reload is not required until M6, but binding must survive normal inventory synchronization during a session.

### M5.2 — Implement server-side controller use/open flow

- [ ] Using an unbound controller returns a clear status and does not open a control menu, or opens a disabled menu; choose one.
- [ ] Using a bound controller resolves the worker on the server.
- [ ] Reject missing/unloaded worker.
- [ ] Reject non-owner access.
- [ ] Open the menu from the server only after validation.
- [ ] Menu carries the minimum context required to render current worker state.

**Task acceptance:** only a valid bound controller opens an actionable menu.

### M5.3 — Build the minimal menu/screen layout

Required UI:

- [ ] worker identity/label;
- [ ] searchable block picker;
- [ ] quantity field;
- [ ] unlimited toggle;
- [ ] Start Mining button;
- [ ] Stop button;
- [ ] current target;
- [ ] progress;
- [ ] state/status;
- [ ] last error.

Do not add inventory dashboards, settings tabs, maps, path visualizers, activity logs, or storage controls.

**Task acceptance:** screen fits all MVP controls in one view at common GUI scales.

### M5.4 — Implement the searchable block picker client-side

- [ ] Enumerate the synchronized block registry on the client.
- [ ] Filter by translated display name and registry ID.
- [ ] Render block icon/name/ID clearly enough to disambiguate modded blocks.
- [ ] Do not send a packet for every search keystroke.
- [ ] Selection stores a registry ID to be sent only when starting.
- [ ] Exclude clearly non-meaningful values such as air if desired, but the server remains final authority.

**Task acceptance:** user can type `diamond`, select the desired block, and start without any server-side search API.

### M5.5 — Define minimal C2S payloads

Prefer two actions:

`StartMiningPayload`

- [ ] selected block registry ID;
- [ ] finite quantity or unlimited flag;
- [ ] menu/container/session context needed to reject stale screens.

`StopMiningPayload`

- [ ] only menu/container/session context needed for validation.

Do not trust a client-supplied worker UUID when the server can derive the worker from the server-owned controller binding/menu context.

**Task acceptance:** packet data represents user intent, not authoritative worker state.

### M5.6 — Implement server validation in one place

For Start:

- [ ] sender is a valid server player;
- [ ] expected menu is open;
- [ ] menu ID/context matches;
- [ ] controller item is still present/valid;
- [ ] binding is server-resolved;
- [ ] worker exists and is loaded;
- [ ] sender owns worker;
- [ ] block ID exists in server registry;
- [ ] block is allowed as a mining target;
- [ ] quantity is within bounds or unlimited;
- [ ] worker is not already busy under the chosen M4 policy;
- [ ] only then call `MiningSession.start()` -> native `MineProcess`.

For Stop:

- [ ] repeat identity/ownership/context validation;
- [ ] call the single product stop method.

**Task acceptance:** validation failures never mutate the worker or native process.

### M5.7 — Add authoritative S2C status updates

Send/update on state changes, not every tick:

- [ ] menu open snapshot;
- [ ] task start;
- [ ] each successful target-block break;
- [ ] completion;
- [ ] cancellation;
- [ ] failure;
- [ ] worker becomes unavailable while menu is open.

Suggested snapshot fields:

- [ ] target registry ID;
- [ ] finite quantity/unlimited;
- [ ] successful target-block count;
- [ ] session state;
- [ ] error code/message.

**Task acceptance:** client never calculates authoritative progress from animation or local inventory.

### M5.8 — Add packet tampering/validation tests

Tests must reject:

- [ ] nonexistent block ID;
- [ ] negative quantity;
- [ ] zero finite quantity;
- [ ] excessive quantity beyond configured MVP max;
- [ ] stale/wrong menu ID;
- [ ] unbound controller;
- [ ] missing/unloaded worker;
- [ ] non-owner player;
- [ ] Start while already busy under reject-while-busy policy;
- [ ] Stop from unauthorized player.

### M5.9 — Add manual dedicated-client acceptance flow

Document a reproducible dev test:

1. spawn worker;
2. give controller;
3. bind controller;
4. use controller to open GUI;
5. search `diamond`;
6. select diamond ore;
7. enter `2`;
8. Start;
9. observe authoritative `0 -> 1 -> 2` progress;
10. verify completion at exactly 2;
11. start unlimited;
12. press Stop;
13. verify movement/mining stops.

## Runnable acceptance test

Automated:

```bash
./gradlew test
./gradlew runGameTestServer
```

Manual:

run NeoForge dedicated server + client and execute the flow above.

## Risks / unknowns

- Menu lifetime versus item moved out of the player's hand/inventory.
- Worker unload while screen remains open.
- Registry IDs for modded blocks.
- Client/server status ordering after immediate completion/failure.
- Avoiding accidental trust in client-supplied worker identity.

## Completion gate

- [ ] Controller binds to exactly one worker.
- [ ] Bound controller opens one MVP GUI.
- [ ] Searchable block picker works.
- [ ] Finite and unlimited jobs start through server validation.
- [ ] Stop works through server validation.
- [ ] GUI displays authoritative target/progress/state/error.
- [ ] Packet tampering tests pass.
- [ ] Client contains no authority over worker state.

---

# Milestone 6 — Add ownership, persistence, progress, and failure reporting

## Goal

The MVP survives save/reload safely, enforces ownership, reports meaningful failure states, and never silently resumes stale runtime pathing after restart.

## Required code/modules

- worker persistent data;
- controller binding persistence verification;
- ownership checks shared by interaction/network paths;
- persisted mining configuration/progress/state;
- typed Automatone process termination/failure reason;
- user-facing error mapping;
- end-to-end persistence/failure tests.

## Tasks

### M6.1 — Persist worker identity and owner

Persist at minimum:

- [ ] stable worker UUID/identity strategy;
- [ ] owner player UUID.

Rules:

- [ ] ownership is assigned server-side;
- [ ] ownership never comes from a client packet;
- [ ] duplicate/invalid identity situations fail conservatively;
- [ ] ownership checks are centralized enough that binding, menu open, Start, and Stop cannot drift into different policies.

**Task acceptance:** after save/restart the same worker remains owned by the same player.

### M6.2 — Persist controller binding correctly

- [ ] Verify the controller's item data component serializes through save/load and normal inventory/container operations.
- [ ] Validate bound UUID on use rather than assuming the target still exists.
- [ ] A stale binding reports `WORKER_NOT_FOUND`/`WORKER_NOT_LOADED` without mutating anything.
- [ ] Do not automatically search all dimensions/chunks or add chunk loading to recover a worker.

**Task acceptance:** controller points to the same worker after restart when that worker is loaded; stale binding fails cleanly otherwise.

### M6.3 — Persist only product state, not pathing internals

Persist:

- [ ] target block ID;
- [ ] finite quantity/unlimited;
- [ ] successful target-block progress;
- [ ] session state;
- [ ] last failure code where useful.

Do **not** persist:

- [ ] A* paths;
- [ ] open/closed path nodes;
- [ ] ore target locations;
- [ ] scan generations;
- [ ] partial block-break progress;
- [ ] active Automatone process internals.

**Task acceptance:** saved entity data is understandable product configuration/state, not a serialization of the pathfinder.

### M6.4 — Define restart/unload behavior

For the MVP:

- [ ] worker unload stops runtime execution because there is no chunk loading;
- [ ] a persisted `RUNNING` session reloads as `INTERRUPTED`, not auto-resumed;
- [ ] reconstruct a fresh transient Automatone runtime from current world state;
- [ ] user must press Start again to resume/restart work;
- [ ] current progress may be retained if the product semantics are "continue remaining quantity after manual restart"; document the exact behavior.

Recommended behavior: retain progress/configuration, mark `INTERRUPTED`, and let Start continue toward the original requested total unless the user edits the job.

**Task acceptance:** restart never causes an unattended worker to begin moving/mining automatically.

### M6.5 — Add typed Automatone termination/failure reporting

Current process code should not force the consumer to parse log strings.

Add the smallest library-level observability needed, for example a termination reason or listener/event.

Initial process reasons should cover at least:

- [ ] `CANCELLED`;
- [ ] `NO_TARGETS`;
- [ ] `PATH_FAILED`;
- [ ] `BREAK_DISABLED` / equivalent impossible-to-break policy;
- [ ] generic `INTERNAL_FAILURE` only as a last-resort fallback.

Rules:

- [ ] the process that detects failure owns the reason;
- [ ] consumer does not duplicate MineProcess path/scan failure logic;
- [ ] normal finite completion is a consumer completion reason, not misreported as a mining failure;
- [ ] explicit user Stop is `CANCELLED`, not `FAILED`.

**Task acceptance:** tests can distinguish no-target, path-failed, user-cancelled, and finite-completed states without reading logs.

### M6.6 — Map library failures to product/user errors

Consumer-level errors can include:

- [ ] `WORKER_NOT_LOADED`;
- [ ] `NOT_OWNER`;
- [ ] `INVALID_BLOCK`;
- [ ] `INVALID_QUANTITY`;
- [ ] `WORKER_BUSY`;
- [ ] `INTERRUPTED`.

Automatone-level errors can include:

- [ ] `NO_TARGETS`;
- [ ] `PATH_FAILED`;
- [ ] `BLOCK_BREAK_FAILED` if the controller can identify a persistent break failure;
- [ ] `BREAK_DISABLED`.

- [ ] Keep stable machine-readable codes separate from localized/display text.
- [ ] Update the open GUI immediately when the server changes the error/state.

**Task acceptance:** every expected failure in automated tests results in a deterministic code and useful screen status.

### M6.7 — Add ownership security tests

- [ ] Player A owns worker.
- [ ] A can bind/open/start/stop.
- [ ] Player B cannot bind if policy forbids it.
- [ ] B cannot open an actionable menu for A's worker.
- [ ] B cannot Start with a forged packet.
- [ ] B cannot Stop with a forged packet.
- [ ] Ownership remains after restart.

### M6.8 — Add save/reload tests

- [ ] idle worker persists identity/owner;
- [ ] bound controller persists binding;
- [ ] configured job persists target/quantity;
- [ ] completed job persists progress/state;
- [ ] running job reloads as `INTERRUPTED`;
- [ ] fresh transient Automatone runtime is constructed once after reload;
- [ ] no mining resumes until explicit Start.

### M6.9 — Add failure-path GameTests

- [ ] no matching target -> `NO_TARGETS`;
- [ ] deliberately unreachable target -> `PATH_FAILED` or deterministic equivalent;
- [ ] break-disabled/unbreakable target -> meaningful failure;
- [ ] explicit Stop -> `CANCELLED`;
- [ ] finite completion -> `COMPLETED`;
- [ ] worker unload/removal -> `INTERRUPTED`/unavailable according to lifecycle policy.

### M6.10 — Run the complete MVP acceptance matrix

End-to-end scenarios:

- [ ] bind one controller to one worker;
- [ ] open GUI;
- [ ] choose a valid block;
- [ ] finite job completes at exact source-block count;
- [ ] unlimited job stops on command;
- [ ] progress updates correctly;
- [ ] unauthorized control is rejected;
- [ ] save/restart preserves identity/configuration/progress;
- [ ] previously running job does not auto-resume;
- [ ] no-target and path failure are visible in GUI;
- [ ] no excluded feature was required to pass the test.

## Runnable acceptance test

```bash
./gradlew clean test
./gradlew runGameTestServer
./gradlew build
```

Then run the documented dedicated-server/client manual acceptance flow once on the release candidate.

## Risks / unknowns

- Reliable mapping of all native process termination paths.
- Entity serialization/data-fixer implications for custom data.
- Worker unload versus removal distinction.
- Keeping stale async path/scan work from mutating a reconstructed runtime.
- Localized UI status staying synchronized with server state.

## Completion gate — MVP complete

The MVP is complete when a player can:

1. own one worker;
2. bind one controller to it;
3. open one controller GUI;
4. select one block;
5. request N source blocks or unlimited;
6. Start;
7. watch server-authoritative progress;
8. Stop;
9. receive clear failure status;
10. save/restart without losing ownership/binding/configuration;
11. observe that an interrupted job does not silently resume.

No storage automation, chunk loading, work zones, dashboards, multi-worker dispatch, or scan enhancements are part of this gate.

---

# Cross-cutting engineering rules

These rules apply to every task and PR.

## Keep PRs small

Prefer one task or one tightly coupled pair of tasks per PR. A PR should have one primary reason to exist.

Good examples:

- build migration;
- CCA removal;
- one mixin/accessor migration group;
- worker runtime ownership;
- worker break controller;
- exact quantity accounting;
- controller item binding;
- Start/Stop networking;
- failure reason API.

Avoid "port everything + worker + GUI" PRs.

## Tests are part of the task

A task is not done because it compiles. Every behavioral task should add or extend a unit test, GameTest, or reproducible dedicated-server acceptance test.

For bugs discovered during the port:

1. add a failing focused test if practical;
2. fix the smallest layer that owns the bug;
3. keep the regression test.

## Server thread owns Minecraft world mutation

Background threads may calculate against safe snapshots/immutable inputs where Automatone already does so, but they must not mutate entities, blocks, inventories, menus, or product state directly.

## Do not hide library problems in the consumer

If native Automatone cancellation is broken, fix Automatone cancellation.

If native Automatone cannot expose why `MineProcess` failed, add a small native failure-reason API.

If a worker adapter is genuinely required because a custom entity lacks a server-player interaction manager, implement that adapter in the consumer.

Do not create a broad `RuntimeAdapter` layer that starts absorbing native process responsibilities.

## Preserve source/license boundaries

Automatone/Baritone-derived code stays in the Automatone library under its applicable LGPL terms and notices. Consumer product code should remain clearly separate so future updates and compliance are easier to reason about.

## Prefer observable invariants

Important invariants worth asserting in tests:

- one loaded worker has one runtime;
- one worker has at most one active mining session;
- finite progress increments only after successful matching source-block destruction;
- cancel is idempotent;
- client packets cannot choose an unauthorized worker;
- unloaded workers do not continue mining;
- stale async results cannot revive cancelled/replaced work;
- server state is authoritative for every GUI status field.

---

# Recommended PR/task order

A practical sequence is:

1. `M1.1` source baseline/reconciliation notes.
2. `M1.2` NeoForge build skeleton.
3. `M1.3` mechanical mappings/API compile port.
4. `M1.4` CCA removal.
5. `M1.5-M1.6` lifecycle + mixin audit.
6. `M1.7-M1.8` native MineProcess smoke test.
7. `M2.1-M2.3` worker + runtime ownership.
8. `M2.4-M2.6` movement control and navigation GameTest.
9. `M3.1-M3.4` worker break controller and tools.
10. `M3.5-M3.8` real destruction + direct MineProcess mining proof.
11. `M4.1-M4.4` MiningSession + exact quantity/unlimited.
12. `M4.5-M4.7` cancellation/race tests.
13. `M5.1-M5.4` controller item + menu/screen/block picker.
14. `M5.5-M5.8` Start/Stop/status networking and validation.
15. `M5.9` manual GUI acceptance.
16. `M6.1-M6.4` ownership and persistence.
17. `M6.5-M6.6` native failure reasons and product mapping.
18. `M6.7-M6.10` security, restart, failure, and full MVP acceptance.

A milestone branch should not advance just because later work looks easy. Each milestone gate exists to reduce the number of systems being debugged at once.

---

# Smallest first implementation slice

The first implementation slice after this planning branch should contain **no worker GUI and no mining product logic**.

Target slice:

```text
NeoForge 1.21.1 + Java 21 Automatone
  -> dedicated server boots
  -> real Baritone runtime constructs
  -> runtime ticks
  -> real getMineProcess() is available
  -> runtime cancels/disposes cleanly
```

The first functional worker slice after that is only:

```text
WorkerEntity
+ WorkerEntityController
+ one isolated target ore
+ getMineProcess().mine(targetBlock)
```

The project should not proceed to the controller GUI until that target ore is reliably found and broken by native Automatone on the dedicated server.