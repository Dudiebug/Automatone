# M4 jobs, chunk loading and automatic resume

Human-approved chunk-loading addition, 2026-09-06. This supersedes the older
M4-M6 exclusions for chunk loading, owner-offline operation and automatic resume.
Implement M4.1-M4.4 prerequisites, then M4.5. M5 controller implementation remains
later work; update its specification for cross-dimension ownership-checked control.

## Jobs (M4.1-M4.4)

Keep one server-owned MiningSession per worker: target registry ID, requested
source-block count (zero means unlimited), completed count, state and error.
Finite requests accept 1 through 1,000,000 blocks. Reject invalid targets/amounts
and Start while busy without changing the current job. Count only actual matching
source-block destruction through WorkerEntityController, never inventory or drops.
Cancel native mining synchronously on the Nth target. Stop is idempotent and
retains the completed count. Native discovery/pathing/cancellation stay native.

## Tickets (M4.5)

While mining, force a moving 3x3 square (nine explicit chunks); otherwise force
only the worker's chunk. Use NeoForge TicketController UUID ownership and ticking
tickets. One persistent center controller and a separate working-ring controller
allow startup to discard old rings and restore only centers. Apply diffs on center
or activity changes, adding new requests before removing old ones. Overlap between
workers must not let one remove another's tickets. Death/permanent removal releases
all tickets; dimension transfer releases the old dimension and acquires the new.
Minecraft's internally loaded neighboring chunks are not part of the explicit count.

## Save/restart

Persist worker identity, optional owner UUID, nine-slot inventory/selection and
job target/amount/progress/state/error. Restore only RUNNING jobs automatically,
with a fresh runtime and remaining finite count. Completed/cancelled/failed jobs
remain idle. Invalid saved jobs become FAILED without a retry loop. Do not save
paths, scanned targets, generations or partial block damage. Shutdown detachment
preserves job intent and the saved center ticket. Validate restored center owners
after their chunk's entities have loaded; remove orphan tickets. A server that is
stopped or paused does no work, and no catch-up or crash transaction is promised.

## Later integration

M5 bindings must include UUID and dimension and resolve owned workers server-side
across dimensions, without proximity checks. Retain menu/controller/ownership
authorization. M6 hardens the persistence and native error mapping already present;
it must not reintroduce manual-only restart or no-chunk-loading assumptions.

## Verification

Focused state and runtime checks cover exact finite/unlimited counts, busy/Stop,
non-target and inventory/drop exclusion, nine/one ticket transitions, boundaries,
overlap/removal/transfer, no-player operation, restored finite/unlimited work,
terminal states staying stopped, inventory retention and orphan cleanup. Use real
server save/reload for restart evidence. Cross-dimension user authorization is an
M5 check. One independent applicable milestone profile follows a clean candidate;
repeat only checks invalidated by repairs. No new dependency or full-suite loop.
