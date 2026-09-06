# M3 evidence

Status: IN_PROGRESS. M3.1 COMPLETE; M3.2 next.

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
