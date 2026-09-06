# M5 implementation evidence

Authority: human approved docs/M5_GUI_CONTROLLER.md and requested implementation.
Final correction: all newly created workers start with empty inventory. No starter
equipment. Baseline: accepted M4; existing human workflow/document edits preserved.

## Current status

M5.1 COMPLETE (controller: focused criteria and architectural scope confirmed).
M5.2 IN_PROGRESS. M5.3-M5.8 PENDING. Milestone acceptance PENDING.
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
