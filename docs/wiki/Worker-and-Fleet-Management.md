# Worker and Fleet Management

## Overview and ownership

The controller opens a roster belonging to its current holder. Each player can have
up to ten active or reserved workers; retired workers do not consume active slots.
Only the owner can view, control, collect from, relocate, or retire their workers.

**Overview** supports individual selection, Select all/Clear, and fleet Start,
Pause, Resume, Stop, and Retire. Each result is reported per worker. Opening a card
shows that worker's Job, Inventory, and Settings pages.

## Batch jobs

**Batch jobs** can apply one target set and per-worker quantity to existing workers,
deploy supplied new workers, or **Fill to 10**. Existing workers remain at their
locations. Each new worker needs a selected real tool and the configured material
quantities from the player's inventory. Preview shows requirements and shortages.

At most four destination searches prepare concurrently. Closing the screen keeps the
queue running. Disconnecting or stopping the server cancels unfinished requests and
releases reservations; successful workers remain.

## Relocation

Relocation supports the Overworld and Nether and searches for a safe destination
inside the world border. It pauses the worker immediately. Success preserves identity,
inventory, settings, and progress and leaves the job paused. Failure leaves the worker
at its original location. A pending relocation can be cancelled.

## Retirement and reactivation

Retirement stops active work and archives the worker's identity, job, progress,
settings, inventory, and equipment. Confirmed death uses the same archive path.
Archived workers appear under **Retired**, where contents can be withdrawn.

Reactivation requires an available active slot. It restores the same identity and
remaining contents at a safe destination with health restored; unfinished work
remains paused until explicitly resumed.
