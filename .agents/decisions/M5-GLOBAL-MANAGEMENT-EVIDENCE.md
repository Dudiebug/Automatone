# Global management extension evidence

Human-approved implementation contract: `docs/M5_GLOBAL_MANAGEMENT.md`.
Baseline: `7cce1ded` / released 0.11.1 inventory update.
Controller: Astra. Extension IN_PROGRESS; M5.10 COMPLETE.

## Verification contract

Use existing focused tests during tasks. One fresh independent gate at M5.14
supplies default + architecture_sensitive + runtime_minecraft profiles and the
isolated persistence probe. Runtime lifecycle/performance checks are selected
where affected. No dependency changes planned. No coverage/mutation targets or
duplicate full profiles: repository proportional verification supersedes the
generic Old Coder gauntlet cadence. Approval: explicit human product plan;
detailed tests are controller-defined under the approved workflow.

## M5.10 — Death retirement

- Source diagnosis: WorkerRoster.removed deleted dead records and load skipped
  legacy dead entries. WorkerEntity.die called this after vanilla death handling.
- Implementation archives before vanilla death loot, with a post-death fallback
  for killers that suppress loot. Shared archival clears the removed entity's
  inventory/equipment after capture. Repeated callbacks and stale incarnations
  cannot replace the live replacement/archive. Legacy dead saves/attachments are
  recovered; reactivation resets health/death/fire/fall state and pauses work.
- `./gradlew.bat :worker:compileJava --console=plain`: PASS. Only three existing
  Error Prone warnings in unchanged controller/chunk-loading code; no new warning.
- Luna authored real death/component/legacy/cancellation tests; Astra reviewed
  them and required nonzero saved progress to detect resets. Fixed two fixture
  compile errors (nonexistent getter and missing local). First runtime: 17/18
  PASS; the legacy fixture expected PAUSED without starting a job. Start a real
  job before unloading; this corrects the fixture, not production semantics.
- Final `./gradlew.bat :worker:runGameTestServer
  -PworkerGameTestNamespaces=automatone_worker_m5_roster_gametest,automatone_worker_m5_menu_gametest
  --console=plain`: all 18 required tests PASS. Log:
  `.agents/evidence/M5/global-death-runtime.log`.
- Added menu death/rebinding/withdrawal regression and an archived death case to
  the existing two-process restart probe. Its actual run remains deferred.
- Controller confirms M5.10 scope/behavior/architecture completion. No disproven
  product assumption. Ordinary healthy unload/cap/menu authorization retained.
- Independent extension profile, two-process restart and manual GUI: PENDING.
