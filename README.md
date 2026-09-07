# Automatone

[![Latest release](https://img.shields.io/github/v/release/Dudiebug/Automatone?label=latest)](https://github.com/Dudiebug/Automatone/releases/latest)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-62b47a)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.249%2B-ea5c32)](https://neoforged.net/)
[![License](https://img.shields.io/badge/license-LGPL--3.0-green.svg)](LICENSE)

Automatone adds autonomous mining workers to Minecraft. Give each worker tools and
supplies, choose one or more blocks, and manage mining jobs from a server-authoritative
controller. It works on dedicated servers, multiplayer clients, and singleplayer.

## Install Automatone 1.0

Automatone 1.0 requires **Minecraft 1.21.1**, **Java 21**, and **NeoForge 21.1.249**
or a compatible newer NeoForge 21.1 build.

1. Download [`automatone-bundled-1.0.jar`](https://github.com/Dudiebug/Automatone/releases/download/v1.0/automatone-bundled-1.0.jar).
2. Remove older `automatone` and `automatone-worker` JARs from the `mods` folder.
3. Put the bundled JAR in the server and participating clients' `mods` folders, or
   in the singleplayer installation's `mods` folder.

The universal JAR contains both Automatone's native pathfinding engine and the worker
mod. The correct client and logical-server features activate automatically.

## Create your first worker

1. Craft an **Automatone Controller** with four iron ingots, one copper ingot, one
   glass pane, and one redstone dust.
2. Hold and use the controller to open your personal worker roster.
3. Open **Batch jobs**, choose target blocks and a finite quantity or **Unlimited**.
4. Add a worker, select a real tool and supplies from your inventory, then review
   the preview and choose **Deploy & start**.
5. Use **Overview** to monitor progress or Start, Pause, Resume, Stop, and retire workers.

## Features

- Native pathfinding and autonomous multi-target mining
- Finite source-block quotas up to 1,000,000, or unlimited jobs
- Up to ten active workers per player with 36 inventory slots each
- Fleet controls, supplied batch deployment, and Overworld/Nether relocation
- Searchable collection across workers and archives with safe overflow handling
- Retirement, death recovery, inventory withdrawal, and reactivation
- Personal settings, per-worker overrides, pickup rules, and notifications
- Persistent worker identity, inventory, job state, progress, and ownership

Read the [wiki](https://github.com/Dudiebug/Automatone/wiki) for installation,
walkthroughs, controls, and troubleshooting. The
[1.0 release notes](https://github.com/Dudiebug/Automatone/releases/tag/v1.0)
contain the complete feature overview.

## Support and project status

Report problems through [GitHub Issues](https://github.com/Dudiebug/Automatone/issues).
Include Minecraft, NeoForge and Automatone versions, the relevant `latest.log` lines,
and steps to reproduce the problem.

The 1.0 build passed 103 unit tests, 9 architecture tests, and package-integrity
checks. In-game installation and gameplay acceptance remain **pending human testing**;
see the [release guide](docs/M6_RELEASE_GUIDE.md) for the numbered checklist.

Automatone is based on [Baritone](https://github.com/cabaletta/baritone) and the
original [Ladysnake Automatone](https://github.com/Ladysnake/Automatone). Historical
Fabric-era code and documentation remain available on the repository's older
[branches](https://github.com/Dudiebug/Automatone/branches) and
[tags](https://github.com/Dudiebug/Automatone/tags).

Licensed under the [GNU Lesser General Public License v3.0](LICENSE). See the
[Code of Conduct](CODE_OF_CONDUCT.md) for community participation.
