# M5 Minecraft acceptance checklist

Status: **PENDING human testing**. Compilation and dedicated-server checks do not
establish the visual, keyboard or sound results below. The human is the Minecraft
tester; the agent's session has no GPU. Record the candidate commit, GUI scale,
result and any reproduction details when reporting back.

Use a disposable Minecraft **1.21.1 / NeoForge 21.1.249** world with cheats enabled
and both Automatone and Automatone Worker from the same candidate. Install both
mods on the client and dedicated server. For a source checkout on a computer with
a working display/GPU, `./gradlew.bat :worker:runM3Client` loads the current worker
code despite that development run's historical name. Do not mix older mod JARs
with this candidate. Use Java 21 for a normal installed Minecraft profile.

1. **Controller and empty creation.** Craft the controller with iron at the left and
   right of the top two rows, copper at top center, glass pane at center and redstone at bottom
   center. Inspect its icon in the inventory and both hands. It must not stack,
   wear out or bind to a person/worker. Right-click opens your roster. Add a worker,
   choose Overworld, and confirm. Its inventory must contain nine empty slots.
   Open Requests while preparation is pending and verify Cancel works. A cancelled
   creation must not leave an extra worker or consume an active slot.

2. **Roster and layout.** Try GUI scales 2, 3 and Auto, plus a smaller window.
   The wide roster should show up to two rows of five workers; compact views must
   reflow and scroll. Create a second worker, rename it, and verify the cards show
   current dimension, coordinates, targets, progress and state. Tab/Shift-Tab and
   Enter/Space should reach and operate buttons. Search fields must accept typing,
   cursor movement and deletion without losing focus. Escape must close dialogs
   first and warn before discarding unapplied edits.

3. **Real slots and remote control.** Open Inventory and move a pickaxe and a few
   blocks between your inventory and the worker's nine slots, including shift-click
   and hotbar swapping. Select another tool slot and inspect the worker's hand.
   Repeat while the worker is in the other dimension. Counts must be conserved.
   Putting away the last accessible controller must close/reject further control.
   Giving that controller to another player must open that holder's own roster.

4. **Block picker and jobs.** Search `iron`, then `minecraft:deepslate_iron_ore`;
   select several blocks, remove a selected chip, and use Selected-only. If a content
   mod is installed, find one of its registered blocks by translated name and full
   ID and select it together with a vanilla block using the mod filter. Inspect
   names/IDs in tooltips. No content mod is required for ordinary use; the modded
   search observation stays PENDING until actually tested with one.

   To observe remote mining, teleport yourself to the card's coordinates (using
   `/execute in <dimension> run tp @s <x> <y> <z>` when necessary), then place nearby
   accessible requested ores and equip the worker yourself. Request a finite total
   of 3 across the selected target types. Progress must count three destroyed source
   blocks altogether. Pause during a run, wait, and Resume: work must stop while
   paused and retain progress. Stop an unlimited run; it must stop moving/mining.

5. **Settings and unsaved changes.** Open Config, search `allowBreak`, toggle it,
   Apply changes and reopen. Reset should resume native defaults. Open one worker's
   Settings → Worker setting overrides and give it an opposing value; another worker
   must retain its inherited profile. Reset the override and Apply to restore
   inheritance. Search an unavailable client/Java-only setting: it must explain why
   it cannot be edited. Enter an invalid numeric/list value and confirm the error is
   visible. Change a field then navigate away: Cancel keeps the draft; confirming
   discard removes it. Reload is explicit and must also protect an unsaved draft.

6. **Batch confirmation.** Use Copy to… or Batch job to select both workers, including
   a running or paused worker. Check Select all/Clear and the recipient count. Preview
   Apply settings and Apply & start. The dialog must name each recipient and identify
   busy jobs being replaced. Cancel must leave jobs unchanged. Confirm must affect
   targets/quantity only, preserve worker overrides, and show each execution result.

7. **Relocate, retire and reactivate.** Relocate a running worker to Nether. It must
   pause immediately; cancelling/failing preparation must leave it at its original
   location paused. Successful relocation must preserve identity and inventory and
   keep control usable. Retire a worker with items: the confirmation must describe
   archival; it must disappear from the active roster and appear under Retired
   workers. Archived slots allow withdrawal, never insertion or cloning. Reactivate
   it at a safe destination: the same identity and remaining items must return,
   with unfinished work paused and no starter equipment added.

8. **Inbox and independent audio toggles.** Complete separate finite runs for each
   Inbox preference combination: toast+sound, toast only, sound only, neither.
   Expect one visible toast where enabled and one quiet chime where enabled. In
   toast-only mode, both appearance and disappearance must be silent. Unrelated
   vanilla toasts must keep their normal sounds. Every completion must remain in
   Inbox regardless of the preferences, with the correct name, targets, amount and
   time. View details/confirm Mark read, use Mark all read and page through history.

9. **Offline/restart flow.** On a dedicated server, leave a finite worker job running
   and disconnect. After it finishes, reconnect: expect at most one unread-count
   summary, not a replay of all completion toasts. Perform a normal server shutdown
   and restart. Verify settings, paused/running jobs, archives, remaining inventory,
   inbox/read states and toast/sound preferences persist. Reopening/reconnecting or
   reactivating a completed worker must not create another completion for that run.

Report which numbered checks passed and attach the exact action/error for any
failure. In-game observations remain separate from automated gate results; M5 is
not accepted while a required check is still PENDING or UNVERIFIED.
