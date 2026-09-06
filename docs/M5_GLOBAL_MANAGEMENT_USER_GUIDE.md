# Automatone 0.12.0 — global management

Install **both** `automatone-0.12.0.jar` and `automatone-worker-0.12.0.jar` on the
server and participating clients, replacing both older JARs. Requires Minecraft
1.21.1 and NeoForge 21.1.249 or newer compatible 21.1 builds. Controller protocol
4 deliberately rejects older clients. Keep only one version of each mod.

## Using the controller

**Overview** shows active workers and their jobs/problems. Tick the small box
beside a card to select it; click the card to open details. Select all/Clear and
Start/Pause/Resume/Stop/Retire operate on that selection. Start uses each worker's
stored job. Retire archives all remaining inventory and equipment.

**Batch jobs** has Targets, Workers, Supplies and Progress steps. Quantity is per
worker; Unlimited has no quota. Fill to 10 selects all active workers and fills
free capacity, including pending reservations. Existing workers keep their
locations. New workers use a safe random Overworld or Nether destination.

Select one actual tool stack per new worker. Materials are quantities per worker;
the default is 64 ordinary cobblestone. Select a material icon, enter its quantity
and press Set. Remove/Clear changes the material selection. Preview shows the
actual tools/components and required/available totals. A shortage prevents
submission. Deploy only equips/configures; Deploy & start also starts the jobs.
Preparing runs at most four workers at once. Closing the screen continues the
queue. Disconnecting or stopping the server cancels unfinished deployments and
leaves unused supplies with the player. Successful workers remain. A failure
after equipment transfer retires that worker with its contents.

**Collection** groups exact item/component variants across owned workers and
archives. Filter by active/retired/both, dimension and worker; search by name or
registry ID. Hover icons for details and source amounts. Select all includes all
filtered pages. Transfer moves only what fits and keeps every remainder at its
source. Active tools/equipment and 64 ordinary cobblestone are protected;
archives are fully withdrawable. Unloaded active workers are unavailable until
they are live again. Optional retirement is off by default: confirmation covers
all active workers in the current scope, then transfers first and archives all
remainders, including overflow.

**Retired** provides archived inventory, collection and safe reactivation with
the same identity. Confirmed death now retires a worker instead of deleting its
record or dropping the archived inventory. Reactivation restores health and
clears death/fire state; unfinished jobs remain paused.

**Settings** contains personal defaults, pickup rules and alert preferences.
Individual details contain worker overrides. Ordinary cobblestone replenishes
up to 64 as building stock; other ignored blocks are not picked up. Job outputs,
tools and component-bearing stacks remain protected. Existing contents are not
automatically ejected. Native pathfinding/mining still belongs to Automatone.

## In-game test checklist — performed by you

Use a test world with the matching JAR pair. Report each item as PASS/FAIL with
what happened; these checks are pending until you run them.

1. **Navigation and compact layout:** open all five pages at your normal GUI
   scale, then a compact window/large GUI scale. Scroll longer lists. Every
   button, field and footer remains accessible; drafts and selections survive
   live updates. Hover item icons and truncated text for details.
2. **Ten-worker batch:** supply ten tools and 640 ordinary cobblestone, choose
   a target/quantity, then Fill to 10 and Deploy only. Preview needs ten tools
   and 640 cobblestone. Capacity includes pending workers; at most four prepare
   together. Ten equipped workers appear idle with the chosen per-worker quota.
3. **Mixed jobs and busy confirmation:** select some existing workers and add
   new ones with enough supplies. Deploy & start applies the same targets and
   quantity to each worker, leaves existing workers in place and confirms busy
   job replacement once. Check finite completion and Unlimited separately.
4. **Kits and failures:** try too few tools/materials, then enough named,
   enchanted or damaged tools. Shortages block submission; components survive
   deployment and supplies are consumed once. Change inventory after preview
   and retry: stale confirmation must not deploy or consume anything.
5. **Queue lifetime:** close the controller during preparation and reopen it;
   progress continues. Start another batch and disconnect before all workers
   finish. Reconnect: completed workers remain, unfinished workers are cancelled,
   unused supplies remain, and roster capacity is released.
6. **Fleet controls:** select individual cards and all cards. Start, Pause,
   Resume and Stop update eligible workers and report individual failures.
   Confirm retirement of selected workers; their contents remain in Retired.
7. **Collection and variants:** use workers in both dimensions plus an archive.
   Add ordinary and renamed/enchanted variants of an item. Filters/search and
   hover source counts are correct; variants stay separate. Select all across
   multiple pages, then Clear. Unavailable active workers are clearly marked.
8. **Partial collection:** nearly fill your inventory, select resources and
   transfer. Only the fitting amount moves; no remainder disappears. Active
   tools/equipment and 64 ordinary cobblestone stay with each worker; excess
   cobblestone is collectible. Retired equipment can be withdrawn.
9. **Collection with retirement:** check the optional retirement box and read
   the exact worker preview. Include an empty worker and use limited player
   capacity. Confirmation stops/retires every active worker in scope, transfers
   what fits and preserves all overflow in archives. Cancel changes nothing.
10. **Pickup defaults and overrides:** drop cobblestone near a worker below
    64, then above 64; it replenishes the reserve. Other ignored common blocks
    stay on the ground. Wanted resources/component-bearing stacks are retained.
    Change global rules and one worker override; inheritance/reset behaves as
    shown, and existing inventory contents are never ejected automatically.
11. **Building and death recovery:** supply cobblestone and enable native
    placement settings as needed; try a mining route needing a bridge or pillar.
    Successful placements consume stock and pickups refill it. Kill a worker
    holding resources/tools: one archive keeps its identity, progress and items.
    Reactivate it; health is restored, contents survive and its old job is paused.
12. **Restart and ownership:** save/restart with active and retired workers and
    an unfinished queue. Verify inventories, settings, archive identity and job
    progress survive; unfinished deployment reservations are released. With a
    second player, confirm each controller sees/controls/collects only its own
    workers and archives.

For a failure, include the checklist number, expected/actual result, mod versions
and relevant `latest.log` lines. A screenshot is useful for a layout problem.
