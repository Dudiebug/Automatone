# Milestone 2 implementation plan

Approved by the user's instruction to implement the proposed plan on 2026-09-04.
This supplements `NEOFORGE_1.21.1_SERVER_WORKER_MILESTONES.md`; it does not waive
`EXECUTION_STRATEGY.md`, sensor policy, or baseline acceptance requirements.

## Entry gate and order

Planning checkout: `plan/neoforge-1.21.1-server-worker` at
`e81dbad7ab53647920c61d824fb927f15ef67a89`, clean before this plan was persisted.
The workflow still records `accepted_baseline: null`. Historical cleanup evidence
reports 84 retained SpotBugs findings and unverified Q2/Q4 coverage. Reconcile
those records with a fresh clean-commit verification before M2.1 becomes READY.
A committed candidate is not automatically an accepted baseline.

Complete focused acceptance for **M2.1 -> M2.2 -> M2.3 -> M2.4**, then independently accept M2 once at its milestone gate. Keep the four
existing task IDs. Do not advance past a failed or unverified dependency.

Graphify preflight used the existing 3,353-node graph and checked relevant
relationships against current source. Its older commit stamp is advisory.
Existing owners are `BaritoneProvider`, `IPlayerContext`, `IPlayerController`,
`InventoryBehavior`, `InputOverrideHandler`, `LookBehavior`, and native pathing.
No graph rebuild was performed during planning.

## Shared design and paths

- Create a separate `worker` Gradle subproject with a normal dependency on the
  existing root Automatone library. Produce two artifacts; do not shade, copy,
  or embed Automatone in the worker artifact.
- Use mod ID `automatone_worker`, entity ID `automatone_worker:worker`, and
  package `automatone.worker`.
- Production files live in `worker/src/main/java/automatone/worker/`; dedicated
  GameTests live in `worker/src/gameTest/java/automatone/worker/gametest/`.
  Unit and architecture tests use the corresponding `test` and `sensorTest`
  source sets. Mod metadata and structures use their source-set resource paths.
- Retain Java 21, NeoForge 21.1.249, and ModDevGradle 2.0.144. Keep pinned
  dependency versions and the existing library artifact intact.
- Reuse `IPlayerContext` despite its legacy name. No public Automatone API
  signature changes or new wire protocol are planned.
- All adaptation, inventory changes, runtime attachment, and movement occur on
  the server. No fake player/client, packet input, global mob mixin, second
  pathfinder/scanner/target queue, or consumer mining algorithm.
- No placement UX, GUI, product authorization model, source-block accounting,
  storage automation, custom inventory persistence, chunk tickets, or retries.

## M2.1 - Minimal worker and inventory host

Add `WorkerMod` and `WorkerEntity extends Mob`. Register the normal living/mob
attributes with health 20, movement speed 0.1, dimensions 0.6 by 1.8, and vanilla
step height 0.6. Register no goals, targets, natural spawning, wandering, combat,
following, or loot collection. Use the existing persistence-required mob flag
to prevent natural distance despawning; do not add a persistence subsystem.

Use one `SimpleContainer(9)` and selected slot 0 through 8, initially 0. Inventory
is authoritative for the held stack. Selection, selected-slot replacement,
removal, and later controller swaps must keep the main hand consistent. Expose
simple worker methods for M2.2's context; do not add an inventory abstraction.
Provide a registered-entity GameTest spawn helper. Do not attach a runtime yet.

Affected areas: root `settings.gradle`, `build.gradle`, `gradle/verification.gradle`;
new worker build/metadata, `WorkerMod.java`, `WorkerEntity.java`, host unit tests,
architecture tests, and `WorkerHostGameTest.java`.

Acceptance mapping:

- AC-1: Spawn and remove the registered entity on the dedicated server; assert
  its attributes and dimensions.
- AC-2: Exercise slots 0 and 8, reject invalid indices without mutation, and
  observe hand changes after selection, replacement, and removal.
- AC-3: Architecture/source checks prohibit product AI and duplicate native
  responsibilities.
- AC-4: Reuse the spawn helper to create the actual registered entity repeatedly.

Extend sensors to include worker code and compare duplication across library
and worker sources, not only within each project. Preserve thresholds. Select
the dependency-change profile in addition to the three shared M2 profiles.

Stop for registration/loading failures, inconsistent inventory/hand ownership,
or missing worker sensor coverage. Stop after host acceptance; lifecycle is M2.2.

## M2.2 - Explicit transient runtime

Add one stable `WorkerContext implements IPlayerContext` and one
`WorkerEntityController implements IPlayerController` per worker. Attach through
`BaritoneAPI.getProvider().createBaritone(context, directory)` when the entity is
added to a server level. Keep the returned runtime on the worker. Repeated
attachment must reuse the same context identity and runtime.

Use a save-local native cache directory `automatone/<worker UUID>`. Resolve
`worldData()` from the held runtime's world provider, returning null before
attachment; getters must never construct or recursively look up a runtime.
Use the existing server ray-trace utility for `objectMouseOver()`.

The existing `Automatone` server post-tick provider callback remains the only
runtime ticker. Do not tick from the entity. On removal/unload, destroy through
the provider and clear the worker reference. Cleanup is idempotent. A reloaded
entity receives a fresh context/controller/runtime and starts idle.

The controller shell synchronizes the held stack and swaps validated slots only
in the worker's own container. It reports survival mode. Breaking and use/place
operations report unsuccessful results without mutating the world; reset
operations are safe. Implementing block breaking remains M3.

Affected areas: `WorkerEntity.java`, new `WorkerContext.java`,
`WorkerEntityController.java`, controller tests, and `WorkerRuntimeGameTest.java`.
Existing provider/runtime code is an integration reference, not a redesign target.

Acceptance mapping:

- AC-1/2: Repeated attachment yields one provider entry with the exact worker,
  context, and controller identities and explicit wiring.
- AC-3: Existing event listeners observe exactly 20 PRE/IN and 20 POST runtime
  events over 20 actual server ticks.
- AC-4: Removal and unload dispose/unregister; repeated cleanup is harmless;
  disposed runtimes emit no further events.
- AC-5: Entity save/remove/load creates a different idle runtime with no active
  goal, path, or process. This tests transient lifecycle, not product persistence.
- Controller checks reject foreign containers and invalid slots without mutation;
  unsupported interactions leave the world unchanged.

Duplicate identities/ticks, recursive world lookup, unload leaks, or auto-resume
block acceptance. Stop after lifecycle acceptance; locomotion is M2.3.

## M2.3 - Direct server movement

Consume native decisions from server post-tick during the next entity tick.
Reuse existing input fields and server look updates. Replace only this worker's
MoveControl, LookControl, and JumpControl tick implementations with inert local
implementations; keep goal selectors empty and stop navigation before its AI
phase. `Mob.serverAiStep()` is final in the pinned source and cannot be overridden.

Preserve vanilla travel, collision, gravity, and jumping. Obtain speed from the
movement-speed attribute without `Mob.setSpeed()` overwriting forward input.
When directional input clears, remove residual horizontal motion while grounded;
preserve vertical jump/fall physics. Use native
`getPathingBehavior().cancelEverything()` for navigation cancellation. Tests must
not clear inputs manually to hide a cancellation failure.

Affected areas: `WorkerEntity.java` with its local controls and
`WorkerMovementGameTest.java`. Native input/look/pathing managers retain their
responsibilities; report a demonstrated native defect before broadening a patch.

Acceptance mapping:

- AC-1: Observe bounded forward/sideways displacement, jumping, and yaw/pitch
  changes through native input/look APIs.
- AC-2: Queue conflicting vanilla move/look/jump/navigation requests; they must
  not overwrite native input or produce jitter.
- AC-3: Architecture checks find no global mixin, fake player/client, packet
  control, or consumer movement algorithm.
- AC-4: Idle means stationary. Grounded cancellation clears directional/jump
  input and horizontal motion by the next entity tick, remaining stable for 20 ticks.

Stop for an unresolvable tick/control conflict or a need for custom physics,
global mixins, or client authority. Accept direct control before M2.4.

## M2.4 - Native navigation GameTests

Add `WorkerNavigationGameTest.java` and a reusable small corridor structure.
Start exclusively with `getCustomGoalProcess().setGoalAndPath(new GoalBlock(...))`.
Fixture construction and initial positioning precede start. After start, tests
only observe and invoke native cancellation/removal; no teleports, supplied
routes, scripted input sequences, or test-only movement implementation.

Each scenario uses a distinct sequential batch. Run worker tests in their own
server process because existing M1 fixtures alter global settings and dispose
all provider runtimes. Restore changed settings and clean up only each fixture's
own worker/runtime on success and failure.

- AC-1: Reach a flat goal six blocks away within 400 ticks; observe native path
  execution and goal completion.
- AC-2: Traverse a one-block rise in a corridor preventing bypass and reach the
  raised goal within 400 ticks, leaving terrain unchanged.
- AC-3: Cancel after measurable movement before arrival. Assert cleared input
  and at most 0.01 block horizontal drift over the subsequent grounded 20-tick window.
- AC-4: Remove after movement; assert no provider entry, disposed runtime, and
  no subsequent runtime events.
- AC-5: Architecture/source evidence proves native route ownership.

Require three consecutive fresh server runs, retaining failures rather than
hiding them with retries. Async publication, fixture interference, obstacle
bypass, or hitbox failures return to their owning task. Stop at M2 acceptance;
mining is M3.

## Verification and stopping rules

The human-approved proportional policy in AGENTS.md and EXECUTION_STRATEGY.md
supersedes per-task full profiles and repeated independent reviews. During M2.1
through M2.4, run focused behavior checks and affected compilation. Complete a
task after focused acceptance, then allow the next task. Keep wider checks PENDING.

At M2 completion, one fresh independent clean-candidate verification supplies the
union of default, architecture_sensitive, runtime_minecraft and dependency_change
requirements. Check both library and worker artifacts and their boundary. Build,
dependency, shared host/concurrency or server/client changes may justify earlier
checks relevant to that risk; do not automatically run every layer after edits.
Use the configured dependency checks where applicable, not unrelated sensorAll
observability requirements. Keep all product acceptance criteria and thresholds.

Parent implements and repairs; Luna only performs small Old Coder test assignments.
Reuse relevant passing evidence, repeating checks only when changes could invalidate
them. Do not require mutation, coverage targets or multiple independent rounds.
Record changes, actual checks/results and deferred milestone obligations concisely.
The measurement runner supports explicit focused selection and milestone profiles;
it does not start product work or grant acceptance. M2 remains behind accepted
QUALITY-CLEANUP, and this workflow update does not authorize product implementation.
