# M6 evidence

Contract: `docs/M6_FINAL_RELIABILITY.md`, explicitly approved by the human.
Controller: Astra. Baseline: M5 `7e781a4b9627092fec115d9ebc08679a500fa295` / 0.12.1.
Implementation/delivery COMPLETE; M6 in-game acceptance PENDING — HUMAN TESTING.

## Prerequisite and verification

The human explicitly accepted M5 and authorized M6. M5 is ACCEPTED by that
approval; historical PENDING measurements remain unchanged. Installed graphify,
ponytail and old-coder skills informed navigation, minimal changes and acceptance
mapping; AGENTS.md/EXECUTION_STRATEGY.md governed proportional checks. No new
dependency, retry engine, saved native state, protocol change or weakened sensor.

## M6.1 — code COMPLETE

Existing v1–v3 entity saves and roster-owned profiles/archives/notifications remain.
Fixed WorkerRoster.removed: stale-incarnation and foreign-owner callbacks cannot
capture/delete an authoritative record. Existing menu, fleet, collection and
relocation entry points retain ownership/context/revision checks before mutation.
Three new real-entity fixtures cover version migration, five invalid-job variants,
all 36 component-bearing slots/equipment and live/unloaded roster removal guards.
Disproved assumption: any removal callback with matching UUID can be trusted.

PASS: `:worker:compileGameTestJava :worker:test --tests automatone.worker.MiningSession*`
(19 unit tests; `persistence-focused.log`). New fixtures compile
(`persistence-fixtures-final.log`); one new fixture identity warning was corrected
without changing its expected outcome. Existing roster/inventory/notification/menu
fixtures supply other contract cases. Runtime results PENDING — HUMAN TESTING.
Luna returned no patch after extended design; Astra authored/reviewed the fixtures.

## M6.2 — code COMPLETE

Loading saved data into an already attached entity could retain old native work.
WorkerEntity now disposes that runtime before restore and attaches one fresh
runtime afterward. RUNNING intent remains readiness-gated; paused/terminal states
remain stopped. Added an attached-load fixture observing disposal, native
cancellation and provider uniqueness, and a nonzero paused run to WorkerRestartProbe.
Existing transfer/death/reactivation/ticket policies and batch-before-relocation
shutdown cleanup were reviewed and retained. No second lifecycle system was added.

PASS: production/GameTest compilation and four provider/generation unit tests.
`lifecycle-focused.log` initially selected unqualified `test`, which also tried
root filters in worker:test and failed with no matching tests; root tests and
compilation passed. Corrected `:test` command in `lifecycle-focused-final.log`
passed using valid UP-TO-DATE evidence. No assertion changed. Actual restart,
tickets, queues and attached reload execution remain PENDING — HUMAN TESTING.

## M6.3 — code COMPLETE

Added IMineProcess.TerminationReason/read-only accessor. Native exits distinguish
CANCELLED, COMPLETED, NO_TARGETS, PATH_FAILED, BREAK_DISABLED and INTERNAL_FAILURE.
Repeated cleanup preserves reasons; new mining resets them; stale mailbox results
obey generation guards. Blacklist exhaustion reports PATH_FAILED. Exploration and
native mining ownership are unchanged; unexpected scan/start errors log stack traces.
Worker mapping retains progress/run ID, distinguishes INTERRUPTED and preserves
Pause/Stop/exact source-count completion. Native item-count completion cannot
establish consumer source-count completion. String saves/payloads and protocol 5
are unchanged. All screen error displays use localized messages or an unknown-code
fallback; legacy codes still render.

PASS: seven native lifecycle/generation/termination unit tests
(`native-termination-final.log`), 21 MiningSession unit tests and all GameTest
compilation (`worker-failure-focused.log`). Five real native fixtures cover
no targets, path blacklist exhaustion, disabled breaking, allowBreakAnyway exact
completion, and native completion/async failure. They remain PENDING — HUMAN TESTING.
Localization check: 138 literal screen keys, 43 error entries, no missing keys
(`localization-check.json`); packaged resource UTF-8/JSON also validated.

Luna's initial async test failed while bootstrapping Minecraft in a plain unit JVM
(`native-termination-initial.log`, unavailable LoadingModList). Astra rejected
extra bootstrap infrastructure, removed it, required draining stale results before
active delivery to avoid a vacuous assertion, and added post-failure new-run reset.
The improved server failure logger no longer initializes Minecraft merely to log,
so mailbox behavior can run in the unit JVM. No product assertion was weakened.

## M6.4 — independent gate and package

Fresh independent verifier: Curie, separate from implementation/test authoring.
Clean candidate: `735a36e08f597dec9bf26c0fd47559e7a798cf9a`.
Command: `Invoke-AutomatoneVerification.ps1 -TaskId M6.4 -Scope Milestone -Profile
default,architecture_sensitive -FreshContext -ReportPath
.agents/evidence/M6/independent-profile.json`.

PASS: all seven sensors — compile, unit tests, Checkstyle, Error Prone, SpotBugs,
ArchUnit and duplication. JUnit results: 79 native + 24 worker tests; architecture:
4 native + 5 worker tests; zero failures/errors/skips. This was the only full
profile; no preliminary duplicate. Raw evidence: `independent-profile.json` and
`independent-profile.raw/checks.txt`. The runner's raw baseline fields remain
UNACCEPTED/null; human M5 acceptance is recorded above and in STATE.yaml, not
invented as a measured baseline by editing the runner report.
Independent bounded source review found no actionable product defect and confirmed
all changed contracts; compiled but unexecuted Minecraft fixtures remain pending.
Baseline reconciliation: VerificationWorkflow.psm1:531 deliberately passes
UNACCEPTED and :134 writes null; :533 explicitly limits the report to measurement.
These sentinel fields do not reverse the human's M5 approval. Preserve the raw
report and use the recorded human approval for prerequisite acceptance.

Graphify incremental code update PASS: 6,295 nodes / 19,625 edges
(`graph-update.log`). Eight existing Groovy parser warnings remain advisory;
document semantics were not regenerated. Corrected Windows-default text encoding
and line endings before the clean candidate; resources/docs are UTF-8.

PASS: `:jar :worker:jar` (`package-build.log`), both 0.13.0 descriptors, worker's
exact native dependency [0.13.0], production termination API, no GameTest classes,
UTF-8 localized resource, all five payload SHA256 checksums and ZIP CRC
(`package-validation.json`). Prior artifacts remain. Not published.

Delivery: `dist/automatone-0.13.0-mvp.zip`, both JARs, README, SHA256 manifest,
M6 upgrade/acceptance guide and existing global-controls guide.
ZIP SHA256: `99e2338690f0ef3a7a23f1f1477d51aef0c7c3045ed1c98b83cc87a4a0230ec3`.
Native JAR SHA256: `e524820b65b61bbbeb7aa900ea9753b54f17875d4b190b34492d465e60c8278d`.
Worker JAR SHA256: `9258235b817ac42b1586777fbbe8c75f6ae32c2a8ad045dad3ba426d69f10370`.
Final bookkeeping commits do not change packaged product source.

## Remaining acceptance matrix

Every in-game measurement is PENDING — HUMAN TESTING. No Minecraft client,
GameTest server, restart probe or profiling server was launched by the agents.

| Contract | Fixture/checklist coverage retained or added |
| --- | --- |
| Save compatibility, invalid jobs, identity/inventory/equipment | M6 persistence; existing inventory/foundation; guide steps 1–2 |
| Profiles, archives and notification deduplication | Existing roster/notifications/collection; steps 1, 8, 12 |
| RUNNING-only finite/unlimited resume; paused/terminal retention | Extended real restart probe; steps 3–5 |
| Fresh runtime, stale work, shutdown, tickets and dimensions | Attached reload, native generation, chunk/batch/relocation; steps 4, 9–10 |
| Owner/non-owner, stale/forged/repeated packets and collection overflow | Removal guard, existing menu/batch/collection; steps 7, 10–11 |
| Native/product failures, cancellation and exact completion | M6 failure and existing quantity/cancellation; steps 3, 5–6 |
| Readable status, normal/compact layouts and notifications | Localization + human GUI; step 12 |

Controller confirms implementation scope and architecture. Delivery does not accept
M6: acceptance awaits the human's runtime/checklist results or an explicit exception.

## 1.0 universal package and release follow-up

Human approved the universal-JAR plan and explicitly requested public version 1.0
as Latest on Dudiebug/Automatone. This supersedes the earlier two-JAR/no-publishing
delivery direction for this follow-up; M6 in-game acceptance remains pending.

Changes: both module versions are 1.0; worker archive is automatone-bundled and
uses ModDevGradle jarJar(project(':')) with transitive=false. The native library
remains a separate normal dependency and is embedded intact. No Java production
code, registry IDs, save formats, network protocol 5, side registration or mining
ownership changed. Existing Dist.CLIENT subscribers/client mixin lists and
logical-server ticks supply client, dedicated-server and integrated-server roles.
Updated README/current-status pointer and the installation/controller guides;
release notes are docs/RELEASE_1.0.md. No task assumption was disproven.

PASS: `./gradlew.bat :sensorCompile :sensorUnitTests :sensorArchunit :worker:jar
--console=plain` (exit 0; bundled-1.0-checks.log). Both projects' compilation
accepted current cached classes; unit and architecture tasks executed freshly:
103 unit tests and 9 architecture tests, zero failures/errors/skips. Existing
Gradle deprecation notice remains. Reused independent M6 source/static evidence
above for unchanged Java code; no second full milestone profile was run.

PASS: `python scripts/workflow/verify_bundled_jar.py
worker/build/libs/automatone-bundled-1.0.jar build/libs/automatone-1.0.jar 1.0`
(bundled-1.0-package.json). Checks archive integrity/unique entries, both mod IDs
and versions, exact worker/native dependency, one declared embedded native JAR
whose bytes equal the standalone build, production assets/classes, JSON/mixin
registration, client-only toast mixin, and no test/runtime/optional API placeholders
or further embedded JARs. PASS: git diff --check.

Artifacts: dist/automatone-1.0 contains the single installable JAR, updated M6
release guide, global controller guide and SHA256SUMS.txt.
JAR SHA256: cff9105cd9b75e1570712f1feff56787f0d076a5b57cabd1421db07a0c14c75d.
Embedded native SHA256: 412a58f2480ea926f276670984dcd4ab7f3508ba59b1137c440720a6eda017a5.
Publication targets tag v1.0 at the verified release commit, title Automatone 1.0,
non-draft/non-prerelease and Latest. Remote readback and downloaded-asset hashes
are recorded in .agents/evidence/M6/bundled-1.0-release.json after publication.

PENDING — HUMAN TESTING: all installation and gameplay checks in the attached
guide, including dedicated-server class loading, multiplayer, singleplayer and
existing-world reload. No Minecraft launch, GameTest or restart probe was run.
Controller confirms the packaging scope and architectural invariants; publishing
does not accept M6 or convert any deferred runtime measurement to PASS.

## 1.0 public documentation follow-up

Human approved a README, player/admin wiki, official patch-note expansion and
default-branch update. Documentation now makes the universal 1.0 JAR the primary
entry point, redirects stale top-level Fabric/Baritone guides to the current wiki,
and retains historical branches/tags. Source-controlled wiki pages cover Home,
installation/upgrades, first worker, mining, fleet management, inventory/collection,
settings/notifications and troubleshooting with a shared sidebar.

The expanded release notes distinguish the complete 1.0 feature set from changes
since previews and retain explicit runtime limitations. Claims were checked against
the controller recipe, protocol 5, active limit 10, inventory size 36, relocation
concurrency 4, target limit 128, quantity limit 1,000,000 and localized UI/error
strings. PASS: relative Markdown links and `git diff --check`. No product source,
release artifact, checksum, version, tag or save/network contract changed; no build
or Minecraft run was needed. Public README/wiki/release readback and the repository
default branch are verified after publication. Runtime acceptance remains
PENDING — HUMAN TESTING.
