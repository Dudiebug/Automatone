# Automatone 1.0

One installable JAR now contains Automatone and Automatone Worker for dedicated
servers, multiplayer clients and singleplayer. Client screens/rendering and
server mining activate automatically; both internal names remain in the Mods menu.

Requires **Minecraft 1.21.1, Java 21, and NeoForge 21.1.249** or a compatible newer
21.1 build. Download `automatone-bundled-1.0.jar` below. Install this same file on
the server and participating clients, or in your singleplayer mods folder.

## Included

- Native worker pathfinding and finite or unlimited multi-target mining, with
  server-owned controls and readable failure messages.
- Controller GUI, up to ten active workers per owner, 36-slot inventories,
  equipment, batch deployment, settings and cross-dimension management.
- Collection, worker archives/reactivation, relocation and completion notifications.
- Persistence safeguards for worker ownership, runtime reload and stale mining results.

## Upgrade

Close the world/server normally and back up the world. Remove both previous
Automatone JARs, including any older bundle, and install only
`automatone-bundled-1.0.jar`. Do not install a separate native JAR alongside it.
Existing mod/registry IDs, v1–v3 worker saves and controller protocol 5 are retained.
Upgrade testing should use a copy of your existing world.

The attached `M6_RELEASE_GUIDE.md` contains installation and numbered expected-result
checks; `M5_GLOBAL_MANAGEMENT_USER_GUIDE.md` explains the controls. Verify downloads
against the attached `SHA256SUMS.txt`.

## Verification

Compilation, 103 existing unit tests and 9 architecture tests passed. Archive checks
passed for both mod versions, exact native dependency, embedded native bytes,
assets, mixins and absence of test classes or unintended bundled dependencies.

Dedicated-server installation, multiplayer, singleplayer and existing-world runtime
checks remain **PENDING — HUMAN TESTING**. Publication as 1.0 does not mark those
checks or M6 gameplay acceptance as passed.
