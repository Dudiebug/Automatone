# Troubleshooting

## The game or server does not start

- Confirm Minecraft 1.21.1, Java 21, and NeoForge 21.1.249 or a compatible newer
  NeoForge 21.1 build.
- Keep only `automatone-bundled-1.0.jar`. Remove separate or older `automatone` and
  `automatone-worker` JARs.
- Multiplayer needs the same 1.0 bundle on the server and participating clients.
- Seeing both Automatone and Automatone Worker in the Mods menu is normal.

## A worker cannot mine

- **No mineable matching blocks were found:** confirm the selected block and native
  search range; choose a present target or relocate the worker.
- **Could not reach remaining targets:** clear or bridge the route, supply building
  material, adjust supported pathing settings, or relocate the worker.
- **Breaking is disabled:** review that worker's mining settings.
- **Mining was interrupted/native start failed/internal error:** reopen the controller,
  check the worker state, and start a new run when appropriate. Include server-log
  details when reporting an internal failure.

## The controller rejects an action

- **Worker unavailable/not loaded:** wait for the worker to load and refresh.
- **Worker busy:** pause or stop it before changing its job.
- **Stale preview/revision/confirmation:** the roster or supplies changed; refresh,
  create a new preview, and confirm once.
- **Active limit:** retire a worker or wait for a pending reservation to finish.
- **Kit shortage/stale supplies:** restore the required items and preview again.
- **No safe destination/search busy:** wait for pending searches or retry relocation.

## Restart and upgrade behavior

Running jobs resume only after their chunks are ready. Idle, paused, completed,
cancelled, and failed jobs remain stopped. Work does not advance while the server is
stopped. Always test an upgrade on a world copy and retain the pre-upgrade backup.

## Reporting a bug

Open an issue at <https://github.com/Dudiebug/Automatone/issues> and include:

- Minecraft, Java, NeoForge, and Automatone versions
- Singleplayer or dedicated-server setup
- Exact steps and expected versus actual result
- Worker state and the relevant page/action
- Relevant `latest.log` or server-log lines
- A screenshot for layout or rendering problems

The 1.0 in-game acceptance checklist remains pending human testing. A reproducible
report helps convert that uncertainty into a focused fix.
