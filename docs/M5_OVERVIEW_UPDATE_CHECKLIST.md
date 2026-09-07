# Automatone 0.12.1 — overview and Collection fixes

Replace **both** mod JARs on the server and participating clients. Minecraft
1.21.1 / NeoForge 21.1.249 or newer compatible 21.1 builds. Controller protocol 5
requires the matching update. Keep only one version of each mod.

Overview now has **Relocate** for checked workers and a draggable scrollbar with
**Showing first–last of total**. Choose Overworld or Nether, review the selected
workers and confirm. Eligible workers pause immediately and queue through the
existing shared limit of four preparations. Review results under **Batch jobs →
Progress**; use Resume after relocation. Closing the controller continues the
queue; disconnecting or stopping the server cancels unfinished work.

Collection clicks and ordinary transfers now tolerate item-count updates from
working miners. Selection stays green and transfer uses the current live amounts.
Full inventory remainders and working supplies stay protected. Changing the query
still invalidates old requests, and collect-and-retire still requires an unchanged
confirmation; refresh its preview if contents changed.

## In-game checks — performed by you

1. **Scrolling:** with nine or ten workers, use the wheel, drag the scrollbar and
   click its track. Reach every worker; the visible range changes correctly.
   Select workers on different rows and confirm selections survive scrolling.
   Repeat at compact GUI size and after removing a worker near the list's end.
2. **Relocation:** select more than four workers with tools and resources. Click
   Relocate, choose a dimension and confirm. All eligible workers pause; at most
   four prepare together and the rest remain queued. Every completed worker keeps
   its identity, inventory, equipment and job progress; Resume starts it again.
3. **Queue controls:** close and reopen during preparation. Progress continues.
   Cancel a queued/preparing worker; its contents remain. Try relocating or
   resuming a worker already queued: it must not queue twice or resume prematurely.
4. **Disconnect and restart:** disconnect or stop the server before a queue
   finishes. Completed relocations remain; unfinished work is cancelled and
   existing workers/items survive. A failed destination does not undo siblings.
5. **Collection while mining:** leave workers actively mining, click several item
   icons and verify green selection. Transfer selected items into free inventory
   slots while counts change. The player receives items without STALE_COLLECTION;
   only the fitting amount moves and all remainder stays with workers.
6. **Collection protection:** test a nearly full inventory, ordinary/named variants
   and working cobblestone/tools. Transfers preserve exact variants, protected
   supplies and overflow. For collect-and-retire, changed contents must still
   reject an old confirmation without moving items or retiring workers.

Report the check number, expected/actual result and the bottom status message.
These results remain **PENDING — HUMAN TESTING** until you run them.
