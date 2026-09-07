# M6 — Final reliability and MVP release

Human-approved implementation plan, 2026-09-06/07. Baseline: accepted M5
`7e781a4b`, version 0.12.1. This supersedes conflicting original M6 single-target,
item-binding and excluded-fleet assumptions. Preserve all approved M5 behavior.

## Sequential implementation

1. M6.1: harden existing entity/roster persistence and shared ownership checks.
   Preserve v1–v3 worker migrations, identity, owner, 36-slot inventory/equipment,
   profiles/overrides, multi-target jobs, progress, paused state, run IDs and
   notifications. Invalid jobs fail with INVALID_SAVED_JOB while preserving
   otherwise recoverable ownership/inventory. Reject unauthorized/stale requests
   before mutation across individual/fleet/collection/archive/relocation actions.
2. M6.2: harden RUNNING-only resume after chunk readiness, one fresh runtime,
   retained finite progress, nine-active/one-idle tickets and owner-offline work.
   Preserve idle/paused/terminal states, death archives, reactivation and transfer.
   Shutdown cancels unfinished transient queues/reservations and keeps successful
   children. Stale results cannot mutate reconstructed work. No saved paths,
   scan queues, partial damage, native internals or offline catch-up.
3. M6.3: expose typed native CANCELLED, COMPLETED, NO_TARGETS, PATH_FAILED,
   BREAK_DISABLED and INTERNAL_FAILURE termination through a read-only accessor.
   Reasons survive repeated cleanup, reset on new work and obey native generation
   guards. Preserve native exploration/blacklisting. Worker maps unexpected
   cancellation to INTERRUPTED; explicit Stop/Pause/finite completion retain their
   product semantics. Replace generic native-stop/start failures with useful codes.
   Localize displayed errors with legacy/unknown fallback; retain string codes,
   save formats, packet shape and protocol 5.
4. M6.4: complete the acceptance matrix, update Graphify once, freeze a clean
   candidate and obtain one fresh independent non-game profile. Deliver matching
   0.13.0 native/worker JARs, exact dependency metadata, SHA256 checksums, upgrade
   guide and numbered human checklist. Preserve prior packages; do not publish.

## Verification and ownership

Astra implements, defines contracts and reviews/integrates bounded Luna Max
feature tests. Reuse tooling and useful regressions. Focus local unit/build/static
checks on changed behavior; compile GameTests but do not launch any Minecraft
client, GameTest server or restart probe. Human owns every in-game measurement.

The fresh independent gate runs default + architecture_sensitive once on a clean
candidate: compile, unit tests, Checkstyle, Error Prone, SpotBugs, ArchUnit and CPD.
Runtime/persistence/network GameTests and actual restart remain PENDING — HUMAN
TESTING. Delivery follows code checks; M6 acceptance requires human results or
an explicit exception. Historical PENDING results never become PASS by inference.

One record: `.agents/decisions/M6-EVIDENCE.md`, linking raw measurements. No new
dependencies, product features, retry system, persistent mining queue or arbitrary
crash transaction guarantee. Native discovery/pathing/mining stays Automatone-owned;
server product/authorization/world mutation stays server authoritative.
