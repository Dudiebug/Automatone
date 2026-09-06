# M4 jobs and chunk loading evidence

Human approved docs/M4_JOBS_AND_CHUNK_LOADING.md on 2026-09-06.
Baseline f2568be8 is M3 ACCEPTED after manual preview satisfaction, worker34PASS,
and independent affected static PASS. M4.5 active. Milestone gate PENDING.

M4.1 COMPLETE: immutable session snapshot and post-destruction source callback; three focused state tests PASS (:worker:test --tests automatone.worker.MiningSessionTest, logs/m4-session-state.log). Controller confirms callback occurs only after actual level.destroyBlock success. Native end-to-end quantity matrix remains PENDING for M4.4; milestone profile PENDING.

M4.2 COMPLETE: public server-only startMining wraps native discovery with source-count cancellation; finite1, finite3of5 and unlimitedpast3 real GameTests PASS (logs/m4-native-quantity.log). Existing inventory does not count. Focused namespace uses NeoForge's existing namespace selector; full default includes old and M4 tests. M4.3 active; no new dependency.

M4.3 COMPLETE: one server-thread stopMining method updates only active sessions and reuses native cancellation/reset. Affected compile PASS (logs/m4-stop-compile.log); repeated/idle Stop state evidence reused from M4.1, native synchronous cancellation from M4.2. Runtime cancellation matrix is the next M4.4 task.

M4.4 COMPLETE: all8 focused native quantity/cancellation GameTests PASS (logs/m4-quantity-cancel-matrix.log), including finite1/3, unlimited, actual stone obstruction destruction, multi-drop redstone, existing items, busy/repeated Stop, Stop after first destruction and mid-slow-block cancellation. First launch was interrupted before test execution by environment refresh; the completed invocation is the recorded measurement. M4.5 now active; milestone gate remains PENDING.

M4.5 IMPLEMENTING: added UUID-owned center/ring tickets with add-before-remove diffs,
post-entity-load orphan validation, chunk-ready deferred native startup, and versioned
inventory/owner/product-state persistence. Only saved RUNNING jobs resume, preserving
the source count. Compile and six focused MiningSession tests PASS
(logs/m45-state-compile.log), including remaining finite progress, invalid snapshots,
terminal states and unlimited overflow. Initial compile caught an ignored Optional
return; changed validation to an explicit empty check and the affected rerun passed.
Runtime lifecycle tests, two-process restart proof and the independent milestone
profile remain PENDING. No native pathfinding or scanned targets are persisted.

Focused runtime PASS: all 15 M4 GameTests (logs/m45-focused-runtime.log, 66 seconds)
cover quantity/cancellation plus actual NeoForge UUID ticket transitions, boundary
diffs, overlap/discard, death, running dimension transfer, slot/owner NBT retention,
invalid saves and terminal inactivity. A test-only restart probe initially activated
without its phase property; repaired its activation guard and reran successfully.
The two-process restart harness is compiled and isolated under worker/build/m4-restart.
Its actual save/restart execution and the complete milestone profile remain PENDING
for the single independent clean-candidate gate. Controller confirms worker/native
ownership boundaries and approved scope; M4.5 is not COMPLETE until that restart
criterion passes.

Independent candidate 54824ed6: complete default/architecture/runtime union PASS
(.agents/evidence/M4/fresh-profile-manual.raw.log): root 28 and worker 49 GameTests,
unit tests, compilation/Error Prone, Checkstyle, ArchUnit, CPD and raw SpotBugs with
dispositions. The two-process restart criterion FAILed: write saved partial work
but the process timed out after shutdown; read found no loaded workers and timed
out (logs/m45-restart-write.log and logs/m45-restart-read.log). The initial independent
record is .agents/evidence/M4/fresh-independent-verification.json. The nonexistent
wrapper task ID M4 was a caller error before execution; the verifier ran its exact
underlying profile union once, without a duplicate profile run.

Controller repair: vanilla stopServer removes physical chunk tickets and drops
entity tracking before assigning a removal reason. Worker onRemovedFromLevel now
releases persisted tickets only for destruction or dimension transfer, preserving
shutdown anchors. Inspection of the failed world's actual entity region confirmed
all five workers retained their RUNNING/terminal state, inventory and progress;
chunks.dat had become empty. Baritone's four default non-daemon executor cores
also kept the saved server process alive. The executor now creates named daemon
threads; runtime disposal still cancels work and CachedWorld.close synchronously
awaits its background tasks and saves its cache before shutdown completes.

Existing SpotBugs approvals 1-8 revalidated by the controller under
EXECUTION_STRATEGY.md: only the thread factory changed. Exposed context/process/
selection identities, shared executor identity and accessor lines/contracts are
unchanged. Updated only their UTF-8/LF source hash; eligibility, thresholds and
original approval contracts remain unchanged. Affected raw analysis and a new
write/read run are PENDING; the failed original evidence is retained.
