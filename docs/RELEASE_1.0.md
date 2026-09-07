# Automatone 1.0

Automatone 1.0 is the first complete NeoForge server-worker release. It adds
autonomous mining workers that players equip, configure, and manage through a
server-authoritative controller.

One installable file, `automatone-bundled-1.0.jar`, contains both the native
Automatone engine and Automatone Worker. The same file runs on dedicated servers,
participating multiplayer clients, and singleplayer. Both internal mods still
appear in the Mods menu.

## Requirements and installation

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.249 or a compatible newer NeoForge 21.1 build

Remove all older `automatone` and `automatone-worker` JARs, then install only
`automatone-bundled-1.0.jar`. Multiplayer requires the bundled JAR on the server
and participating clients. Singleplayer needs it only in that installation.

Back up an existing world before upgrading. Automatone retains its existing mod and
registry IDs, controller protocol 5, and v1-v3 worker save compatibility. Test an
upgrade on a copy of the world before replacing a production server. To roll back,
restore that backup and the previous matching JARs.

## Mining and job control

- Workers use Automatone's native target discovery, pathfinding, movement, tool
  selection, block breaking, and placement behavior.
- A job can target one or many registered blocks. Search by translated name or
  registry ID and select up to 128 target types.
- Finite jobs count successfully destroyed matching source blocks across all selected
  targets. Choose from 1 to 1,000,000 blocks, or select **Unlimited**.
- **Start** begins a new run, **Pause** retains its progress, **Resume** continues
  the remaining work, and **Stop** cancels the run without erasing completed progress.
- Workers use the real tools and building materials supplied by the player. Successful
  placement consumes material normally; pickups can replenish configured supplies.
- Clear terminal messages distinguish no targets, unreachable paths, disabled
  breaking, interruption, cancellation, completion, and unexpected internal failure.

## Controller, workers, and fleets

- The reusable Automatone Controller opens the current holder's personal roster. It
  is crafted with four iron ingots, one copper ingot, one glass pane, and redstone.
- Each player may have up to ten active or reserved workers. Retired workers do not
  consume active slots.
- **Overview** shows each worker's name, dimension, location, targets, progress,
  state, and current problem, with individual or fleet controls.
- **Batch jobs** applies a shared target and per-worker quantity to existing workers,
  deploys supplied new workers, or fills remaining roster capacity. New workers
  receive only the actual tools and materials selected from the player's inventory.
- Destination preparation is bounded to four concurrent searches. Closing the menu
  lets a batch continue; disconnect or server shutdown cancels unfinished deployments
  and leaves unused supplies with the player.
- Workers can be managed across the Overworld and Nether. Relocation finds a safe
  destination and leaves the job paused while preserving identity and contents.

## Inventory, collection, and recovery

- Every worker has 36 persistent inventory slots plus selected tool/equipment state.
- The searchable **Collection** view groups exact item variants across owned active
  workers and archives. Filters cover worker, dimension, and active/retired scope.
- Transfers move only what fits in the player's inventory; every overflow item remains
  at its source. Named, enchanted, damaged, or component-bearing variants stay distinct.
- Active tools, equipment, and a reserve of 64 ordinary cobblestone are protected
  from bulk collection. Excess cobblestone remains collectible.
- Retirement archives identity, job, settings, inventory, and equipment. Death also
  archives recoverable contents instead of duplicating or dropping them twice.
- Archived inventory can be withdrawn. Reactivation restores the same worker at a safe
  destination with its remaining contents; unfinished work returns paused.
- Optional collect-and-retire transfers what fits first and archives every remainder.

## Settings and notifications

- Personal configuration supplies defaults for new and existing workers.
- Per-worker overrides take precedence; resetting an override returns to inheritance.
- Searchable native settings expose only controls supported by this server-worker path.
  Applying settings replans running work without losing finite progress.
- Pickup rules retain wanted resources, job outputs, tools, component-bearing items,
  and the cobblestone building reserve while ignoring configured unwanted pickups.
- Completion notifications persist for offline owners. Read state and preferences
  survive reconnects and restarts, and one completed run produces one notification.

## Persistence and reliability

- Worker UUID, owner, name, dimension/location, inventory/equipment, settings, job,
  run ID, progress, archives, and notification state persist in world saves.
- A running job resumes after restart when its chunks are ready. Idle, paused,
  completed, cancelled, and failed jobs stay stopped. There is no offline catch-up:
  work advances only while the server is running.
- Workers keep their required chunks active within the ten-worker roster limit, so
  owner-offline work can continue while the server itself is ticking.
- Server-side ownership and revision checks reject foreign, stale, forged, repeated,
  or expired controller requests before mutation.
- Runtime reload replaces old native state, and generation guards prevent stale
  asynchronous scan or path results from changing a newer job.
- Unexpected native scan/start failures are logged server-side while readable status
  remains available in the controller.

## What changed since preview builds

- The two matching preview JARs are now one universal 1.0 JAR with automatic client,
  dedicated-server, and integrated-server behavior.
- Native mining reports typed completion and failure reasons instead of collapsing
  unexpected stops into a generic status.
- Saved-data reload now disposes the old native runtime before attaching a fresh one.
- Stale or foreign entity-removal callbacks can no longer overwrite another worker's
  authoritative roster record.
- Final packaging verifies both mod identities and versions, the exact embedded native
  artifact, required assets and mixins, and the absence of test classes or unintended
  bundled dependencies.

## Documentation and support

Start with the [Automatone wiki](https://github.com/Dudiebug/Automatone/wiki) for
installation, the first-worker walkthrough, detailed controls, and troubleshooting.
The attached `M6_RELEASE_GUIDE.md` contains the human runtime checklist, and
`SHA256SUMS.txt` verifies the release downloads.

Report issues at <https://github.com/Dudiebug/Automatone/issues>. Include the exact
Minecraft, NeoForge, Java, and Automatone versions; reproduction steps; expected and
actual results; and relevant `latest.log` lines.

## Verification and current limitations

Compilation, 103 unit tests, 9 architecture tests, and universal-package integrity
checks passed for the released commit. The archive checks confirmed both internal
mod versions, exact native dependency and bytes, assets, mixins, and absence of test
classes and unintended bundled dependencies.

Dedicated-server installation, multiplayer, singleplayer, existing-world upgrades,
GameTests, and restart probes remain **PENDING — HUMAN TESTING**. This official 1.0
release and its Latest designation do not mark those runtime checks as passed.

Automatone 1.0 supports Minecraft 1.21.1/NeoForge 21.1 only. It has no automatic retry
for terminal mining failures, no offline progress while the server is stopped, and no
guarantee of transactional recovery after an arbitrary process crash. Normal shutdown
and world backups remain required for safe upgrades.
