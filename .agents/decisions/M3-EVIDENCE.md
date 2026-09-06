# M3 evidence

Status: IN_PROGRESS. M3.1 and M3.2 COMPLETE; M3.3 COMPLETE; M3.4 is next.

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
- M3 focused checks: PENDING.
- Final default + architecture_sensitive + runtime_minecraft union: PENDING.
- Three fresh native mining worker-server runs: PENDING.
- Actual client visual/audio observation: PENDING.

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
