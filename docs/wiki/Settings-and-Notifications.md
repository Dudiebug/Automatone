# Settings and Notifications

## Settings inheritance

Automatone applies settings in this order:

1. Native Automatone defaults
2. The owner's personal configuration
3. Per-worker overrides

Resetting an override returns that value to inheritance. Search and categories help
find native settings; only settings supported by the server-worker runtime are
editable. Applying settings to a running worker replans its native work without
losing finite progress. Paused work remains paused.

## Pickup rules

Personal defaults and per-worker overrides decide which nearby items workers retain
or ignore. Wanted resources, configured job outputs, tools, and component-bearing
stacks are protected. Ordinary cobblestone is retained up to the 64-item building
reserve; other configured unwanted pickups can remain on the ground. Changing a rule
does not automatically eject existing inventory.

## Notifications

Completion alerts can use an in-game notification, toast, and sound according to the
owner's preferences. Notification history and read state persist across reconnects
and restarts. A completed run produces one completion entry; work completed while
the owner is offline appears as an unread summary after login.
