# M5 — Automatone UI and controller

Human-approved implementation plan, 2026-09-06. Supersedes the original M5
single-worker binding, single-target, one-screen and excluded-fleet contracts,
and conflicting M6 integration assumptions. M4 is the accepted prerequisite.
The final human correction is mandatory: new workers have nine empty inventory
slots, with no starter pickaxe, tools or materials.

## Product contract

- One reusable, craftable, non-stackable controller opens its current holder's
  roster. No player/worker binding, energy or durability. Ownership never follows
  the item. Recipe: four iron ingots, copper ingot, glass pane and redstone dust.
- Author a 16x16 controller in Piskel from the concept's iron body, green display
  and button, and copper antenna. Keep editable source and transparent PNG. No
  ImageGen for the production sprite. Concept PNGs are visual references only.
- Ten active workers per owner, free creation with empty inventory. Cross-world
  control does not require proximity. Retired workers do not consume active slots.
- Roster normally uses two rows of five; small screens reflow with scrolling.
  Cards show name, dimension, coordinates, targets, quantity/progress and state.
  Next free slot adds a worker. Selection supports individual/all and batch jobs.
- Job, Inventory and Settings tabs share navigation. Job has a dominant searchable
  multi-select registered-block picker, mod filter, selected chips, quantity,
  unlimited, Start, Pause/Resume, Stop, Apply job and Relocate. Search translated
  names and registry IDs; server validates requests. Inventory exposes nine real
  slots, selected tool and player inventory. Settings includes name, location,
  per-worker setting overrides, relocation and retirement.
- Personal Configuration provides searchable native settings, categories, typed
  controls, descriptions, reset and explicit Apply. There is NO Permissions page.
  Display reasons for unavailable client-only, Java-only or shared infrastructure
  settings; never expose an editable control that silently has no effect.
- Settings inheritance is native defaults -> personal profile -> worker override.
  Resetting an override resumes inheritance. Applying settings cancels stale native
  work and replans previously running jobs without losing progress. Paused jobs stay
  paused. Native runtime instances and async calculations must use isolated settings;
  global swapping and thread-local impersonation are forbidden.
- Multi-target finite quantities count successful matching source-block destruction
  together per worker, never drops/pickups. Default 64, existing finite bounds
  1..1,000,000; zero represents unlimited internally. All native discovery, mining,
  pathfinding, movement and cancellation remain Automatone-owned.
- Pause preserves job and progress; Resume continues remaining work. Stop ends the
  run. Start resets progress. Existing native completion/error behavior remains.
- Apply job copies only targets and quantity, preserving recipient overrides.
  Preview recipients; replacing busy jobs requires confirmation. Offer Apply settings
  and Apply & start. Invalid/stale contexts must not bypass ownership or replacement
  confirmation; report per-worker execution errors accurately.
- Add/relocate/reactivate choose Overworld or Nether. Sample destinations anywhere
  inside that dimension's world border, with bounded asynchronous preparation,
  cancellation and safe floor/collision/hazard/build-limit checks. Relocation pauses
  immediately. Failure/cancellation leaves the worker at its original location paused.
- Retirement pauses, archives identity/job/settings/remaining inventory, removes
  the live entity and releases tickets. Archived inventory allows view and withdrawal
  only. Reactivation requires an active slot and safe destination, restores the same
  identity and remaining inventory; unfinished jobs stay paused and terminal states
  stay terminal. Never grant equipment during reactivation.
- Include retired roster/details/withdrawal/reactivation, notifications inbox and
  explicit dialogs for deployment, relocation, batch replacement, retirement,
  reactivation and discarding unsaved edits. Show pending/cancel/failure states.
- Owner-only completion toasts with quiet sound; separate personal toast/sound
  toggles. Persist latest 100 completion entries with run identity, worker, targets,
  amount, time and read state. One server completion event per run; pause/replan/
  reopen/reconnect cannot duplicate it. Keep offline completions and show one unread
  login summary, not a replay of all toasts.

## Authority, persistence and failure model

Server-owned menus and roster handles resolve identity. Validate current menu,
controller access, owner, active/retired state, revision, registry IDs and quantities
before mutation. Minecraft native slots synchronize inventories; additional state
uses bounded custom payloads. Client screens/renderers remain client-only.

Persist roster/archive, profile/overrides, inventory/selection, multi-target jobs,
paused state, run identities and notifications. Upgrade v1 single-target jobs into
one-element target lists without changing progress or ownership. Running jobs still
resume after normal server restart; paused/terminal jobs remain stopped. Do not save
native search queues, paths or partial damage. Existing M4 nine/one ticket policy
remains. No arbitrary-crash transaction or offline catch-up guarantee is introduced.

| Failure | Required evidence |
| --- | --- |
| Personal settings leak across workers/async calculations | Two isolated runtimes with opposing settings and captured calculation contexts |
| Cancelled/paused work resumes from stale native result | Native runtime observation after pause/replan and resume of remaining amount |
| Target list/finite accounting drifts | Real mixed-target destruction stops at exact combined count |
| Creation/retirement races duplicate slots/workers/items | Server validation, concurrent/stale requests, archive withdrawal and save/reload |
| Hostile client operates another owner's worker | Negative menu/payload/slot tests with unchanged authoritative state |
| RTP blocks tick or leaves tickets/entities after failure | Bounded search, cancellation, border/hazard cases and cleanup checks |
| Restart loses paused state or repeats notifications | Save/load of legacy/new jobs, archive and per-run completion identity |
| Native GUI leaks client classes into server | Compilation, architecture sensor and dedicated-server execution |

## Execution sequence and evidence

M5.1 isolated settings + multi-target/Pause/Resume; M5.2 roster/profile/archive;
M5.3 safe RTP and slot reservations; M5.4 sprite/controller/recipe; M5.5 menus and
validated networking; M5.6 native screens; M5.7 notifications; M5.8 independent gate.
The concise record is .agents/decisions/M5-EVIDENCE.md. Task specs define focused
acceptance checks; complete each before the next. Astra authors/reviews the testing
foundation and integrates bounded Luna Max tests. Use focused checks during work,
then one fresh independent clean-candidate milestone profile. No required sensor
waivers, blanket mutation/coverage targets, or duplicate preliminary full gate.

M6 retains final reliability/error/compatibility hardening and acceptance, rebased
onto these contracts. M5 implements the persistence its user flows need now rather
than shipping retirement or notifications with transient authoritative state.
