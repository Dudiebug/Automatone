# NeoForge 1.21.1 port status

Current release: **Automatone 1.0**, one universal JAR containing the native engine
and worker module. See the [release guide](M6_RELEASE_GUIDE.md) for installation,
features and human testing. The sections below preserve the historical foundation
snapshot and do not describe the current implementation or verification status.

Last updated: 2026-09-04
Branch: `plan/neoforge-1.21.1-server-worker`

## Can I install this on a server?

Not as a usable worker mod yet. The current branch is a tested server-side Automatone library foundation. It
loads in the NeoForge development server, but it does not provide a spawnable worker or player-facing way to
start navigation and mining. No release JAR from this branch has been packaged and tested as a normal server
installation.

The historical Fabric installation instructions and Cardinal Components examples in the main README do not
apply to this branch.

## Implemented

- Minecraft 1.21.1, NeoForge 21.1.249, ModDevGradle 2.0.144, and Java 21 build configuration.
- NeoForge mod metadata and a server entry point that ticks and disposes Automatone runtimes.
- Explicit provider/runtime/world ownership without Fabric, Quilt, or Cardinal Components on the server path.
- Native pathing, scanning, cache, cancellation, and `MineProcess` availability.
- Server-safe settings callbacks and dedicated-server construction.
- Removal of the retained server-core mixin/accessor layer; the current server path uses public 1.21.1 APIs.
- Focused fixes for stale asynchronous results, value ownership, VarInt serialization, resource closure,
  collision context, command handling, and duplicated implementation.

## Verification snapshot (2026-09-02)

The final independent run used:

```powershell
pwsh -NoProfile -File .agents/evidence/quality-cleanup-20260902/ENTRYPOINT.ps1 -Final
```

That entry point and its raw results were local evidence artifacts. From a fresh clone, run the committed
closed-loop verification controller with:

```powershell
pwsh -NoProfile -File scripts/workflow/Invoke-AutomatoneVerification.ps1 -TaskId QUALITY-CLEANUP -Profile bootstrap
```

Measured results:

- 67 unit tests passed.
- 4 architecture tests passed.
- 13 dedicated-server GameTests passed.
- Checkstyle, compilation, Error Prone, and configured CPD checks passed.
- Reported CPD regions fell from 7 to 0 in the configured source sets.
- SpotBugs findings fell from 129 to 84, with no new unmatched findings.
- The overall required workflow still failed because SpotBugs treats the 84 retained findings as blocking.

The retained findings include intentional live-object/public API contracts, compatibility surfaces, legacy
optional-integration stubs, and four unresolved pathing-thread warnings. They have not been suppressed or
waived. Full stale-publication interleaving coverage, positive multi-chunk scanner parity, and exhaustive
movement-world parity also remain unverified.

## Not implemented

- A registered, spawnable non-player worker entity and its inventory host.
- Per-worker runtime attachment and cleanup.
- Reliable worker movement, jumping, looking, and conflict handling with vanilla AI controls.
- Complete direct block-breaking, tool/durability, and normal drop behavior for the worker.
- Player authorization, commands/networking, persistence, status, and product-facing UX.
- A release JAR validated by installing it into a normal dedicated-server `mods` directory.

## Next step

Start **M2.1 — Create the minimal worker and inventory host**. M2.2 then attaches one Automatone runtime to
that worker, M2.3 makes direct movement reliable, and M2.4 verifies navigation on the dedicated server. Direct
worker mining and later user-facing features follow in subsequent milestones.

## Repository evidence policy

Source code, tests, workflow configuration, task specifications, and this status summary belong in Git. Raw
baseline source copies, generated Graphify output, build products, test worlds, and local verification logs are
excluded because they duplicate repository content or are machine-generated. The concise measured results
above preserve the handoff without turning the repository into an artifact archive.
