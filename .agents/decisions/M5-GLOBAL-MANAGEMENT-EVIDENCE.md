# Global management extension evidence

Human-approved implementation contract: `docs/M5_GLOBAL_MANAGEMENT.md`.
Baseline: `7cce1ded` / released 0.11.1 inventory update.
Controller: Astra. Extension IN_PROGRESS; M5.10–M5.11 COMPLETE.

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

## M5.11 — Native placement and pickup rules

- Real BlockItem placement now validates worker/thread/world/reach/hit/bounds,
  honors entity grief and worker-attributed NeoForge placement hooks, rolls back
  captured changes on rejection and consumes exactly one held item on success.
  Server success is normalized for the native placement helper. Mob sneak input
  now updates worker pose: native pillar movement requires crouch feedback.
- Fixed always-on 64 ordinary-cobblestone reserve, zero other ignored pickups,
  protected components/job outputs and no routine inventory ejection. Versioned
  migration preserves custom lists and contents. Personal pickup defaults,
  per-worker overrides/reset, bounded intents and fixed-rule UI replace old knobs.
  Negotiated protocol is now 3. No fake player or second native movement engine.
- Initial focused runtime: 58/62 PASS. Fixture defects: a shallow bridge gap
  permitted descent; malformed-policy inventory assertion expected different
  stacks than it saved. Corrected the fixtures without weakening their contracts.
  Pillar failure exposed missing Mob crouch feedback and was repaired in the
  adapter. Slow-demo timeout passed on the affected native rerun; final gate
  retains this existing regression (no test limits or implementation relaxation).
- `:worker:compileJava`: PASS (`global-placement-compile.log`). Initial menu
  namespace: all 10 PASS (`global-placement-pickup-runtime.log`). Native/pickup
  rerun: 51/52 PASS, only the still-shallow bridge fixture failed
  (`global-placement-pickup-repair.log`). All eight pickup and slow-demo tests PASS.
- Final `:worker:runGameTestServer
  -PworkerGameTestNamespaces=automatone_worker_m5_placement_gametest --console=plain`:
  3/3 PASS (`global-placement-focused.log`), actual native bridge/pillar completion,
  exact consumption, direct guards and hook rollback. New namespace is included
  in the default gate. Compilation and `git diff --check`: PASS; no new warnings.
- Controller confirms M5.11 completion. Disproved assumption: setting Mob sneak
  input alone supplies native crouching state. Independent profile, actual
  restart, final GUI acceptance and packaging remain PENDING for M5.14.
