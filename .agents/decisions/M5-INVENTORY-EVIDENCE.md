# M5.9 inventory extension evidence

Contract: `.agents/tasks/M5.9.md`. Baseline: `557bbd01`, including the focused
death/unload roster repair. User-world recovery remains UNVERIFIED; that repair
does not claim to explain every unavailable worker.

## Changes

- Expand active/archive containers to 36 main slots, preserving hotbar 0..8 and
  storage 9..35. Version 3 saves retain the version 1/2 reader. Native Automatone
  still owns tool selection and hotbar swaps.
- Native menu slots place the player on the left and worker on the right; narrow
  screens switch panels without changing server slot identities. Collect all
  uses native stack merging and leaves overflow in the source, including archives.
- Optional per-worker block-list cleanup skips excess unwanted pickups and
  compacts/ejects excess ordinary block stacks only when a pickup needs room.
  Default reserve is 64, disabled initially. Real ItemEntities must be accepted
  before source stacks are removed. Tools/custom stacks and current job outputs
  are protected. Actual target drops, including the completion drop, are learned
  through BlockDropsEvent and persisted with their target set. Cleanup pauses
  while the worker's menu is open. No new loot roll, scanner or movement engine.
- Policy editing reuses the block picker with draft/reload/apply controls and
  server controller/owner/menu/revision/pending validation. Policies do not restart
  jobs. Both artifacts are version 0.11.1; worker network protocol is 2 because
  the native menu layout has changed.

## Checks

- Worker production compilation PASS (`inventory-main-compile.log` and
  `inventory-ui-compile.log` under `.agents/evidence/M5/`). The initial compile
  retained the same three historical Error Prone warnings; no new warning.
- Focused inventory, cleanup, menu, lifecycle and migration GameTests PASS: the
  combined 41-case run passed 39 and exposed two new fixture defects; the affected
  17-case inventory suite then passed in full. Reused passing menu/roster/relocation
  cases are unchanged. Logs: `inventory-focused-runtime-2.log` and
  `inventory-focused-runtime-3.log` under `.agents/evidence/M5/`.
- Controller reviewed the delegated tests and corrected invalid capacity/count
  expectations, ItemStack list comparison, missing imports and mock-player payload
  capabilities. The first combined attempt was interrupted by a missing Notice
  channel in the new player fixture; it is not a passing run. Native storage-tool
  evidence enables the existing `allowInventory` setting, whose native default
  remains false. The drop fixture uses isolated podzol, since grass also exists
  outside the chamber. Native discovery legitimately chose the nearer grass.
  All acceptance assertions remain tied to the requested behavior.
- Compilation of the affected GameTests PASS. The new tests reuse the pinned
  vanilla server-player helper, which emits its existing API deprecation warning.
  Newly introduced Error Prone test warnings were repaired; no suppression added.
- Source plus the passing lifecycle/serialization checks establishes that the
  entire entity NBT, including the tested policy/protection payload, follows
  archives/reactivation and relocation. The isolated restart probe now checks
  slot 35 in addition to the existing hotbar contents; its new run remains PENDING.
- Graphify boundary update completed: 5,813 nodes / 17,162 edges; existing Groovy
  parser warnings remain advisory (`inventory-graph-update.log`). No duplicate
  inventory authority or native mining/scanning engine was introduced.
- Fresh independent extension profile and isolated restart evidence PENDING.
  No duplicate preliminary full profile has been run.
- GPU/client layout and interaction acceptance PENDING; checklist:
  `docs/M5_INVENTORY_MANUAL_ACCEPTANCE.md`.

Controller confirms focused task completion and architectural scope. Independent
extension verdict remains PENDING. M5 remains IN_PROGRESS; M6 has not begun.
