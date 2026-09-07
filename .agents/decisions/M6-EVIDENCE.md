# M6 evidence

Contract: `docs/M6_FINAL_RELIABILITY.md`, explicitly approved by the human.

Baseline: `7e781a4b` / 0.12.1. Controller: Astra. M6 IN_PROGRESS.

## Prerequisite and verification contract

Human explicitly accepted M5 and authorized M6 implementation. Record M5 ACCEPTED

by human approval; historical M5 runtime/checklist measurements remain as recorded,

including PENDING entries. No previous measurement is upgraded to PASS.

Reuse existing tooling, dependencies, serializers and ownership policy. Installed

graphify, ponytail and old-coder skills inform structural navigation, minimal

changes and acceptance mapping; repository proportional verification governs

cadence. No blanket coverage/mutation targets or preliminary full-profile run.

Astra reviews bounded Luna Max test work. One fresh independent clean-candidate

default + architecture_sensitive gate at M6.4. All Minecraft execution, including

GameTests and restart probes, is PENDING — HUMAN TESTING. No product publication.

## M6.1 — implementation COMPLETE; runtime PENDING — HUMAN TESTING

Rebased the task contracts onto expanded M5 and the approved M6 implementation

plan. Source preflight confirms existing worker v1–v3 entity serialization and

roster-owned profiles/archives/notifications; no new persistence framework needed.

Fixed WorkerRoster.removed so foreign-owner/stale-incarnation callbacks cannot

capture/delete the authoritative record. Existing menu/roster/fleet/collection/

relocation entry points already validate owner, context and stale revisions.

Added three real-entity GameTests for v1–v3 paused saves, five invalid-job variants,

36 component-bearing inventory slots/equipment, and live/unloaded removal guards.

Registered the M6 namespace. No serialization format change was needed.

PASS: :worker:compileGameTestJava :worker:test --tests automatone.worker.MiningSession*

(19 unit tests, zero failures), persistence-focused.log. New fixtures compiled in

persistence-fixtures-final.log. Replaced one new fixture reference-equality warning

with Entity.equals (identity semantics); no new warning remains. GameTest runtime

is PENDING. Existing WorkerChunkLoadingGameTest, WorkerM5FoundationGameTest,

WorkerInventoryGameTest, WorkerRosterGameTest and WorkerNotificationsGameTest cover

running/terminal saves, legacy conversion, full inventory, archives and notification

round trips; existing menu/batch/collection tests cover negative authorization.

Luna returned no patch after extended design; Astra took over and reviewed the

focused tests. Disproved assumption: any removal callback with matching UUID is

safe to trust. Controller confirms focused code completion and scope/invariants.

## M6.2 — implementation COMPLETE; runtime PENDING — HUMAN TESTING

Loading into an already attached worker previously retained old native work.

WorkerEntity now disposes the old runtime before restore and attaches one fresh

runtime afterward, preserving readiness-gated RUNNING intent. Added a live-reload

fixture observing disposal/cancellation, PAUSED progress and provider uniqueness;

extended the existing two-process restart probe with a nonzero paused job/run ID.

Reviewed existing transfer/death/reactivation/ticket policies and shutdown order:

WorkerBatch cancels unfinished children/reservations before WorkerRelocation stops;

successful children remain intact. Runtime disposal cancels native processes and

invalidates scans. No replacement loading/queue engine was needed.

PASS: production + GameTest compilation and four provider/generation unit tests.

Initial lifecycle-focused.log command incorrectly used unqualified test, which

also selected worker:test and failed there with no matching root test names.

Root tests passed and fixtures compiled. Corrected command uses :test;

lifecycle-focused-final.log PASS with valid checks UP-TO-DATE. No test assertion

or product behavior was weakened. Existing shutdown, batch/relocation and ticket

fixtures are retained. Runtime/restart execution remains PENDING — HUMAN TESTING.

Controller confirms scope/code completion. Disproved assumption: saved-data load

only ever targets an entity without an existing native runtime.

## M6.3 — implementation COMPLETE; runtime PENDING — HUMAN TESTING

Added additive IMineProcess.TerminationReason/accessor. MineProcess records all

native exits, keeps the last reason through repeated cleanup, clears it on new

work and rejects stale mailbox results using existing generations. Blacklisted

exhaustion reports PATH_FAILED; native exploration remains unchanged. Scan/start

exceptions retain full diagnostic stack traces in the server log.

Worker RUNNING-only mapping preserves progress/run ID, maps unexpected cancellation

to INTERRUPTED and cannot overwrite Pause/Stop/source-count completion. Native

item-count COMPLETED is not accepted as consumer source-count completion. Added

43 localized error messages/fallback and mapped every existing screen error display;

protocol 5, packet shape, save versions and legacy string codes are unchanged.

PASS: seven focused native lifecycle/generation/termination unit tests

(native-termination-final.log), 21 worker MiningSession unit tests and all GameTest

source compilation (worker-failure-focused.log). Added five real native GameTests:

no targets, path blacklist exhaustion, disabled breaking, allowBreakAnyway exact

completion, and native completion/async internal failure with sticky cleanup.

Runtime results remain PENDING — HUMAN TESTING. Localization static check PASS:

138 literal screen keys exist, 43 error entries (localization-check.json).

Luna authored initial cancellation/async tests. First fixture bootstrapped Minecraft

inside a plain unit JVM and failed because LoadingModList was unavailable

(native-termination-initial.log). Astra rejected added bootstrap infrastructure,

removed it, required draining stale completion before active delivery to prevent a

vacuous assertion, and added new-run error reset. Scan failure logging now uses the

server logger with its stack trace, so this mailbox check requires no game bootstrap.

No product assertion was weakened. Existing native-file compiler warnings are in

unchanged methods; the independent gate owns their exact disposition checks.

Controller confirms task code completion, native/consumer boundaries and scope.

## M6.4 — IMPLEMENTING

Fresh independent profile, graph update and package validation: PENDING.

All in-game acceptance remains PENDING — HUMAN TESTING.

## Final acceptance matrix

| Contract | Code/fixture evidence | In-game measurement |
| --- | --- | --- |
| v1–v3 saves, invalid jobs, identity/36 slots/equipment | M6 persistence fixtures + existing inventory/foundation tests; compilation PASS | PENDING |
| Profiles/overrides, archives and notification deduplication | Existing roster/notifications/collection fixtures retained | PENDING |
| RUNNING-only finite/unlimited resume, PAUSED/terminal retention | Extended WorkerRestartProbe; state unit tests PASS | PENDING |
| Fresh runtime, stale work, shutdown and tickets | M6 attached-load fixture, native generation unit tests PASS; existing chunk/batch/relocation fixtures | PENDING |
| Ownership, stale/forged/repeated packets, collection overflow | M6 removal guards; existing menu/batch/collection negative tests retained | PENDING |
| Typed failures, Pause/Stop and exact completion | Seven native + 21 worker focused unit tests PASS; five M6 native fixtures compile | PENDING |
| Readable status and normal/compact layouts | 138 localization keys and 43 error entries checked | PENDING |

Graphify incremental code update PASS: 6,295 nodes / 19,625 edges. Eight existing
Groovy parser warnings are advisory; document semantics were not regenerated.
Corrected Windows-default encoding in new task/evidence/error text to UTF-8 before
candidate packaging. No protocol/schema change or analyzer policy change.
