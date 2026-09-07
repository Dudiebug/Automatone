# Automatone 0.13.0 — final reliability release

For Minecraft 1.21.1, Java 21, and NeoForge 21.1.249 or a compatible newer 21.1
build. Install both `automatone-0.13.0.jar` and `automatone-worker-0.13.0.jar` on
the server and participating clients. The controller continues to use protocol 5.
Use the matching pair of JARs; the worker requires exactly native version 0.13.0.

## Upgrade

Stop the server normally, copy the world to a backup, and replace both previous
Automatone JARs. Keep only one version of each mod in the mods folder. Start the
server normally. Worker saves v1–v3 remain supported; this release does not change
the save format. Running jobs resume after their chunks become ready. Paused,
idle and terminal jobs remain stopped. Upgrade testing should use a copy of your
0.12.1 world. To roll back, restore that backup and the previous matching JARs.

Your existing controller, ten-worker limit, 36-slot inventories, global collection,
archives, kits, relocation, settings and notifications remain available. See the
included M5 global-management guide for those controls; its older installation
version/protocol paragraph is superseded by this guide.

## What changed

- Native mining failures now identify missing mineable targets, unreachable paths,
  disabled breaking, interruptions and internal errors. Screens show readable
  messages; unexpected scan/start errors include details in the server log.
- A stale or foreign entity's removal callback cannot overwrite/delete another
  worker's authoritative roster record.
- Loading saved data into an attached worker disposes its old native runtime and
  attaches a fresh one, retaining product state and progress.

Finite quantities still count successful requested source-block destruction,
combined across selected targets per worker. Drops, pickups and inventory contents
do not count. Start begins a new run; Pause/Resume retains progress; Stop cancels.
No work occurs while the server is stopped. No automatic retry is added.

## Numbered in-game acceptance checklist

All steps below are **PENDING — HUMAN TESTING**, using the matching 0.13.0 JARs.
Record each result and the build/checksums when reporting problems.

1. **Upgrade existing data.** Open the controller in your copied 0.12.1 world.
   Expected: correct owner roster, worker UUIDs/names/locations, personal settings,
   overrides, archived workers, read/unread notifications and preferences remain.
   Transfer the controller to another player: it opens that player's own roster.
2. **Check inventory.** Compare all 36 slots, selected hotbar slot, equipment and
   named/component-bearing items on active and archived workers before/after the
   upgrade. Expected: exact contents and counts; no granted replacement equipment.
3. **Resume finite work after restart.** Start a multi-target finite job with more
   available targets than requested; note a nonzero completed count and restart
   normally before completion. Expected: the same job/run continues from that
   count, stops exactly at the original total, and destroys no extra target later.
4. **Check other saved states.** Restart with separate unlimited RUNNING, PAUSED,
   configured IDLE, COMPLETED, CANCELLED and FAILED jobs. Expected: only RUNNING
   resumes; unlimited advances, and all other jobs retain progress/state. Resume
   the paused job explicitly: it continues its remaining amount.
5. **Test Pause and Stop.** Pause while mining, wait, then Resume. Stop during a
   later run and repeat Stop. Expected: no further destruction while paused or
   cancelled; completed progress remains. Pause/Stop never turn into a failure.
6. **Read failure messages.** Disable exploration and request an absent target;
   try a target beyond an impassable route; disable breaking for the selected
   blocks. Expected: useful NO_TARGETS, PATH_FAILED, and BREAK_DISABLED messages.
   A block listed in allowBreakAnyway remains mineable with allowBreak disabled.
   Terminal errors stay visible after reopening the controller/restarting.
7. **Check authorization.** Use a second player's controller and attempt to
   access/control/collect/relocate/withdraw from the first player's workers.
   Expected: no foreign workers or inventory become controllable. Confirm an old
   preview after its workers have changed: reject it without unintended mutation.
8. **Exercise archives.** Retire a worker with inventory, withdraw part, restart,
   and reactivate it. Repeat with death retirement. Expected: same identity and
   remaining contents, no duplicate death drops/equipment, unfinished work PAUSED,
   terminal work still terminal. Archives do not occupy active worker slots.
9. **Check dimensions and owner-offline work.** Control Overworld and Nether
   workers remotely, relocate one, and leave a running job with its owner offline.
   Expected: work continues while the server ticks, relocation preserves contents
   and leaves the job paused, and no worker remains active in both dimensions.
10. **Interrupt queued deployment/relocation.** Queue more than four children,
    then disconnect or stop the server after some have succeeded. Expected:
    successful children remain; unfinished requests/reservations are cancelled;
    no duplicate kits, lost archived remainders or permanently occupied slots.
11. **Collect while mining.** Select items, allow workers to collect more, and
    transfer with both ample and limited player inventory capacity. Expected:
    current amounts transfer once; overflow stays at its source; active equipment
    and the cobblestone reserve stay protected. A stale collect-and-retire preview
    must reject before transferring or retiring anything.
12. **Check notifications and layout.** Complete a finite job, reopen/reconnect,
    restart, and inspect normal and compact GUI sizes. Expected: one completion
    entry per run, no repeated toast storm, readable errors, preserved selection,
    and usable scrolling/controls. Offline completions produce one unread summary.

## Runtime regression checks for the human

The compiled fixtures cover forged packets, exact ticket counts, runtime identity,
invalid save variants and native failure boundaries beyond the manual actions.
Run these from the matching source checkout in a disposable test environment:

```powershell
./gradlew.bat :runGameTestServer :worker:runGameTestServer --console=plain
./gradlew.bat :worker:runM4RestartServer -PworkerRestartDirectory=m6-restart -PworkerRestartPhase=write --console=plain
./gradlew.bat :worker:runM4RestartServer -PworkerRestartDirectory=m6-restart -PworkerRestartPhase=read --console=plain
```

Use a fresh `worker/build/m6-restart` directory for the write/read pair; both phases
must use the same directory. The existing restart harness retains its M4 task name
and now checks the M6 paused-job contract too. Expected: all required GameTests
pass; restart phases print `M45_RESTART_WRITE_PASS` and `M45_RESTART_READ_PASS` with
no failure marker. Runtime GameTest classes are intentionally absent from the
production JARs; the Gradle runs use the source checkout's test mod.

Code/build/static verification does not establish these in-game results. M6 is
delivered for your testing; milestone acceptance requires your results or an
explicit exception. Arbitrary-crash transactional recovery is outside this release.
