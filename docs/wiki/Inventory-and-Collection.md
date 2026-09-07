# Inventory and Collection

## Worker inventory

Each worker has 36 persistent inventory slots plus selected equipment state. The
Inventory page transfers real item stacks between the player and worker, preserving
names, enchantments, damage, and other components. Workers receive no free tools or
materials when deployed or reactivated.

Active tools and equipment are protected from bulk collection. Automatone also keeps
up to 64 ordinary cobblestone per active worker as building stock; building can
consume it and pickup rules can replenish it. Excess cobblestone is collectible.

## Global collection

**Collection** searches and aggregates exact item variants across owned workers and
archives. Filter by active/retired scope, dimension, or worker. Hover an item to see
its details and source amounts. Named, enchanted, damaged, and component-bearing
variants remain separate.

Select individual types or use **Select all** for every eligible filtered result,
including results on other pages. **Clear** removes the selection. Unloaded active
workers are marked unavailable rather than represented by stale saved inventory.

Transfers move only what fits in the player's inventory. Every remainder stays at
its original worker or archive. Retired equipment is withdrawable.

## Collect and retire

Retirement during collection is optional and off by default. Its confirmation lists
all active workers in scope, including workers with none of the selected item. On
confirmation, Automatone transfers what fits, then retires those workers and archives
all remaining tools, supplies, selected overflow, and unselected items. A stale or
cancelled confirmation changes nothing.
