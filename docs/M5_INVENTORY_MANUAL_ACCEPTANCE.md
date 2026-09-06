# M5 inventory update — client checks

Use matching Automatone and Automatone Worker **0.11.1** JARs on client and server,
with Minecraft 1.21.1 and NeoForge 21.1.249. Close Minecraft/server before replacing
the two older JARs. Inventory protocol 2 requires both sides to update together.
Existing worker items migrate into a 36-slot inventory; new slots start empty.

Automated server evidence is recorded in
`.agents/decisions/M5-INVENTORY-EVIDENCE.md`. These GPU checks remain PENDING until
performed on a real client.

- Open an existing worker's Inventory page. At normal width, verify your three
  storage rows and hotbar appear on the left and the worker's on the right.
  Its old hotbar contents should be unchanged. Number buttons under the worker
  hotbar select the held tool.
  Automatone's existing `allowInventory` setting enables its automatic tool moves
  from storage to the hotbar; its default is unchanged.
- Increase GUI scale or narrow the window. Use Your inventory/Worker inventory
  tabs to reach all 36 slots in either panel. Resize back to the wide view.
  Check tooltips, cursor stack, shift-click and hotbar swap on both layouts.
- Put distinct items in worker slots in the first and last storage rows. Use
  Collect all with room in your inventory, then with a nearly full inventory.
  Only what fits should move; the rest stays with the worker. Repeat on a retired
  worker and verify its inventory remains withdrawal-only.
- Open Automatic cleanup. Hover the Cleanup toggle for behavior details. Select
  unwanted blocks with search/mod/selected filters, choose a Keep count (default
  64 per listed item), turn cleanup on and Apply. Leave and reopen the page to
  verify the saved settings. Cancel an unsaved edit and verify the discard prompt.
- Close the worker menu, fill the worker with excess listed blocks, then let it
  collect a wanted item. Excess listed stacks should appear as drops nearby and
  the wanted item should enter the inventory. A listed current mining target and
  its mined output must be retained. Tools and renamed/custom stacks stay safe.
- Restart the world and check full inventory, selected tool and cleanup settings
  again. The policy also follows a worker through retirement/reactivation and
  relocation.

The earlier unavailable-worker repair handles stale records from committed death
and chunk unload. If the reported worker remains unavailable after updating, retain
its displayed dimension/coordinates and the server log for diagnosis; successful
recovery of that particular world has not been established by automated tests.
