# M3 evidence

Status: IN_PROGRESS. M3.4 reopened for independent-gate runtime repairs; client observation UNVERIFIED.

## Authority and starting point

- Human approved implementation of docs/M3_MINING_IMPLEMENTATION_PLAN.md,
  including non-player hooks and the Stevenator player-like renderer.
- Starting checkout: bd7877ca (clean); accepted prerequisite 30561e6b is an
  ancestor. M2 remains ACCEPTED; its completed checks are not being repeated.
- Astra owns production/integration; a reused Terra author owns tests. A separate
  fresh Terra verifier is reserved for the final gate.
- Installed Graphify structural query identified the existing worker/controller,
  ToolSet, native break helper and MineProcess. Source review confirmed the
  controller is a stub, native raycasting/inventory/tool selection already exist,
  and the break helper's per-tick hitting flag is distinct from cancellation.

## Measurements

- Initial git status: clean. M2 prerequisite ancestry check: PASS.
- M3.1–M3.4 focused checks: PASS (measurements below).
- Independent profile union: static/root runtime PASS, worker runtime FAIL; affected repair checks PENDING.
- Three fresh native mining worker-server runs: PENDING.
- Actual client visual/audio observation: UNVERIFIED (host OpenGL unavailable).

## Explicit boundaries

Player-only break/protection events are outside the human-approved non-player
contract. The old blanket ban on client classes in the worker conflicts with the
newly approved renderer; replace it with an enforced common/client dependency
boundary, without relaxing native ownership or fake-player prohibitions.
No M4 work, dependency upgrades, suppressed findings or waived checks are planned.

## M3.1 development

- Added a small package-private WorkerBreakState for target/progress and one
  advance per tick (including across reset/retarget). Block/tool snapshots live
  in the existing controller, which remains the only world-interaction adapter.
- The initial two targeting RED attempts failed the fixture's eye-ray precondition
  before reaching controller behavior. They are fixture failures, not evidence
  that the controller regression was demonstrated. Raw outputs are retained in
  `.agents/evidence/M3/logs/m3-1-targeting-baseline-red*.log`.
- Corrected baseline RED: the larger cleared chamber passed the actual eye-ray
  precondition and failed `A valid worker eye ray must begin block-damage intent`.
  The other 14 worker GameTests passed. Raw:
  `logs/m3-1-targeting-baseline-red-fixture-corrected.log` under the same directory.
- Implemented controller target validation using native RayTraceUtils, fixed
  4.5 reach, target/state/tool and selected-slot invalidation, explicit reset,
  removal cleanup and read-only interaction diagnostics. WorkerContext remains
  byte-identical, preserving its exact approved ownership dispositions.
- Initial unit attempts exposed NeoForge registry bootstrapping requirements
  (`LoadingModList` absent in plain JUnit). Rather than introduce a mod-loader
  harness, isolated the coordinate/tick/progress state from registry-backed
  snapshots. Unit tests cover the former; GameTests cover real blocks/tools.
  Failed harness logs remain `logs/m3-1-worker-break-state-test*.log`.
- PASS: `./gradlew.bat :worker:test --tests automatone.worker.WorkerBreakStateTest
  --no-daemon --console=plain` — 3 tests, zero failures/errors/skips. Raw:
  `logs/m3-1-worker-break-state-test-revised.log`.
- PASS: `./gradlew.bat :worker:runGameTestServer --no-daemon --console=plain` —
  all 19 required tests passed (14 existing + 5 targeting). Raw:
  `logs/m3-1-worker-targeting-gametest.log`. Measures valid target, 4.49/4.51 eye
  reach, occlusion, misalignment, air, target/slot/tool/block changes, passive
  reach/facing invalidation, removal and harmless per-tick hitting-flag clearing.
- A syntax-only test pattern-matching cleanup removed one new compiler warning.
  `:worker:compileGameTestJava --no-daemon --console=plain` then passed without
  warnings (`logs/m3-1-worker-gametest-compile-after-pattern-fix.log`). The prior
  server measurement remains applicable; the syntax change preserves behavior.
  Twelve pre-existing reference-identity warnings were not recategorized as new.
- Controller review confirms M3.1 focused criteria and unchanged native/server
  ownership. The state completion transition is unit-tested; real destruction
  and physical progression belong to M3.2. Broad profiles remain PENDING.
- Graphify incremental code update passed: 4,782 nodes / 12,832 edges. Eight
  known partial Groovy parses remain advisory; document semantics were not rebuilt.

## M3.2 development

- Added a client-only classic PlayerModel renderer with held-item arm poses and
  vanilla item layers. Downloaded the selected skin from the page's public PNG
  URL after its download endpoint returned a browser challenge. PNG signature,
  64x64 dimensions, 2,021 bytes and SHA-256 were verified; provenance/hash are
  bundled in worker-skin-source.txt. The texture is unchanged.
- `:worker:compileJava --no-daemon --console=plain` passed for renderer/attributes.
  Its deprecated EventBusSubscriber.bus warning was repaired by removing that
  obsolete member: pinned FML 4.0.44 AutomaticEventSubscriber bytecode dispatches
  IModBusEvent handlers to the mod bus automatically. Client-only Dist restriction
  remains explicit. The next focused run will compile the corrected annotation.
- Source inspection confirms Item.mineBlock accepts LivingEntity and uses the
  tool component's damagePerBlock; Block.dropResources accepts the real worker
  and selected tool and invokes NeoForge BlockDropsEvent/experience handling.
  Entity destruction permissions use canEntityDestroy/canEntityGrief and the
  cancellable LivingDestroyBlockEvent; player-only events are not fabricated.
- Behavioral RED: grounded stone, actual eye ray and selected iron pickaxe passed
  fixture preconditions, then failed on zero progress
  (`logs/m3-2-grounded-stone-progression-red.log`); the other 19 tests passed.
- Implemented actual block/tool progress and worker mining attributes, vanilla
  haste/fatigue/submerged/airborne modifiers, changed-stage crack broadcasts,
  four-tick hit sounds, successful-removal-only durability and tool-aware drops.
  Final destruction revalidates snapshots/raycast after non-player hooks.
- The frozen progression test is GREEN: all 20 worker GameTests passed
  (`logs/m3-2-grounded-stone-progression-green.log`). Renderer correction also
  compiled. The reported controller ReferenceEquality warning is the unchanged
  `container != worker` ownership guard present at bd7877ca, not new code.
  Remaining M3.2 tool/drop/hook and architecture cases are being authored.
- Selected-slot comparison PASS: all 21 worker GameTests
  (`logs/m3-2-selected-tool-speed.log`), including faster incomplete stone
  progress with an iron pickaxe selected from slot 1 than with an empty hand.
- Pinned-source review confirms Level.destroyBlock supplies block updates,
  final level event 2001 and BLOCK_DESTROY game event; it does not call
  Block.destroy, so the explicit post-removal callback is retained. PlayerModel
  copies limb poses into sleeves/pants/jacket for the requested outer layers.
- Windows Computer Use initialized successfully and enumerated application
  windows. This establishes an observation route, not a completed client check.
- PASS: batched destruction slice, all 26 required worker GameTests in
  `logs/m3-2-destruction-slice-gametest.log`. Added bedrock rejection, ore
  completion with exactly one target-specific BlockDropsEvent and raw iron in
  the world, selected-tool wear and breakage, no inventory insertion, Haste
  comparison, and cancellable LivingDestroyBlockEvent preserving the block.
  Completion and denial both clear crack stage. Controller-only fixtures now
  detach the idle native runtime, retaining real worker/world/controller and
  native raycasting, so later input-release behavior cannot confound progress.
- Test compilation initially needed API/syntax corrections; final focused
  `:worker:compileGameTestJava` passed clean in
  `logs/m3-2-destruction-slice-compile-final.log`. Earlier compile outputs are
  retained; none is claimed as a behavioral RED. Architecture check remains pending.
- Focused architecture test initially passed five cases after replacing the
  old blanket client ban. Controller review found its new common boundary
  omitted the worker's own client package; author was directed to include it
  and rerun that focused sensor before completion. Existing no-legacy,
  no-fake/server-player, native-engine and library-to-worker rules are retained.
  Client registration is inspected as bytecode with the explicit Dist.CLIENT
  annotation and typed mod event. Initial raw logs: `logs/m3-2-architecture-*`.
- PASS: `:worker:checkstyleGameTest :worker:checkstyleSensorTest`
  (`logs/m3-2-test-style.log`). Graphify incremental update: 4,869 nodes /
  13,187 edges; the same eight advisory partial Groovy parses remain.
- Final common/client boundary sensor PASS, five tests with zero failures,
  errors or skips (`logs/m3-2-architecture-boundary-final.log`), after adding
  the worker client package. Prior GameTest evidence is unaffected and reused.
- Controller confirms M3.2 COMPLETE: actual hardness/tool/modifier progression,
  successful-removal-only wear and normal tool-aware loot, entity destruction
  hooks, crack/sound effects and isolated renderer meet this task's criteria.
  No native algorithm, dependency, threshold or suppression was added. The
  required actual client observation and full milestone profiles remain PENDING.

## M3.3 development

- Baseline: completed M3.2 candidate 3e0e253d. Read M3.3 and its native
  callers; scoped Graphify query confirms MineProcess / BlockBreakHelper /
  PathingBehavior / InputOverrideHandler ownership. No consumer cancellation
  engine is needed.
- Source disproved the assumption that existing guards also protect path
  publication: scan generations are guarded, but PathingBehavior publishes
  asynchronous results unconditionally. The path search also clears a prior
  cancellation at calculate entry. Repairing this native cancellation race is
  within M3.3; existing scan guards and regression evidence are retained.
- BlockBreakHelper skips reset when wasHitting is false and ignores release
  during inter-block delay. MineProcess clears scan intent only. Native
  clearAllKeys is used during ordinary mining ticks, so it must not become an
  unconditional break reset. Tests and native repairs are in progress.
- Implemented the independent path-publication guard while the author prepares
  the mine-cancellation RED. PathingBehavior invalidates the pending search
  under its existing locks and rejects a completed result whose search is no
  longer current. Late work cannot install an executor or clear a newer search.
  The search algorithm is unchanged. Segment cancellation clears inputs only
  when it had path state; idle calls must preserve the existing manual native
  input contract. MineProcess and BlockBreakHelper remain unchanged for the
  first cancellation RED; this is a partial repair, not a pristine baseline.
- The first native cancellation attempt failed a transient swing-state
  precondition, before cancellation. The author replaced that
  observation with a test worker that counts native swing calls and delegates
  to vanilla to isolate cancellation. This measures invocation, not rendered
  animation. Subsequent source review found a real animation omission below.
- Meaningful RED: native input reached the controller, accrued incomplete
  progress/stage and invoked MAIN_HAND swing, then MineProcess.cancel failed
  to clear controller state. All other 26 worker GameTests passed. Raw:
  `logs/m3-3-native-cancel-red-final.log` (with the partial path guard above).
- Repaired BlockBreakHelper to explicitly reset even with a false previous-hit
  flag and to handle release/missing ray before its delay. MineProcess resets
  now release the prior active miner's path/goal/inputs/controller, while
  preserving another current process owner's state. Scan locking/generations
  and ordinary clearAllKeys behavior remain unchanged.
- Frozen cancellation GREEN: all 27 required worker GameTests passed in
  `logs/m3-3-native-cancel-green.log`, including immediate idle/input reset
  and an intact unfinished target after 20 server ticks. Remaining M3.3
  release/restart/path-race cases and focused scan regressions are pending.
- Parent investigation of the initial swing failure found that base Mob does
  not call LivingEntity.updateSwingTime. Monster.aiStep calls it explicitly;
  the worker did not. Thus native swing invocation alone cannot establish
  animation progress. The earlier timing explanation is not accepted as a
  sufficient diagnosis. Add the missing vanilla swing-state tick and retain
  an animation-progression regression; actual client observation stays pending.
- Swing progression RED: `logs/m3-3-worker-swing-red.log` ran 30 tests,
  failing only the bounded vanilla swing-state assertion. Added the vanilla
  updateSwingTime call to WorkerEntity.aiStep on both sides. GREEN:
  `logs/m3-3-native-cancellation-suite.log`, all 30 required tests passed.
  This includes release, lost ray, explicit helper stop without prior hitting,
  restart and repeated cancel, in addition to swing progression.
- PASS: existing root MineProcessLifecycleTest (2) and
  MineProcessSessionGenerationRegressionTest (3), zero failures/errors/skips;
  `logs/m3-3-mineprocess-lifecycle-session-tests.log`. No scan regression was
  replaced or weakened. The deterministic pending-path race test is pending.

### Existing pathing warning revalidation

A separate bounded read-only Luna review revalidated only previously approved
SpotBugs IDs 36-41 after the necessary PathingBehavior source change. This is
not the reserved independent Terra milestone gate. Reviewer: luna_old_coder;
Windows host; fresh context; no code/tests/analyzer run or milestone acceptance.

- Current canonical UTF-8/LF SHA-256:
  `92bdcb460214abb354c94ac0f02fdb100a6bacdad5e871a983925b8b17970e3b`.
- IDs 36/37/38/39 remain server-tick-owned calcFailedLastTick,
  pauseRequestedLastTick, cancelRequested and pausedThisTick. Their relevant
  writes/callers and finding line identities are unchanged.
- IDs 40/41 retain the required live getCurrent/getNext handle contracts.
- The reviewer checked the unchanged tick caller chain and consistent
  pathPlanLock-before-pathCalcLock ordering. Early cancelled search work may
  still finish computing, but the new guard rejects its publication.
- Controller verified the exact reviewed source hash and integrated only the
  six approval source hashes/evidence links. Eligibility identities, contracts,
  reviewer identity, thresholds and all other approvals are unchanged. Raw
  analyzer confirmation remains PENDING at the single milestone gate.

### M3.3 completion

- New root race regression exercises the production async completion closure
  with a real AStarPathFinder, blocks publication until calculation finishes,
  cancels it and installs a replacement, then verifies late completion preserves
  that replacement. This directly detects the prior unconditional clear. It
  does not assert the result type; successful stale-path rejection is supported
  by the shared guard's source flow, not claimed as a separate runtime result.
  No path-race RED was rerun after the guard was implemented.
- Root fixture development first encountered NeoForge split-package resolution;
  moving the test to the existing baritone.gametest package fixed that harness
  issue. A 1ms fixture calculation limit was increased to 1000ms. No production
  timeout or algorithm changed. Final root run: all 26 required GameTests passed
  in 1.858s. Raw server output copied without rerun to
  `logs/m3-3-root-pathing-cancellation-race-server-20260905-192209.log`.
- Root :compileGameTestJava passed without Java diagnostics. Its original
  captured stdout was archived without rerun as
  `logs/m3-3-root-pathing-cancellation-race-compile-captured-20260905-192151.log`.
- Controller confirms M3.3 COMPLETE. The 30-worker server result and five focused
  root unit results remain applicable (no subsequent production changes).
  M3.1 retarget/removal evidence and M3.2 ownership-boundary evidence are reused;
  source review confirms the added worker swing tick adds no client dependency
  or native engine. Full profiles and end-to-end mining/visual proof stay PENDING.

Graphify refresh after M3.3: PASS (4,932 nodes, 13,501 edges, 271 communities); advisory community labels changed. No full sensor profile run before the independent milestone gate.

## M3.4 development

M3.3 completed candidate: `e944b15b`. M3.4 begins from that commit.
Added `:worker:runM3Client`, loading the production mods and existing GameTest
source set, with structures staged into `worker/build/m3-client`. It enables
NeoForge's development GameTests without a new dependency. Client launch and
actual visual/audio observation remain PENDING. Desktop window enumeration
succeeded; a Minecraft window has not yet been launched.
Graphify scoped preflight located WorkerEntity, native LookBehavior, the renderer
and existing worker fixtures. Pinned BodyRotationControl source only clamps head
rotation while moving; the native server look changes entity yaw/pitch. The new
ore proof will measure head alignment before any presentation repair.
Client preparation PASS: `./gradlew.bat :worker:prepareM3ClientRun --no-daemon
--console=plain`, 7 seconds, assets cached (`logs/m3-4-prepare-client.log`).
This validates the new run configuration, not actual client presentation.
The first proof server attempt failed before executing mining: the new fixture
was initially placed outside the staged structures directory. The test author
is correcting the fixture location; this is a harness failure, not a native
mining result.
The first real 31-test server run failed only the native ore proof after 700
accelerated ticks: mining active and goal present, but no path, movement or
breaking. Native `MineProcess.searchWorld` skipped WorldScanner for tracked ore
when the cache was empty and `extendCacheOnThreshold` was false. Settings.java
explicitly documents that option as extending a nonzero cache result. Restored
the empty-result fallback to the existing native scanner. The proof retains
default settings; no consumer scanner, coordinates or cache seeding is added.
After the cache fallback repair, the frozen proof advanced through native path,
movement, ray and progressive destruction and failed solely at head-yaw
convergence (31 tests, one failure). Raw:
`logs/m3-4-native-mine-after-cache-repair-20260905-194321.stdout.log`.
This demonstrates the predicted presentation gap. WorkerEntity now mirrors its
native authoritative yaw into vanilla head tracking on the next server entity
tick. No rotation target, movement decision or client protocol is introduced.
Drop/wear assertions occur after the failed head assertion and are not yet PASS.
The next run after the head repair failed before path execution: 700 GameTest
ticks elapsed in only 422 ms (19:45:27.602–19:45:28.024), below the native
500 ms primary / 2,000 ms failure pathfinder budgets. Root compilation was
correctly UP-TO-DATE because MineProcess had not changed since the previous run.
This is not evidence of stale build outputs. The test author is adding bounded
observation pacing and diagnostics while retaining production timeouts and the
same tick/behavior assertions. Raw:
`logs/m3-4-native-mine-after-head-repair-20260905-194503.stdout.log`.
First pacing attempt was ineffective: `LockSupport.parkNanos(5ms)` returned early,
with all 31 tests completing in 2.655s despite a nominal 3.5s proof delay.
Diagnostics show the correct native GoalBlock for the ore and AStar still in
progress, proving discovery while path completion remains unmeasured. Raw:
`logs/m3-4-native-mine-paced-proof-20260905-195012.stdout.log`.
The test author is replacing the pacing primitive and measuring wall time;
this failed attempt is retained, not counted as a passing path/head measurement.
Final focused proof PASS: `:worker:compileGameTestJava` and
`:worker:runGameTestServer --no-daemon --console=plain`, all 31 required tests,
35.42 seconds. The fixture uses a measured 50ms observation delay (normal 20TPS)
without changing the 700-tick proof / 760-tick timeout or native settings. Raw:
`logs/m3-4-native-mine-50ms-paced-proof-20260905-195337.stdout.log`.
The earlier 5ms sleep did provide 4,423ms wall time and exposed a real 10-block
path plus initial movement but timed out before the full chain; that incomplete
measurement is `logs/m3-4-native-mine-sleep-paced-proof-20260905-195156.stdout.log`.

Controller review confirms M3.4 focused native proof: one block-type request,
no target coordinates in discovery/pathing input, native goal/path and >2-block
movement, progressive damage with real reach/ray and head facing, destruction,
raw iron and tool wear. Existing M3.3 cancellation cases remain green in the
31-test run. The demo reuses the chamber; only its display run slows the native
break-speed attribute to 2.5%. Operator command is `/worker_m3_demo start|cancel`;
use spectator mode for the elevated viewing position. No consumer engine added.
Three fresh consistency runs and the independent profile union remain PENDING.

Actual client observation: UNVERIFIED. Parent ran `:worker:runM3Client`; NeoForge
EarlyDisplay failed every GLFW profile from OpenGL 4.6 through 3.2 with
`WGL: The driver does not appear to support OpenGL`. No Minecraft window was
available via desktop enumeration. Raw parent launch is `logs/m3-4-client-launch.log`.
The author inadvertently duplicated the launch despite parent taking ownership,
producing an additional log-lock warning; both attempts had the same OpenGL
failure. Parent terminated only the two identified failed task client processes
(PIDs 6940 and 18720), allowing Gradle to exit. No visual or audio PASS is claimed,
no driver installed, and no graphics requirement waived. Complete all automated
verification before reporting this remaining environmental check to the human.

Graphify incremental refresh PASS: 4,986 nodes / 13,714 edges / 268 communities.
The existing eight Groovy parse advisories and community relabel warning remain
advisory. M3.4 focused work is controller COMPLETE; milestone remains IN_PROGRESS
pending the independent gate, fresh-server consistency and client observation.

## Independent milestone gate and repairs

The single fresh Terra verifier received only the human contract, approved spec,
clean candidate `bb28ca6744b59b11c7013b5c7ff416e01ec4cef6` and runner entry point.
It ran the default + architecture_sensitive + runtime_minecraft union once.
Initial raw/report: `.agents/evidence/M3/independent-milestone.json` and
`independent-milestone.raw/checks.txt`. Compile, unit tests, Checkstyle, Error Prone,
SpotBugs, architecture and duplication PASS. Root GameTests: 26/26 PASS, 1.952s.
Worker GameTests: 31 run, two FAIL, 39.56s: native navigation cancellation did not
reach cancellable movement within 400 ticks; native ore proof still had AStar in
progress without movement after 700 ticks / 36,414ms. The prior focused PASS does
not establish consistency or override these fresh failures. Extra worker runs
were held for repair rather than repeating the same failing candidate.

The runner mislabeled these real GameTest failures as UNVERIFIED/INCOMPLETE:
its runtime mapping omitted the underlying runGameTestServer failure, and a broad
UNVERIFIED word match consumed unrelated log prose. Acceptance remained blocked.
Correcting that mapping requires a focused regression, not a policy relaxation.
The initial JSON/raw are retained unchanged; actual runtime failures are FAIL.

Blind source review found native ownership intact and no injected target/path
or test double in the ore proof. The verifier recorded the run/spec/test/checker
and ownership findings before receiving this builder evidence. It remains the
same independent context for affected repair checks; no second review context
or identical preliminary/full-profile rerun is planned. Canary/mutation and
additional review rounds were not run under the proportional policy.

Root cause under repair: pinned ServerChunkCache.getChunk dispatches background
lookups to the main-thread executor and joins. BlockStateInterface and native
WorldScanner use it during asynchronous work. Pinned ChunkMap instead exposes
an immutable, volatile visible-chunk map expressly for access from other threads;
its public holder lookup and getChunkIfPresent(FULL) read completed atomic futures
without joining or loading chunks. Reuse that loaded view in native lookups.
Test author owns focused background-access and checker regressions; no consumer
scanner, path policy, native timeout or dependency change is proposed.

The native access regressions demonstrated RED: root 28 tests, exactly the two
new BSI/scanner background reads timed out while the server thread waited; prior
26 tests passed. Raw server copy: `logs/m3-native-chunk-access-red-server.log`.
Implemented shared `BlockStateInterface.getLoadedChunk`: on server use the
published visible chunk holder and completed FULL chunk; on other providers keep
the existing non-loading lookup. BSI get0/isLoaded and the three native scanner
lookups reuse it. Root GREEN: 28/28, 1.902s (`logs/m3-native-chunk-access-green.log`).
No new chunk snapshot, scanner, path algorithm or dependency was introduced.

Affected compile, Checkstyle and CPD passed (`logs/m3-final-compile-static.log`).
That invocation correctly blocked at the old source-hash approval for AIR;
it is not an all-PASS static run. The helper now sits after the existing
methods, preserving frozen AIR return identity at line 93; the analyzer and
approval validator will be rerun on the reviewed source.

### Existing AIR warning revalidation

Reused independent Luna reviewer `luna_old_coder` revalidated only existing
SpotBugs ID 67: AIR is still the private static immutable registry BlockState,
returned by get0(III) for invalid vertical coordinates at line 93. VALID,
canonical UTF-8/LF source SHA-256
`216c07456120a73c59e90fd1a4cc9b98300bd357572777b4a7f15a93eb5dee1f`.
Updated only that approval's source hash and evidence link. Frozen eligibility,
identity, reviewer and contract remain unchanged; no new warning was waived.

The workflow repair recognizes underlying runGameTestServer failures and uses
explicit measurement markers rather than an unrelated UNVERIFIED word in prose.
Existing 38 workflow repair checks passed; retained new regressions and final
independent affected checks are still to be recorded.

Human clarified at 2026-09-06 03:21 UTC that the offered manual checks mean
IN-GAME appearance/GUI-style observations. Automated GameTests remain this
implementation's responsibility. Continue those autonomously; hand off only
visual/audio observation and keep M3 unaccepted until it passes.
