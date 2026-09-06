# M3 — Native mining with player-like presentation

Approved by the human for implementation on 2026-09-06 in the M3 task.
This supplements the master plan's four M3 tasks. The explicit renderer and
non-player-hook decisions supersede older server-only worker test assumptions.

## Goal and baseline

Start from M2 acceptance checkout `bd7877ca`, retaining accepted prerequisite
`30561e6bad934087848cc252796b352c0630444d`. Deliver one worker that discovers,
approaches, faces and progressively mines one isolated ore through
`worker.runtime().getMineProcess().mine(targetBlock)`.

Watching the worker must show a player-like humanoid, its held tool, arm swings,
progressive block cracks, mining sounds and break effects. Bundle the classic
four-pixel-arm skin from
<https://www.minecraftskins.com/skin/23749988/the-stevenator--edited-/>, retaining
attribution; use Minecraft's player model and outer skin layers.

No GUI, quantity/session logic, persistence, storage, custom scanner, pathfinder,
target queue, movement engine, fake player or replacement mining process.

## Sequential tasks

### M3.1 — Targeting and break state

- Extend the existing WorkerEntityController and retain existing public native
  interfaces. One transient state holds target/state, selected tool snapshot,
  progress and completion; advance at most once per server tick.
- Fixed 4.5-block reach shared with native raycasting. Actual eye/rotation raycast
  must hit the requested block. Reset on target/tool change, invalid/replaced
  block, lost reach/alignment, abort and removal; clear any crack overlay.
- The helper's per-tick `setHittingBlock(false)` is not an abort.
- Focused unit transitions and server targeting tests complete this task;
  actual physical progress/destruction is supplied by M3.2.

### M3.2 — Destruction and presentation

- Actual world-position hardness, selected-stack speed/harvest suitability and
  applicable vanilla mining modifiers determine progress. Honor negative
  hardness; no fixed timers or path-cost estimates as authoritative progress.
- Keep native tool selection. On successful removal without automatic empty-tool
  drops, call tool-aware Block.dropResources with the captured block entity,
  worker and pre-damage stack. Call the item's LivingEntity-capable mineBlock
  behavior for usage/durability. Never predict or inject inventory loot.
- Preserve applicable destruction, neighbor, loot/drop and experience behavior.
  The human explicitly accepts non-player hooks: player-only break/protection
  hooks are excluded; do not introduce a fake-player bridge.
- Use native swings and vanilla tracking; broadcast changed crack stages only,
  reset with -1, play hit sounds at vanilla cadence and normal final effects.
- Add a side-restricted client renderer using PlayerModel<WorkerEntity>, classic
  arms, skin layers and held items; use vanilla equipment synchronization.
- Correct the old blanket client-dependency test for the approved renderer:
  common/server code must not reference client code and registration must be
  client-only. Retain legacy-loader, native ownership and no-player constraints.

### M3.3 — Cancellation

- Repair native cancellation in Automatone. Native mine cancellation clears
  intent, progress, cracks and held input synchronously, including when the
  helper's previous-hitting flag is false.
- Releasing left-click or losing a valid raycast aborts stale breaking. Existing
  scan-generation/path cancellation guards prevent stale work from restarting.
- Retarget, repeated cancel and removal are safe. A mid-break cancel leaves the
  unfinished block intact through an observation window.

### M3.4 — Native proof and visual acceptance

- One grounded worker, iron pickaxe and iron ore beyond initial reach in an
  isolated traversable chamber. Start with the direct native call only; fixture
  coordinates must not enter discovery/pathing inputs.
- Observe native movement, facing/reach, intermediate progress, real destruction,
  raw-iron drops and tool wear.
- Add a local client run configuration and reuse the chamber for one visual
  observation of skin layers, held tool, swings, cracks, sounds, final effects
  and cancellation cleanup. No runtime asset download or new dependency.

## Execution and acceptance

Astra implements/integrates. One reused Terra test author (Luna fallback) owns all
tests and fixtures. One separate fresh Terra verifier grades the clean final
candidate without repairs. Helpers do not redelegate or approve their own work.
Use installed Graphify, Ponytail and Old Coder under repository proportional
verification rules. Update the existing graph after meaningful code changes.

Focused cases: start/continue/complete; tick deduplication; retarget/tool change;
invalidation/abort/removal; reach boundary/occlusion/facing; progressive stone/ore;
appropriate tool faster than hand; bedrock; tool drops/wear/breakage/drop events;
mid-break cancel and fresh restart; existing asynchronous cancellation regressions;
automatic swing/crack observations plus one actual client visual/audio check.

Compile affected code and run selected tests during each task. Batch related
GameTests per behavior slice; repeat passing checks only after invalidating
changes. Keep one concise M3 evidence record and mark broader obligations PENDING.

After M3.4, freeze a clean candidate. The independent verifier runs the deduplicated
default, architecture_sensitive and runtime_minecraft profile union once, root
and worker. Mining consistency requires three fresh worker-server runs including
the gate; repeat runtime checks, not the static union. Add dependency measurement
only for dependency changes. Preserve exact analyzer thresholds and dispositions.

Tasks complete after focused checks and controller review. M3 accepts only after
the independent gate and visual observation pass. If interactive access is
unavailable, finish automated work and mark that sole observation UNVERIFIED.
Routine scoped repairs need no further approval; architecture changes or waivers
remain human decisions. Stop after M3; do not begin M4.
