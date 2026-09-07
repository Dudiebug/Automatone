# Installation and Upgrades

## Requirements

- Minecraft 1.21.1
- Java 21
- NeoForge 21.1.249 or a compatible newer NeoForge 21.1 build
- [`automatone-bundled-1.0.jar`](https://github.com/Dudiebug/Automatone/releases/download/v1.0/automatone-bundled-1.0.jar)

The bundled JAR contains both Automatone and Automatone Worker. Seeing both names
in the Mods menu is expected. Do not install a separate native Automatone JAR.

## Dedicated server and multiplayer

1. Stop the server normally.
2. Back up the world.
3. Remove every older `automatone` and `automatone-worker` JAR from the server's
   `mods` folder.
4. Put `automatone-bundled-1.0.jar` in that folder.
5. Install the same bundled JAR in each participating client's `mods` folder.
6. Start the server and connect with a matching client.

## Singleplayer

Install NeoForge for the correct Minecraft instance, remove older Automatone JARs,
and put the bundled JAR in that instance's `mods` folder. Singleplayer automatically
runs both the client features and the integrated logical server.

## Upgrading an existing world

Automatone retains its mod IDs, registry IDs, controller protocol 5, and v1-v3
worker save support. Test the upgrade on a copy of the world first. Running jobs
resume once their chunks are ready; idle, paused, completed, cancelled, and failed
jobs remain stopped. To roll back, restore the pre-upgrade world backup and previous
matching JARs.

Verify downloads with `SHA256SUMS.txt` from the
[1.0 release](https://github.com/Dudiebug/Automatone/releases/tag/v1.0). If startup
fails, see [Troubleshooting](https://github.com/Dudiebug/Automatone/wiki/Troubleshooting).
