# M5 implementation evidence

Authority: human approved docs/M5_GUI_CONTROLLER.md and requested implementation.
Final correction: all newly created workers start with empty inventory. No starter
equipment. Baseline: accepted M4; existing human workflow/document edits preserved.

## Current status

M5.1-M5.7 COMPLETE for automated task criteria. Human visual/client checks PENDING. M5.8 independent gate IN_PROGRESS. Milestone acceptance PENDING.
Skills: Graphify structural query, Ponytail native/reuse-first implementation,
Old Coder failure model under the repository proportional verification policy.
Product spec approved; Astra owns detailed test contracts. No extra dependency,
mutation or coverage target is assumed. Final independent gate remains PENDING.

## Preflight

- Native MineProcess already accepts multiple blocks; product session stores one.
- Native settings currently resolve globally across approximately 50 source files.
  Runtime/async isolation must precede personal configuration UI.
- Existing worker has nine empty inventory slots, ownership, progressive breaking,
  exact quantities, persistent sessions and nine/one chunk ticket lifecycle.
- GUI/controller/roster/RTP/archive/notification implementation does not yet exist.
- Existing graph queried for worker/entity/mining/session/settings; graph is advisory.

## Checks

- Runtime settings now belong to Baritone instances, with explicit apply/cancel
  and captured CalculationContext values propagated to tools, block-state reads,
  heuristics, path cutoff, mining scans and movement. Runtime-owned cache chains
  use suppliers so applied settings reach existing worlds. Legacy context/API
  overloads retain default behavior. Worker jobs own immutable multi-target lists,
  PAUSED state and persisted run identity; source destruction remains native.

- Initial multi-target WorkerEntity/MiningSession compilation: `gradlew.bat
  :worker:compileJava --console=plain` PASS before runtime settings integration.
  Existing WorkerChunkLoading/WorkerEntityController warnings were unchanged.
- Luna-authored `MiningSessionMultiTargetTest`: `gradlew.bat :worker:test --tests
  automatone.worker.MiningSessionMultiTargetTest` PASS (11 tests); Astra reviewed
  contract assertions, defensive copies, invalid-state preservation and run identity.
  Subsequent restore-validation changes need the affected unit checks rerun.
- Integrated compilation and affected unit checks: `gradlew.bat :worker:test
  --tests automatone.worker.MiningSessionTest --tests
  automatone.worker.MiningSessionMultiTargetTest :worker:compileGameTestJava
  --console=plain` PASS: 6 existing + 13 multi-target unit tests. Corrected one
  test assertion comparing a Setting wrapper instead of its value before runtime.
- `gradlew.bat -PworkerGameTestNamespaces=automatone_worker_m5_gametest
  :worker:runGameTestServer --console=plain` PASS: all four required tests. Real
  mixed source destruction stops at 3 of 4; paused work stays stopped for 20 ticks;
  resume and running-settings replan retain progress/run identity; paused v2 save
  reload and v1 single-target migration stay inactive until explicit resume;
  two worker/calculation settings snapshots, nested mutable collections and delayed
  path-node heuristic evaluation remain isolated. Log:
  `.agents/evidence/M5-foundation-runtime.log`.
- Root unit check initially FAILED (35 of 77): the legacy PathNode constructor
  eagerly initialized Minecraft through settings, breaking standalone custom-goal
  use. Repaired the constructor to preserve legacy heuristic dispatch; explicit
  runtime construction still supplies the calculation settings. Existing tests
  unchanged. `gradlew.bat :test --console=plain` PASS: all 77. Worker units from
  the integrated `test` invocation also PASS: 22. Logs:
  `.agents/evidence/M5-native-units.log` and `M5-native-units-repair.log`.
- Affected native runtime check initially FAILED (27/28 PASS): the reflection-based
  cancellation race fixture expected the old synthetic lambda signature. `javap`
  confirmed the added CalculationContext capture; updated fixture invocation and
  preserved cancellation/publication assertions. Rerun all 28 native GameTests
  PASS. Worker runtime ran 53 cases: 52 PASS, with one fixture collision repaired
  as described below. Logs:
  `.agents/evidence/M5-affected-runtime.log` and `M5-affected-runtime-repair.log`.
- Legacy navigation/tool/cache fixtures now configure owned settings. Cache prune
  concurrency stimulus remains enabled explicitly; no behavioral assertions removed.
- Worker boundary test failure was neighboring structure cleanup: its move from
  origin X=11807043 to the next chunk's center X=11807064.5 entered the next
  template's cleanup bounds (the 17-wide templates have expanded clearing bounds).
  Pinned Minecraft StructureUtils.clearSpaceForStructure explicitly discards all
  non-player entities inside those bounds. Disproven assumption: sibling setup
  could not reach this moving fixture. Gave this test its own batch and asserted
  the worker remains live; retained all center/ring-diff checks. Affected rerun:
  `gradlew.bat -PworkerGameTestNamespaces=automatone_worker_m4_gametest
  :worker:runGameTestServer --console=plain` PASS: all 15 required cases.
  Log `.agents/evidence/M5-ticket-fixture-repair.log`. Other 52 runtime case results
  remain applicable: only this fixture batch/guard changed. All four M5 foundation
  tests passed again in that 53-case run after native cache/helper integration,
  including the additional paused-profile-apply assertion.
- M5.1 controller confirms no duplicate scanner/pathfinder/miner, no client server
  authority inversion and no granted inventory. Focused criteria PASS. Static,
  architecture and fresh independent complete milestone checks remain PENDING;
  no identical preliminary milestone profile was run.
- Milestone gate PENDING; use actual configured default/architecture_sensitive/
  runtime_minecraft profiles. Network authorization is checked by explicit worker
  tests included in integration; profiles.json has no named network_security profile.
  This mapping does not waive network checks or add a policy/profile exception.

## M5.2 — roster, profiles and retirement (COMPLETE)

- Added overworld SavedData roster with owner isolation, ten active/pending slots,
  server-thread reservations, repeated/stale request rejection, profile and worker
  revisions, native default/personal/override composition and reset-by-removal.
- Retirement pauses native work, snapshots entity identity/job/selected slot and
  inventory, clears the removed entity's inventory, then discards it through the
  existing runtime/ticket lifecycle. Archives expose copied items and revisioned
  withdrawal only. Reactivation restores the same UUID and remaining stacks;
  unfinished jobs stay paused and new workers have nine empty slots.
- Owned legacy entities are adopted on load/claim. Existing legacy workers above
  the new cap are retained; further reservations are denied until capacity exists.
  Versioned roster saves preserve profiles/overrides/archive. Pending destination
  searches are transient and do not consume slots after restart.
- Native setting parsing uses SettingsUtil on isolated copies, with strict boolean,
  registry, finite-number and bounded-cost validation. Editable names are scoped
  to actual server mining/path/movement/cache consumers; fixed reach, client and
  other-process/integration settings have unavailable reasons. Empty lists retain
  native empty-list semantics without parsing an empty registry identifier.
- Structural Graphify query PASS (advisory, stale line numbers; source inspected).
  `gradlew.bat :worker:compileJava --console=plain` PASS, three existing warnings.
  Dedicated-server roster tests are PENDING. Milestone sensors and fresh independent
  clean-candidate verification remain PENDING; this task is not COMPLETE yet.
- Focused final runtime PASS: all four required roster GameTests; log
  `.agents/evidence/M5-roster-runtime.log` (Gradle exit 0). Tests cover actual
  server-thread rejection, active+pending cap, ownership/replay, copied profiles,
  inheritance/reset, strict/stale validation, active roster save/load, retirement
  of a just-started running job, removed runtime/cleared inventory/released tickets,
  archive copy isolation/exact withdrawal/stale repeat, pending reactivation guards,
  archive save/load, and same-UUID paused reactivation with only remaining stacks.
- Parent reviewed Luna assertions; strengthened the genuine v1 fixture to omit
  both v2 job fields and assert retained entity UUID/owner and migrated target.
  Final affected four-test rerun PASS. Earlier helper run had one expectation
  mismatch after numeric validation consolidated NaN into SETTING_OUT_OF_RANGE;
  corrected expected error without changing the required rejection behavior.
- M5.1 runtime isolation, progress-preserving replan and v1/v2 job persistence
  evidence reused; those implementations are unchanged. Controller confirms M5.2
  focused criteria COMPLETE and architecture intact. Milestone gate still PENDING.

## M5.3 — safe deployment and relocation (COMPLETE)

- Added bounded service: at most four concurrent searches, 32 sampled columns,
  600 ticks overall and 200 ticks per preparation. Samples cover the selected
  Overworld/Nether border; floors require support, clear worker collision volume,
  no adjacent liquid/fire/damaging/portal blocks and valid build height. Nether
  searches stay below its roof. Invalid dimensions and stale/foreign requests reject.
- Pinned ServerChunkCache source disproved the assumption that getChunkFuture is
  nonblocking on the server thread: it invokes managedBlock there. The service
  instead adds one temporary vanilla region ticket (center and full neighbors)
  and polls getChunkNow on later ticks. It never joins a terrain future.
- Cancellation, bounded failure and shutdown release preparation tickets and
  creation/reactivation reservations. Relocation pauses before preparation;
  successful moves reset motion and immediately update existing M4 tickets.
- Pinned Entity.changeDimension removes its source before addDuringTeleport,
  which does not return destination admission failure. The worker adapter now
  keeps a paused cross-world source until addFreshEntity accepts the destination,
  closes its runtime before the copy attaches to the same identity cache, and
  restores source runtime/roster on rejection. Same-world moves retain vanilla
  transfer. Occupied/attached workers reject without deleting riders or leashes.
- Focused compile PASS. Initial overloaded event-listener method-reference compile
  failure repaired by naming the static event entry point onServerTick. Runtime
  checks and milestone-wide sensors remain PENDING; task not COMPLETE yet.
- Real NeoForge destination EntityJoinLevelEvent rejection regression PASS: the
  original paused worker retains its entity/owner/position/inventory/run, restored
  runtime and sole source anchor; no destination entity/ticket. Log
  `.agents/evidence/M5-relocation-rejection-repair.log` (one test, Gradle exit 0).
- Disproven fixture assumption: GameTests cannot share the production Java package
  because they load as a separate NeoForge module (split-package ResolutionException,
  `.agents/evidence/M5-relocation-rejection.log`). Moved tests to the established
  gametest package and exposed the bounded server-side service seams; no module
  flags or runtime ownership changes. Main service tests still PENDING.
- Cap-edge follow-up from M5.2 review: grandfathering loaded legacy workers could
  violate the approved ten-slot limit while slots were reserved. Superseded that
  provisional migration choice: legacy overflow now retires through the same
  preserving archive path. Five focused roster tests PASS including reserved-slot
  overflow retaining UUID/inventory; log `.agents/evidence/M5-roster-cap-repair.log`.
  This repairs the cap contract without granting equipment or dropping saved data.
- Final focused relocation run PASS: all seven required tests, Gradle exit 0;
  `.agents/evidence/M5-relocation-runtime-final.log`. Includes real default service
  event dispatch, asynchronous admission/cancel, cap/replay, empty deployment,
  archived reactivation, paused-original cancellation, raw preparation-ticket
  cleanup after timeout/exhaustion, 128 seeded samples across a 20,000-block border
  reaching both sides of each axis, hazard/collision/build checks, successful
  Nether transfer, and destination-join rejection preserving the original worker.
- Parent reviewed Luna fixtures and repaired absolute floor-height/all-air-column
  assumptions, native fire support, migrated-entity/pending-request cleanup and
  border-range assertions. Prior failures were fixture defects; final tests retain
  the required outcomes. Removed an unnecessary test warning suppression and
  retained the direct reference-identity assertion. No analyzer rules were waived.
- Controller confirms M5.3 focused criteria COMPLETE. All path/mine/movement
  algorithms remain native. Broader static/architecture and fresh independent
  clean-candidate milestone verification remain PENDING.

## M5.4 — Controller item (COMPLETE; human visual check deferred)

- Drew the production controller directly in the Piskel browser editor with pen,
  rectangle and fill tools. Saved editable `docs/art/controller/controller.piskel`
  and its 1x texture export. Nine colors, iron casing, green worker display/button,
  copper antenna; no ImageGen production asset. Standard generated item model and
  English localization; reusable non-stackable item in Tools & Utilities.
- Shipped recipe: four iron ingots, one copper ingot, glass pane and redstone,
  producing one controller. No binding, durability, energy or equipment grants.
  Opening the holder's roster is the dependent M5.5 menu integration.
- PASS: `:worker:compileJava :worker:processResources` (Gradle exit 0), log
  `.agents/evidence/M5-controller-compile.log`. Existing chunk-loading warnings
  were not suppressed. PASS: texture is 16x16 with 154 fully transparent pixels
  and nine opaque colors; every pixel matches the PNG embedded in editable source,
  `.agents/evidence/M5-controller-assets.log`. Visually inspected in Piskel.
- PASS: one real dedicated-server GameTest, Gradle exit 0, log
  `.agents/evidence/M5-controller-runtime.log`. The loaded recipe matches the exact
  approved ingredients, assembles the registered default controller with stack
  limit one/no durability/no binding components, and rejects each missing ingredient.
- BLOCKED/UNVERIFIED: actual inventory/held-item observation. Launched
  `:worker:runM3Client`; NeoForge reported `Failed to locate a primary monitor`
  (`glfwGetPrimaryMonitor failed`). Desktop capture also failed for that client
  monitor. Stopped only the identified failed client process; launch exits nonzero.
  Raw log: `.agents/evidence/M5-controller-client.log`. No rendering PASS claimed.
- Requested restoration of the display or explicit deferral of this task's visual
  check to the final M5 gate. M5.4 is not COMPLETE and dependent M5.5 implementation
  has not started. Broad sensors and independent milestone acceptance remain PENDING.
- Human resolved the blocker: "i will be your in Minecraft game tester. you don't
  have a GPU in this session". Controller accepts M5.4 automated criteria and advances
  to M5.5 under that explicit testing split. Actual inventory/hand observation stays
  PENDING for the human's final M5 checklist, not PASS. Do not retry local GPU clients;
  continue compilation, dedicated-server GameTests and required deterministic sensors.

## M5.5 — Server menus and networking (IN_PROGRESS)

- Added holder-scoped native menus, real nine-slot inventory binding, controller use,
  bounded intent/snapshot codecs, monotonic session request sequencing, owner and
  revision validation, relocation status and all current job/profile/archive actions.
  Selection opens a fresh native menu id; server-owned batch previews retain the
  exact job, recipients/revisions/run identities and start choice before confirmation.
- Native 1.21.1 source disproves the assumption that vanilla rejects stale inventory
  state IDs: ServerGamePacketListenerImpl computes staleness but calls clicked before
  resynchronizing. One required worker-only mixin intercepts before remote-update
  suppression. Native Slot guards additionally reject worker cloning and explicitly
  block player-to-archive quick-move, whose merge path does not check mayPlace.
- Archive menus share the authoritative SimpleContainer and persist actual changes;
  no-op setChanged does not revise the archive. Existing explicit withdrawal uses the
  same backing. Successful reactivation invalidates that backing; rejected admission
  preserves it. Item synchronization remains Minecraft-owned.
- Demonstrated spec/test mismatch: M2's WorkerArchitectureRulesTest banned every
  consumer ServerPlayer reference. Approved docs/M5_GUI_CONTROLLER.md requires real
  holder menus and networking, and pinned NeoForge openMenu/send-to-player APIs
  require ServerPlayer. Corrected only that dependency predicate to permit exactly
  WorkerMenu and WorkerPacketListenerMixin. All other classes, including WorkerEntity,
  WorkerContext and WorkerEntityController, remain banned; unconditional FakePlayer/
  Factory bans remain, plus an unconditional no-ServerPlayer-subclass rule. This is
  the approved product boundary, not a worker impersonation waiver or relaxed analyzer.
- Initial compile exposed the pinned readNbt(accounter) return type (Tag, not
  CompoundTag). Repaired with an explicit compound root check; focused compile PASS
  (`.agents/evidence/M5-menu-compile-repair.log`). Luna's real menu/packet/slot negative
  and conservation tests are in progress under the task's Astra-authored foundation.
  Focused runtime/architecture outcomes and milestone-wide sensors remain PENDING.
- Focused server/client and real-player boundary sensor methods PASS, Gradle exit 0:
  `.agents/evidence/M5-menu-boundary.log`. No complete milestone profile was run.
  Native source confirms registered payloads use negotiated GenericPacketSplitter;
  vanilla discarded-payload size limits do not cap registered codecs. Retained bounded
  NBT quotas of 4 MiB for intents and 8 MiB for snapshots, sufficient for existing
  supported 8192-character settings values without a custom fragmentation protocol.
  Shape/field/registry/count limits still apply before product mutation.
- Existing five roster GameTests PASS after sharing the native archive backing:
  `.agents/evidence/M5-menu-archive-regression.log`, Gradle exit 0.
- Controller review found an additional cross-world slot hazard: relocation replaces
  the worker entity and inventory object while retaining UUID. Menus now require the
  same authoritative inventory object before accepting clicks and reopen under a
  fresh native menu id after replacement or reactivation. A UUID-only binding would
  expose an obsolete source inventory copy. The focused menu tests include this case.
- Controller reviewed and accepted the corrected seven real menu GameTests: focused
  namespace automatone_worker_m5_menu_gametest PASS (all 7 required tests, 2026-09-06
  10:17:43 local), with successful compileGameTestJava. Retained actual server log:
  .agents/evidence/M5-menu-runtime-server.log. Tests exercise real packet handling,
  positive current-click control, owner/session/revision/codec negatives, matching
  archive merge and creative clone rejection, pending cancellation/resume, settings
  isolation and single-use batch confirmation. Fixture cleanup closes menus and
  removes the real test players even when absent from PlayerList. Diff check PASS.
  M5.5 COMPLETE; human visual checks and independent milestone profile remain PENDING.

## M5.6 — Native client screens (automated COMPLETE; human checks PENDING)

- Added a client-only registered AbstractContainerScreen using the concept's square
  charcoal/iron frame, copper corners and green selection accents. Native Slot
  geometry/hit-testing remains fixed inside a centered 176px inventory canvas.
  The roster uses up to five columns and paged wheel scrolling at compact scales;
  registered blocks search translated names/IDs with namespace filtering, selected
  chips, quantity/unlimited, and native item tooltips. Includes active/archive
  inventory, tool selection, job/batch actions, name/location, settings inheritance,
  deployment/relocation/reactivation/retirement and unsaved-edit dialogs.
- Settings panel provides typed controls, native parsing/range feedback, search,
  categories, override/reset/reset-all and unavailable reasons. 248 descriptions
  were extracted from the pinned native Settings.java Javadocs. Client drafts retain
  their loaded revision; explicit Reload resolves stale edits. Accepted responses
  alone reset the baseline. Requests freeze editing until acknowledged. Normal
  untouched job drafts follow fresh server status without hiding current progress.
- Pinned API mapping confirms Dist.CLIENT registration, native slots and key/scroll
  signatures. Slot.isActive is presentation only; existing server guards remain.
  Added a client snapshot counter to avoid deep-copying large unchanged settings NBT
  every screen tick. This is presentation bookkeeping, not an authority revision.
- Focused compile/processResources and common/server boundary sensor PASS:
  .agents/evidence/M5-screens-boundary.log. Final affected compile PASS after draft
  refresh correction: .agents/evidence/M5-screens-final-compile.log. Initial panel
  definite-assignment failure repaired and four new compiler warnings removed;
  no suppression. Static UI translation keys resolve, both JSON resources parse,
  and git diff --check PASS. Prior boundary evidence remains applicable (subsequent
  edits stayed in client presentation and did not change dependencies).
- Controller confirms approved scope: no Permissions page, no equipment grants,
  no consumer mining/scanning engine. Human owns actual GPU client testing by
  explicit instruction. Real GUI scales, keyboard/tooltips, modded search, slots,
  dialogs and visual quality remain PENDING in the final human checklist; these
  have NOT been measured or claimed PASS. Independent milestone sensors PENDING.

## M5.7 — Completion notifications and inbox (automated COMPLETE)

- A real successful finite source-block completion now records owner history before
  online delivery. The roster persists the current worker's last-notified run marker,
  latest 100 immutable completion records (run/worker/name/targets/amount/time/read),
  and independent toast/sound preferences with their own optimistic revision. Old
  version-one rosters without those fields retain empty history/enabled defaults.
  Ordinary save/load, retirement and reactivation preserve the marker; arbitrary
  historical rollback is outside the approved normal-shutdown persistence contract.
- Login sends one unread-count summary per connection/login, never individual offline
  replay. New server menu actions page five newest-first owner entries, mark one/all
  read, and edit preferences. No global broadcast or controller requirement for owner
  completion delivery. Dedicated-server Notice payload booleans remain independent.
- Client Inbox shows records/details/read state and preferences. Native marker toasts
  use one optional 0.20-volume amethyst chime. Pinned ToastComponent source proves its
  automatic in/out audio has no public opt-out: one client-only mixin redirects the
  two calls only for WorkerCompletionToast, preserving all other toast audio. Its
  separate client config/package protects dedicated-server class loading. Actual
  toast visuals, silent transition behavior and audio level remain human PENDING.
- M5.5's exact ServerPlayer transport allowlist now additionally permits only
  WorkerNotifications, whose references address real recipients/login events.
  This implements the approved notification transport boundary; worker/runtime
  impersonation, FakePlayer/Factory, consumer engines and common/client bans remain.
- Shared server and client compile/processResources PASS (M5-notifications-compile.log
  and M5-notifications-client-compile.log). Final compileGameTestJava and all four
  focused notification GameTests PASS, final raw log M5-notifications-runtime.log.
  Native case observes one actual target destruction, pause/apply/resume and one
  owner payload. M5.1's existing partial-progress pause proof remains applicable.
  The 101-record cap fixture restores valid completed NBT; it is explicitly not a
  claim of 101 native mining executions. Full record equality/read states/preferences
  round-trip, replay guards, noncompletion negatives, offline summary deduplication,
  foreign-run rejection and all four presentation-flag combinations are covered.
- Controller reviewed tests and requested the additional full-record, foreign-run,
  both-disabled and cleanup assertions; corrected focused rerun PASS. Fixtures clean
  login bookkeeping and restore the mining chamber even after cleanup failures.
  Resource/translation references and diff whitespace checks PASS. No full profile
  was run before the independent gate. M5.7 scope confirmed; M5.8 is next.
- Human checklist: docs/M5_MANUAL_ACCEPTANCE.md. No GPU client launched in this task.

## M5.8 — Independent milestone gate (IN_PROGRESS)

- Freeze the feature candidate in a separate clean detached worktree, preserving
  concurrent human workflow edits and untracked GUI reference art in the main tree.
- A fresh independent verifier supplies the configured default,
  architecture_sensitive and runtime_minecraft profile union once, plus approved
  scope and behavioral evidence review. No identical preliminary gate was run.
- Human GPU checks remain PENDING even if every automated sensor passes. Final
  acceptance requires their results; no exception or waiver is inferred.
