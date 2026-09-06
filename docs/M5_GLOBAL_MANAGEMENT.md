# Global Worker Management Update

Human-approved plan, 2026-09-06. Authority: the explicit request to implement
this plan. Extends the accepted inventory candidate `7cce1ded` and supersedes
conflicting M5 death-deletion, cleanup and single-deployment behavior. Preserve
the ten-active-worker limit, 36-slot inventories, ownership, server authority,
and Automatone ownership of scanning, pathfinding, movement and mining.

## Global interface

Use the existing Minecraft-style appearance with five main pages:

- Overview: cards with dimension, job, progress and problems; individual/all
  selection; bulk Start, Pause, Resume, Stop and Retire. Report eligibility and
  per-worker results. Confirm replacing running jobs and retirement.
- Batch jobs: targets, per-worker quantity, existing-worker selection,
  new-worker count, destination and equipment in one flow.
- Collection: searchable item icons, counts, selection and transfers.
- Retired: archives, withdrawal and reactivation.
- Settings: personal defaults, pickup rules and worker overrides.

Cards open individual details. Preserve drafts/selections during live updates.
Compact screens reflow and scroll without hiding controls.

## Batch deployment and jobs

Choose targets -> select existing workers / Fill to 10 -> configure supplies ->
Deploy & start. Fill to 10 selects existing active workers and fills remaining
capacity, including pending reservations. Quantity is per worker; show the
combined total and support Unlimited. Existing workers stay in their current
locations. New workers use existing safe random Overworld/Nether deployment.
Deploy only creates equipped workers without starting mining.

The per-new-worker kit selects real tools/material quantities from player
inventory, with 64 cobblestone as the default material entry. Preview actual
tool stacks, required/available totals and shortages. Preserve all components;
never generate supplies. Block submission for shortages. Confirm busy-job
replacement once. Show queued/preparing/running/failed/cancelled results inline.

Reserve all requested roster slots before beginning. Queue preparation through
the existing four-concurrent service. Transfer each kit only during successful
server-side deployment commit. Closing the screen does not cancel. Disconnect
or shutdown cancels unfinished deployments and releases reservations; unused
supplies stay with the player. Successful workers survive other failures. A
failure after transferring equipment retires that worker with its contents.

## Collection

Default to all owned active workers across dimensions. Include worker/dimension
filters and access to archives. Show item icons and aggregate counts without a
table or permanent names. Hover shows name, components and per-worker amounts.
Keep item/component variants separate. Search names and registry IDs; select
multiple types. Select all includes every eligible filtered result, including
offscreen entries; Clear clears selection. Transfer selected merges into the
player inventory and leaves overflow at its source. Protect active tools,
equipment and the 64-cobblestone reserve. Archives remain withdrawal-accessible.
Unavailable active workers are explicit, never served from stale saved copies.

Retire workers after collection is optional and off by default. Confirm all
active workers in the collection scope and that mining stops. Transfer first,
then retire those workers; archive every remainder, including selected overflow.
Invalid/stale requests perform neither action. Limited capacity is supported
partial collection and reports the preserved remainder.

## Pickup rules

Replace keeping 64 of each unwanted type with 64 ordinary cobblestone as working
stock. Building may consume it; pickups replenish it. Bulk collection protects
the reserve. Ignore other unwanted pickups. Protect wanted resources, explicit
job outputs, tools and component-bearing stacks. Cobblestone job outputs may
exceed the reserve and the excess is collectible. Use the existing common-block
list as default, editable globally with per-worker overrides. Migrate existing
and new workers; preserve custom lists and inventory contents. Remove generic
retained-count controls and routine automatic ejection.

## Death retirement

Confirmed death archives owner/identity/name/settings/job progress/inventory and
equipment, pauses unfinished jobs, cancels native work, removes the live entity
and releases tickets. Archive before death drops can lose/duplicate contents.
Cancelled death remains active. Repeated callbacks are harmless. Reactivation
restores the same identity and remaining contents at a safe destination with
restored health and cleared death/fire state; unfinished jobs remain paused and
no equipment is granted. Recover legacy dead records as archives where their
data still exists. Absent deleted records cannot be reconstructed.

## Placement and server boundary

Replace unconditional placement failure with Minecraft block-item placement
using the worker hand and validated hit. Validate server thread, reach,
dimension, bounds, collision, permission and NeoForge cancellation hooks.
Consume exactly one item only on success; rejected placement preserves world
and inventory. Normalize server interaction success for native Automatone.
Support horizontal bridges and vertical pillars without fake players or a
second native engine.

Extend bounded controller intents/snapshots for fleet actions, queued batch
deployment/kits, collection and collection-with-retirement. Use server identities,
revisions and request tokens; client inventory contents are not authoritative.
Validate before server-thread mutation. Repeats cannot duplicate work/items.
Keep player/menu access in the established boundary and reject incompatible
protocol versions clearly.

## Execution and acceptance

One extension, in dependency order:

1. M5.10 Death retirement, recovery and ticket cleanup.
2. M5.11 Placement and pickup rules.
3. M5.12 Global collection and collect-and-retire.
4. M5.13 Fleet UI, queued batches and kits.
5. M5.14 Integration, visual acceptance and packaging.

Focused real GameTests cover death with all 36 slots and equipment, cancellation,
idempotence, archive withdrawal/reactivation/restart; native bridge/pillar
placement, exact consumption, exhausted/blocked/cancelled placement; reserve
pickup/use/refill, unwanted rejection and protected outputs; cross-dimension
and archive collection, variants, full/partial capacity, stale/non-owner/repeat
requests, retirement remainders; ten-worker bounded queue, mixed batches, kit
shortages and changing inventory, failures/cancellation/disconnect/shutdown,
per-worker quantities and busy confirmation. GUI acceptance covers normal and
compact sizes, hover/selection, transfers, setup and progress.

Reuse tooling. Astra owns implementation, test contracts and integration; use
bounded Luna test assignments for substantial tests. One fresh independent
clean-candidate gate supplies the applicable runtime, persistence, architecture
and static-analysis profile once. Repair and rerun affected checks. No duplicate
preliminary full profile or unrelated dependency/security profile. One concise
evidence record: `.agents/decisions/M5-GLOBAL-MANAGEMENT-EVIDENCE.md`. Record manual
GUI evidence honestly. Produce matching versioned JARs, checksums and user guide.
