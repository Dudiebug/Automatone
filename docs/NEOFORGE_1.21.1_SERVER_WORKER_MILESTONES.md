# Automatone NeoForge 1.21.1 Server Worker Rebuild Plan

## Purpose

Build a deliberately small NeoForge 1.21.1 server-side worker around native Automatone rather than rebuilding Baritone inside the consumer mod.

MVP:

- one server-side worker entity;
- one `Automatone Controller` item bound to that worker;
- one GUI with a searchable block picker;
- finite source-block quantity or unlimited;
- Start and Stop;
- current target, progress, status, and error;
- every state-changing action validated and executed by the server;
- mining delegated to native `getMineProcess().mine(...)` and `cancel()`.

Implementation work stays in this repository. Use `minefortress-mod/automatone:main` as the newer behavioral reference where it has server-side changes that this fork's `1.20` branch does not yet contain.

## Branch and baseline

Planning branch:

`plan/neoforge-1.21.1-server-worker`

This branch is based on this fork's `1.20` branch because it is the closest existing Minecraft-version branch in the repository.

### Locked M1.1 baseline ledger

These immutable commits define the port baseline:

- fork source baseline: `Dudiebug/Automatone@545c552d6f3c333e32a256dd82227004954dd5c3` (`1.20`, Minecraft 1.20.1);
- planning baseline: `Dudiebug/Automatone@d555e41d0beb96194de5ff7c0a8dac7b701d0581`;
- upstream behavioral reference: `minefortress-mod/automatone@58d090edaa3b8fb6cfdd3b1fecacf41f60ab8d4c` (`main`).

The upstream reference changes 94 files relative to the fork source baseline. It is a behavioral reference, not a merge source. Bring forward only these server-worker-relevant behaviors, adapting them to the explicit host API introduced in M1.3:

| Upstream behavior to preserve | Why the server worker needs it | Port rule |
| --- | --- | --- |
| General inventory access in `IEntityContext`, `InventoryBehavior`, `MineProcess`, movement, and `ToolSet` | A non-player worker must count items, select tools, and expose a hotbar without `PlayerInventory` ownership | Use the worker host's vanilla `Container` and selected-slot access; do not port `IMinefortressEntity` |
| `LivingEntity`-based controller, placement, tool, and input paths | Pathing and mining must operate on a server-side non-player entity | Pass the host entity and controller directly; retain no fake-player bridge |
| Non-player-safe movement calculation, including tool/effect/entity-dimension inputs | Native path cost and movement decisions must work without a `Player` | Preserve the generic calculations, but derive permissions and inventory from the explicit host contract |
| Generic finite-quantity inventory iteration in `MineProcess` | Native `MineProcess` must remain functional for inventory-based callers even though the product later counts source blocks separately | Iterate the host inventory through its public container API |
| Negative-height movement correction from `d718f3abab42f9dff930c1d80871ef7124e8475f` | 1.21.1 worlds routinely contain targets below Y=0 | Preserve the corrected vertical-distance behavior and separately validate scanner section offsets during M1.4 |
| Runtime-local world-provider reference in `Baritone` | Explicit lifecycle ownership must not repeatedly depend on a component lookup | Construct/inject the provider directly in M1.3; do not retain the CCA key lookup |

Explicitly reject these upstream changes:

- `IMinefortressEntity`, `PlayerMinefortressEntity`, `IBlockPosControl`, `IFortressAwareBlockEntity`, and every other MineFortress-specific API or ownership hook;
- fake-player networking, client fake-player types, player-behavior mixins, advancement/sleep/chunk-tracking exceptions, and the testmod fake-player implementation;
- global mob `MoveControl`, `LookControl`, `JumpControl`, or AI-cancellation mixins; worker-local control belongs to the later consumer worker milestone;
- upstream `DummyEntityController` block/fluid/scaffolding implementation, fortress placer tracking, pillaring cleanup, and other MineFortress task behavior;
- command disabling, client renderer/notification changes, Fabric metadata/build migration, test deletion, and publication workflow changes;
- wholesale upstream merges or cherry-picks. Mechanical 1.21.1 API updates are reimplemented against NeoForge/Mojang mappings in M1.2.

This ledger is the complete M1.1 import decision. Later upstream code is included only if a compile or runtime failure proves it necessary for the listed native server mining path, and that exception must be added here with its commit, behavior, and reason before implementation.

## Task sizing rule

This plan intentionally has **26 implementation tasks total**.

| Milestone | Tasks |
| --- | ---: |
| 1. NeoForge port | 5 |
| 2. Worker attachment | 4 |
| 3. Mine one block | 4 |
| 4. Quantity and cancellation | 4 |
| 5. Controller, GUI, networking | 5 |
| 6. Ownership, persistence, failures | 4 |
| **Total** | **26** |

A task is a coherent implementation unit that could reasonably be one PR or one tightly related pair of commits. Checklist bullets inside a task are implementation notes and acceptance details, **not additional project tasks**.

Do not split a task just because several classes are involved. For example, progressive breaking, tool durability, and normal drops all belong to the same worker block-breaking task.

## Architecture contract

### Automatone library/module owns

- path calculation;
- movement decisions;
- target discovery performed by `MineProcess`;
- goal generation;
- tool-selection decisions already inherent to Automatone;
- break/place input intent;
- native process cancellation;
- process-level failure reasons.

Automatone must be usable as a normal dependency. Do not relocate/shade it into the consumer mod, and do not recreate Cardinal Components on NeoForge.

### Consumer worker mod/module owns

- worker entity;
- worker inventory exposed to Automatone;
- worker-specific controller implementation;
- controller item binding;
- GUI/menu/screen;
- NeoForge networking;
- ownership and authorization;
- exact source-block quantity accounting;
- persisted product state;
- user-facing status/error presentation.

The consumer must **not** contain a second scanner, pathfinder, target-position queue, movement engine, or mining algorithm.

## Quantity semantics

Native Automatone finite `MineProcess` quantity is inventory/item based, which is not the same as "mine N source blocks" when Fortune, Silk Touch, pre-existing inventory, or multi-drop blocks are involved.

For the MVP, the controller quantity means **source blocks successfully mined**.

Therefore:

- start native mining without using its inventory-count quantity as the product limit;
- increment server-authoritative progress only after a matching source block is actually destroyed;
- when progress reaches the requested amount, immediately call `getMineProcess().cancel()`;
- unlimited omits that finite stop condition.

This is a stop condition around native Automatone, not a second mining implementation.

## Explicitly out of scope through Milestone 6

Do not add:

- storage automation;
- chest assignment/deposit behavior;
- chunk tickets/loading;
- offline mining;
- work areas or exclusion zones;
- dashboards or telemetry history;
- worker fleets;
- multiple workers per controller;
- job queues;
- roaming/frontier extensions;
- shared ore knowledge;
- custom long-range scanners;
- custom pathfinding;
- packed-worker lifecycle;
- cross-dimension dispatch;
- automatic retry systems.

If a milestone appears to require one of these, first prove it is actually necessary for that milestone's acceptance test.

---

# Milestone 1 — Port Automatone to NeoForge 1.21.1

## Goal

A NeoForge 1.21.1 dedicated server can load the real Automatone library, construct and tick a real runtime, and obtain the native `MineProcess` without Fabric, Quilt, or Cardinal Components runtime dependencies.

## Required code/modules

- Automatone API required by server mining/pathing;
- `Baritone` and provider/runtime construction;
- behaviors, goals, pathing, movement;
- `MineProcess` and its scanner/cache dependencies;
- server-side input override handling;
- minimum required accessors/mixins;
- dedicated-server GameTest configuration.

Do not port client rendering, HUDs, command UX, schematica, or unrelated MineFortress features unless compilation/runtime evidence shows the mining core requires them.

## Major Fabric/Quilt -> NeoForge replacements

- Java 17 -> Java 21;
- Fabric/Quilt build plugins -> NeoForge-supported Gradle tooling;
- Yarn/Quilt names -> Minecraft 1.21.1 Mojang/NeoForge mappings;
- Fabric/Quilt metadata/initializers -> NeoForge mod metadata/initialization;
- Fabric/Quilt registration -> NeoForge registry/event APIs only where required;
- Cardinal entity/world components -> explicit Java-owned runtime/controller/world-provider lifecycle;
- client-only integration -> removed or isolated away from dedicated-server code.

## Tasks — 5 total

### M1.1 — Lock the source baseline

- Compare this fork's `1.20` branch against `minefortress-mod/automatone:main` for server-worker-relevant changes.
- Record the upstream reference commit SHA.
- Bring forward only the behavior needed for `Baritone`, `MineProcess`, entity context, input/controller handling, world scanning, and required accessors.
- Avoid wholesale merging unrelated client/MineFortress features.

**Done when:** the team can identify exactly which upstream behavior is being ported and why.

### M1.2 — Build and mechanically port to NeoForge 1.21.1

- Configure Minecraft 1.21.1, a compatible NeoForge 21.1.x version, and Java 21.
- Replace Fabric/Quilt Gradle plugins, metadata, dependencies, and initialization.
- Convert mappings/imports and repair 1.20.x -> 1.21.1 registry, item, block, entity, world, chunk, fluid, and inventory API changes.
- Keep behavior changes separate from mechanical remapping where practical.

**Done when:** the server-side pathing/process source compiles against NeoForge 1.21.1.

**Implemented:** the mechanical Mojmap/API port uses `cabaletta/baritone@f3a51d47a05fa4fc9cacd6d90091f617a8d685df`
(`1.21.1`) as its mapping reference. The project now builds with Java 21, NeoForge `21.1.249`, and
ModDevGradle `2.0.144`; `clean test build` passes. Runtime ownership remains an M1.3 concern, and the
server-only mixin/client dependency audit remains an M1.4 concern.

### M1.3 — Remove Fabric/Quilt/CCA runtime ownership

- Remove Cardinal Components inheritance and key lookups from `IBaritone`, controllers, entity context, and world context.
- Replace component factories with ordinary constructors/factories.
- Define the smallest explicit runtime and per-level world-provider lookup required by Automatone internals.
- Define one server tick/disposal lifecycle and ensure removed hosts cannot retain live runtimes.

Do not build a generic NeoForge replacement for CCA.

**Done when:** the mining path has no Fabric/Quilt/CCA runtime dependency and a test runtime can be created, ticked once per tick, and disposed cleanly.

**Implemented:** `BaritoneProvider` is now an ordinary explicit owner: callers supply the host context and
data directory, duplicate creation for one context is prevented, NeoForge's server post-tick event advances
owned runtimes once, unavailable hosts are removed, and server shutdown disposes all runtimes. `Baritone`
owns its `WorldProvider` directly, and disposal is idempotent. The focused provider lifecycle test covers
create/deduplicate/tick/host-removal/dispose; real dedicated-server construction remains part of M1.5.

### M1.4 — Minimize mixins and restore native `MineProcess`

- Audit each existing mixin/accessor and retain only those truly required by the server pathing/mining core.
- Prefer public 1.21.1 APIs or consumer-worker-local behavior over broad mob/player mixins.
- Update required mixin targets/descriptors.
- Restore the real `Baritone#getMineProcess()`, scanner/cache dependency chain, block filters, async calculation publication, and cancellation behavior.
- Remove client notification/render dependencies from the server mining path.

**Done when:** the retained mixin list is small and documented, and native `MineProcess` can activate/cancel in a server-only test.

**Implemented:** the retained server-core mixin/accessor list is empty. The obsolete Yarn-era entity, mob,
fake-player, server-player, command-source, chunk-manager, shutdown, `ItemStack`, loot-table, client-chunk,
and palette hooks were removed. Chunk scanning and block-state access now use public 1.21.1 APIs; the slower
public `PalettedContainer#get` scan is the intentional M1 baseline. `BlockOptionalMeta` matches target block
items without injected `ItemStack` state or a fabricated client-side server/resource loader (loot-derived exact
quantity semantics remain M4 scope).

`Baritone#getMineProcess()` exposes the native `MineProcess`. Starting a mine now establishes process state
without client initialization, cancellation invalidates outstanding scans, and asynchronous scan results are
published on the owning tick only when their generation is still current. Mine failures use ordinary logging,
with no toast, desktop-notification, or renderer path. `MineProcessLifecycleTest` verifies native activation,
cancellation, and the concrete `Baritone` accessor without a client runtime. Dedicated-server construction and
world-backed scanning remain explicitly M1.5.

### M1.5 — Dedicated-server smoke tests

Add GameTests/smoke tests that:

- boot the dedicated server;
- construct a server-side Automatone runtime;
- retrieve the real `getMineProcess()`;
- tick for a bounded period;
- activate/cancel a harmless process state;
- dispose and recreate the runtime;
- verify no client, Fabric, Quilt, or CCA class is required.

**Implemented:** the NeoForge GameTest server boots and exercises the real provider, server-level runtime,
native `MineProcess`, ticking, cancellation, disposal/recreation, cache lifecycle, ownership boundaries, and
scanner behavior. The final bounded run passes 67 unit tests, four architecture tests, and 13 dedicated-server
GameTests. This establishes the M1 server-library foundation; it does not create the M2 worker entity or make
the branch a user-ready server mod. The required cleanup workflow remains blocked by 84 retained SpotBugs
findings, with no new unmatched findings and no approved waiver.

## Runnable acceptance test

```bash
./gradlew clean test
./gradlew runGameTestServer
./gradlew build
```

## Risks / unknowns

- Minecraft 1.21.1 signature and mapping drift;
- mixin target changes;
- chunk/cache APIs;
- tool/enchantment APIs;
- hidden CCA assumptions;
- scanner/path calculation thread-safety;
- accidental client-class references.

## Completion gate

Milestone 1 is complete when:

- NeoForge 1.21.1 dedicated server boots;
- Java 21 build passes;
- Fabric/Quilt/CCA runtime dependencies are gone from the server mining path;
- every retained mixin applies;
- runtime construction/ticking/disposal is stable;
- `getMineProcess()` returns the real native process;
- dedicated-server smoke tests pass consistently.

---

# Milestone 2 — Attach Automatone to one server-side worker entity

## Goal

One non-player server-side worker owns one Automatone runtime and can move under Automatone control without fake-client input infrastructure.

## Required code/modules

- `WorkerEntity`;
- worker inventory/selected-slot host support;
- initial `WorkerEntityController` shell;
- runtime attachment/ticking/disposal;
- worker-local suppression of conflicting vanilla AI/control;
- movement GameTests.

## Major integration replacements

- explicit worker-owned runtime instead of CCA automatic attachment;
- explicit worker controller instead of generic component lookup;
- small generic host/inventory contract instead of MineFortress product abstractions;
- worker-local AI/control suppression instead of broad global mob mixins where possible.

## Tasks — 4 total

### M2.1 — Create the minimal worker and inventory host

- Register one worker entity with only required attributes.
- No wandering, combat, following, storage behavior, or product AI.
- Expose inventory, held item, and selected-slot semantics needed by Automatone tool switching.
- Add a GameTest helper/test spawn path instead of building placement UX yet.

**Done when:** worker spawn/despawn and inventory/selected-slot behavior work on a dedicated server.

### M2.2 — Attach one explicit Automatone runtime

- Construct exactly one runtime per loaded worker.
- Supply the worker entity context and controller explicitly.
- Tick the runtime exactly once per server tick.
- Cancel/unregister on unload/removal.
- Recreate one fresh transient runtime after reload.

**Done when:** one loaded worker always maps to exactly one live runtime.

### M2.3 — Make direct server-side movement reliable

- Verify Automatone forward/sideways/jump/look input moves the custom entity.
- Prevent vanilla goals/navigation/`MoveControl`/`LookControl`/`JumpControl` from overwriting active Automatone decisions.
- Prefer solving conflicts in `WorkerEntity`, not global mixins.
- Define inactive behavior as simply standing still.
- Confirm cancel clears stale movement.

**Done when:** the worker moves deterministically without packets, fake clients, jitter, or vanilla navigation taking control.

### M2.4 — Navigation GameTests

Test:

- flat 5-8 block goal;
- one simple step/jump obstacle;
- cancellation while moving;
- removal/disposal after movement.

Use native Automatone goal/pathing behavior, not a custom movement script.

## Runnable acceptance test

```bash
./gradlew runGameTestServer
```

## Risks / unknowns

- entity tick ordering;
- custom mob hitbox/step-height assumptions;
- rotation/raycast behavior;
- vanilla movement controls zeroing Automatone input;
- runtime lookup recursion or duplicate ticks.

## Completion gate

- one worker == one runtime;
- native Automatone moves the worker to a simple goal;
- simple jump/step traversal works;
- no fake client/player input layer exists;
- cancellation/removal leaves the worker idle and runtime disposed;
- movement GameTests pass consistently.

---

# Milestone 3 — Demonstrate native mining of one requested block

## Goal

A direct call to `getMineProcess().mine(targetBlock)` makes the worker autonomously discover, path to, face, and break one normal positive-hardness target block on a dedicated server.

No GUI work begins before this passes.

## Required code/modules

- real worker-specific `IPlayerController` behavior;
- progressive server-side block breaking;
- reach/raycast handling;
- tool selection, durability, and normal destruction/drop integration;
- cancellation/reset handling;
- isolated mining GameTest.

## Major integration replacement

The generic upstream dummy entity controller is not sufficient for ordinary survival block breaking, while the real player controller relies on `ServerPlayer` interaction management. The custom worker therefore needs one focused controller implementation that fulfills Automatone's expected break contract using server-side Minecraft/NeoForge APIs.

## Tasks — 4 total

### M3.1 — Implement worker break-state and targeting semantics

Build one small block-break state machine covering:

- idle/start/continue/complete;
- target change;
- abort/reset;
- out-of-reach/invalid target;
- worker removal.

Use the worker's actual eye position/rotation and a defined survival-like reach. Ensure Automatone's selected-block/raycast view agrees with the controller's target.

**Done when:** focused tests reliably identify reachable targets and reset correctly when target/reach changes.

### M3.2 — Implement real progressive destruction

In the same controller path:

- calculate destroy progress using 1.21.1 block/tool rules rather than fixed timers;
- honor unbreakable blocks;
- use the actually selected worker tool;
- preserve appropriate durability/usage;
- perform normal server-side block destruction and loot/drop behavior;
- preserve relevant NeoForge/vanilla hooks through the chosen destruction API.

Do not directly inject predicted loot into worker inventory.

**Done when:** stone/ore breaks progressively, appropriate tools are faster than an empty hand, bedrock remains unbreakable, and normal drops occur.

### M3.3 — Integrate cancellation with native Automatone mining

- Wire the worker break controller into the `IPlayerController` methods used by Automatone's break helper.
- Ensure `MineProcess.cancel()` clears held break/input state.
- Ensure path target changes do not leave progress on old blocks.
- Verify mid-break cancel leaves an unfinished block intact.

**Done when:** native click/break intent drives the controller and cancellation cannot finish a stale block afterward.

### M3.4 — Native one-block mining proof

GameTest:

- one worker;
- one traversable test chamber;
- one isolated normal-hardness target ore;
- no consumer-provided target coordinate.

Call only:

`worker.getBaritone().getMineProcess().mine(targetBlock)`

Assert that native Automatone finds the block, moves to it, reaches/faces it, and causes the worker controller to break it.

Review check: the consumer contains no custom A*, ore scanner, or target-position queue.

## Runnable acceptance test

```bash
./gradlew runGameTestServer
```

## Risks / unknowns

- exact 1.21.1 mining speed/durability APIs;
- block event/hook behavior for a non-player worker;
- raytrace disagreement between Automatone and controller;
- `MineProcess` scanner/cache behavior after the port;
- stale asynchronous path/scan publication after cancellation.

## Completion gate

- one direct native `mine(block)` call finds the target without consumer coordinates;
- worker walks to and faces/reaches it;
- normal positive-hardness progressive breaking succeeds;
- normal destruction/drop behavior occurs;
- mid-break cancel works;
- consumer has no second mining/search/pathing engine;
- mining GameTest passes consistently.

---

# Milestone 4 — Exact quantities and cancellation

## Goal

Request exactly N source blocks or unlimited mining while keeping discovery, pathing, movement, and mining inside native Automatone.

## Required code/modules

- small `MiningSession` product-state object;
- successful target-block break callback;
- finite/unlimited stop logic;
- one public Stop path;
- quantity/cancellation GameTests.

## Tasks — 4 total

### M4.1 — Add the minimal mining session and progress callback

Keep state small:

- target block registry ID;
- finite requested source-block amount or unlimited;
- matching source blocks successfully broken;
- state: `IDLE`, `RUNNING`, `COMPLETED`, `CANCELLED`, `FAILED`;
- last error placeholder.

Progress increments only after actual destruction of the requested target block. Do not count attempted hits, drops, pickups, or unrelated obstruction blocks.

**Done when:** target and obstruction breaks are distinguishable and only requested source blocks affect progress.

### M4.2 — Implement finite and unlimited execution

- Finite jobs start native `MineProcess` and stop at exactly N matching source-block destructions by calling native `cancel()` immediately on the Nth success.
- Unlimited uses the same native mining path without the finite check.
- Pre-existing inventory and multi-drop/Fortune outcomes do not alter source-block progress.
- Validate sensible finite amount bounds in consumer code.

**Done when:** five available targets with quantity 3 results in exactly three requested source blocks destroyed.

### M4.3 — Make Stop simple, synchronous, and idempotent

- Expose one product Stop method.
- Stop calls native `MineProcess.cancel()` and clears worker break/input state.
- Stop while idle and repeated Stop are harmless.
- For MVP, reject Start while already busy instead of implementing task replacement/queues.
- Stale async calculation cannot restart a cancelled task.

**Done when:** after Stop plus an observation window, no further requested target block is successfully broken.

### M4.4 — Quantity/cancellation GameTests

Cover:

- finite 1;
- finite 3 of 5;
- unlimited continues beyond 3;
- cancel after first successful break;
- cancel halfway through a slow block;
- obstruction blocks do not count;
- pre-existing matching inventory does not count;
- multi-drop/Fortune does not count as multiple source blocks.

## Runnable acceptance test

```bash
./gradlew runGameTestServer
```

## Risks / unknowns

- choosing the exact successful-destruction callback point;
- matching target blocks that are also broken as path obstructions;
- one-tick stale break input;
- stale async path/search results after cancellation.

## Completion gate

- finite N means exactly N requested source blocks;
- unlimited continues until Stop/native failure/exhaustion;
- Stop prevents future successful target breaks;
- progress is server-authoritative;
- one worker has at most one active session;
- quantity/cancel tests pass.

---

# Milestone 5 — Controller item, GUI, and server-authoritative networking

## Goal

A player can bind one controller to one worker, open one GUI, select a block and amount, Start, Stop, and see authoritative target/progress/status/error.

## Required code/modules

- `AutomatoneControllerItem`;
- binding data component;
- menu and client screen;
- searchable client-side block picker;
- Start/Stop custom payloads;
- authoritative status updates;
- compact server-side validation path.

## Major NeoForge integration APIs

- 1.21.1 `ItemStack` data components for controller binding;
- server-opened menu/container flow;
- client-only screen registration;
- NeoForge custom payload registration/handlers;
- server/main-thread world and worker mutation.

## Tasks — 5 total

### M5.1 — Controller item and binding

- Register the controller item.
- Store bound worker UUID in a versioned item data component; dimension may be included for lookup/diagnostics if useful.
- Bind by interacting with an owned worker.
- Perform binding writes and ownership validation on the server.
- Keep rebinding behavior explicit and simple.

**Done when:** one controller reliably identifies one bound worker during normal play/inventory synchronization.

### M5.2 — Minimal one-screen GUI and block picker

One screen only:

- worker identity;
- searchable block picker;
- quantity field;
- unlimited toggle;
- Start;
- Stop;
- current target;
- progress;
- status;
- last error.

Block search is client-side over the synchronized block registry, filtering translated name and registry ID. Send only the selected registry ID when Start is pressed.

Do not add tabs, inventory dashboards, path visualizers, maps, logs, or storage controls.

**Done when:** typing `diamond` can select a block and all MVP controls fit in one clear screen.

### M5.3 — Start/Stop payloads and centralized server validation

Keep payloads minimal and intent-based. Derive worker identity from server-owned controller/menu context instead of trusting a client-supplied worker UUID.

Start validation must check:

- correct open menu/context;
- controller still valid and bound;
- worker exists and is loaded;
- sender owns worker;
- block registry ID is valid/allowed;
- quantity is valid or unlimited;
- worker is not already busy.

Only then call the product session, which delegates to native `MineProcess`.

Stop performs the same identity/ownership/context checks, then calls the one Stop method.

**Done when:** invalid packets cannot mutate worker/session/native process state.

### M5.4 — Authoritative status/progress updates

Send a fresh state snapshot when meaningful state changes occur:

- menu open;
- task start;
- each successful target block;
- completion;
- cancellation;
- failure;
- worker becomes unavailable.

The client displays server state; it does not infer authoritative progress from animation or inventory.

**Done when:** GUI consistently displays current target, finite/unlimited amount, progress, state, and error from server-owned state.

### M5.5 — Networking tests and manual GUI acceptance

Automated validation tests reject:

- nonexistent/invalid block;
- negative/zero finite quantity;
- excessive quantity;
- stale menu context;
- unbound controller;
- unloaded/missing worker;
- non-owner;
- Start while busy;
- unauthorized Stop.

Manual dev flow:

1. spawn worker;
2. get controller;
3. bind;
4. open GUI;
5. search/select block;
6. request 2;
7. Start and observe `0 -> 1 -> 2`;
8. verify completion;
9. start unlimited;
10. Stop and verify mining/movement cease.

## Runnable acceptance test

```bash
./gradlew test
./gradlew runGameTestServer
```

Then execute the documented dedicated-server/client flow once.

## Risks / unknowns

- menu lifetime while controller item moves;
- worker unload while screen remains open;
- modded block registry/display handling;
- status ordering when completion/failure happens immediately;
- accidentally trusting client worker identity.

## Completion gate

- controller binds to one worker;
- one-screen searchable GUI works;
- finite/unlimited Start is server-authoritative;
- Stop is server-authoritative;
- target/progress/status/error are authoritative;
- packet tampering tests pass;
- client has no direct authority over worker state.

---

# Milestone 6 — Ownership, persistence, progress, and failure reporting

## Goal

Finish the MVP with safe save/reload behavior, enforced ownership, and meaningful failure reporting without adding offline operation or a persistent pathfinding/job engine.

## Required code/modules

- worker/controller/product persistence;
- ownership validation shared by interaction/network paths;
- restart/unload semantics;
- small typed native failure/termination API where needed;
- end-to-end security, save/reload, and failure tests.

## Tasks — 4 total

### M6.1 — Persist ownership, binding, and product state

Persist only understandable product state:

- worker identity and owner UUID;
- controller worker binding;
- target block ID;
- finite quantity/unlimited;
- successful source-block progress;
- session state;
- last useful failure code.

Do **not** persist A* paths, path nodes, ore target positions, scan generations, partial break progress, or active Automatone process internals.

Centralize ownership checks enough that binding, menu open, Start, and Stop use the same policy.

**Done when:** identity, owner, controller binding, configuration, and completed progress survive save/restart.

### M6.2 — Define simple restart and unload semantics

MVP rules:

- unloaded worker does no work because there is no chunk loading;
- a persisted `RUNNING` task reloads as `INTERRUPTED`;
- reconstruct a fresh transient Automatone runtime from current world state;
- never silently auto-resume after restart;
- user must press Start again;
- retain target/amount/progress so a manual restart can continue toward the requested total.

**Done when:** restart never causes unattended movement/mining, and no stale runtime/path state is restored.

### M6.3 — Add typed native failure reporting and user error mapping

Add only the smallest Automatone observability needed so the consumer never parses log text or duplicates `MineProcess` failure logic.

Initial native reasons should cover at least:

- `CANCELLED`;
- `NO_TARGETS`;
- `PATH_FAILED`;
- `BREAK_DISABLED` or equivalent;
- fallback `INTERNAL_FAILURE` only when necessary.

Consumer-level errors can cover:

- `WORKER_NOT_LOADED`;
- `NOT_OWNER`;
- `INVALID_BLOCK`;
- `INVALID_QUANTITY`;
- `WORKER_BUSY`;
- `INTERRUPTED`.

Keep machine-readable codes separate from display/localized text. Finite completion is `COMPLETED`, and explicit user Stop is `CANCELLED`, not `FAILED`.

**Done when:** expected native/product failures are distinguishable without parsing logs.

### M6.4 — Full MVP security, persistence, and failure matrix

Automated/end-to-end coverage:

- owner can bind/open/start/stop;
- non-owner cannot bind/control via UI or forged packet;
- ownership remains after restart;
- controller binding remains after restart;
- target/quantity/progress persist;
- running job reloads as `INTERRUPTED` and does not auto-resume;
- fresh transient runtime is created once after reload;
- no target -> `NO_TARGETS`;
- unreachable target -> deterministic path failure;
- break-disabled/unbreakable target -> meaningful failure;
- explicit Stop -> `CANCELLED`;
- finite amount -> `COMPLETED`;
- worker unload/removal -> interrupted/unavailable according to lifecycle policy;
- no excluded feature is needed to pass the matrix.

## Runnable acceptance test

```bash
./gradlew clean test
./gradlew runGameTestServer
./gradlew build
```

Then execute the dedicated-server/client MVP flow once on the release candidate.

## Risks / unknowns

- mapping all relevant native `MineProcess` termination paths;
- distinguishing unload from permanent removal;
- entity serialization/versioning details;
- stale async work publishing after runtime reconstruction;
- keeping GUI state synchronized with failure/restart transitions.

## Completion gate — MVP complete

The MVP is complete when a player can:

1. own one worker;
2. bind one controller;
3. open one GUI;
4. select one block;
5. request N source blocks or unlimited;
6. Start;
7. watch server-authoritative progress;
8. Stop;
9. receive a clear failure/status when something goes wrong;
10. save/restart without losing identity, ownership, binding, configuration, or progress;
11. observe that interrupted work never silently resumes.

No storage automation, chunk loading, work zones, dashboards, multi-worker dispatch, or scanner enhancements are part of this gate.

---

# Engineering rules

Keep these simple rules throughout the project:

1. **One task = one coherent reason to change code.** Do not create extra architectural layers just to make task boundaries cleaner.
2. **Tests belong to the feature task.** Do not create separate testing architecture unless genuinely required.
3. **Server owns world mutation and authorization.** Background calculations may use safe data, but they do not mutate world/product state directly.
4. **Fix native Automatone problems in Automatone.** Do not hide broken cancellation or missing process failure information behind a large consumer adapter.
5. **Keep the consumer thin.** Worker adaptation, product state, GUI, and authorization are valid; pathfinding/scanning/mining reimplementation is not.
6. **Do not add excluded features to solve hypothetical future problems.** Pass the current milestone first.

# Recommended implementation order

1. `M1.1` source baseline.
2. `M1.2` NeoForge build + mechanical port.
3. `M1.3` remove Fabric/Quilt/CCA lifecycle coupling.
4. `M1.4` mixin audit + restore native `MineProcess`.
5. `M1.5` server smoke tests.
6. `M2.1-M2.4` minimal worker and movement proof.
7. `M3.1-M3.4` real worker block breaking and one-block native mining proof.
8. `M4.1-M4.4` exact quantities/unlimited/cancellation.
9. `M5.1-M5.5` controller, one-screen GUI, networking, validation.
10. `M6.1-M6.4` persistence, failure reporting, final MVP acceptance.

Do not start the GUI until Milestone 3 passes.

# Smallest first implementation slice

The first implementation slice contains no worker GUI and no product mining logic:

```text
NeoForge 1.21.1 + Java 21 Automatone
  -> dedicated server boots
  -> real runtime constructs
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

The project does not proceed to controller UI/network product work until that target ore is reliably found and broken by native Automatone on the dedicated server.
