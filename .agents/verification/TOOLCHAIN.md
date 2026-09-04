# Bootstrap verification toolchain

This is the candidate bootstrap manifest, not an accepted baseline. The
independent verifier must rerun the entry point from a fresh context and keep
the resulting verdict. Product source and existing product tests are outside
this tooling change.

## Pinned versions

| Component | Pin | Evidence/source |
| --- | --- | --- |
| Gradle wrapper | 9.2.1 | [Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html) and `gradle/wrapper/gradle-wrapper.properties` |
| Java compilation | release/toolchain 21 | `build.gradle`; [Error Prone installation](https://errorprone.info/docs/installation) requires a JDK 21+ execution environment |
| NeoForge | 21.1.249 | `gradle.properties` |
| ModDevGradle | 2.0.144 | existing `build.gradle` declaration |
| Checkstyle | Gradle Checkstyle plugin, tool 12.1.2 | [Gradle Checkstyle plugin](https://docs.gradle.org/current/userguide/checkstyle_plugin.html) and [Checkstyle releases](https://checkstyle.org/release-notes.html) |
| Error Prone | plugin 5.1.0; core 2.50.0 | [official Gradle plugin](https://github.com/tbroyer/gradle-errorprone-plugin) and [Error Prone releases](https://github.com/google/error-prone/releases) |
| SpotBugs | Gradle plugin 6.5.11; engine 4.10.2 | [official Gradle plugin](https://github.com/spotbugs/spotbugs-gradle-plugin) |
| ArchUnit | `archunit-junit4` 1.5.0 | [ArchUnit getting started](https://www.archunit.org/getting-started) |
| PMD CPD | 7.16.0 | [official CPD documentation](https://pmd.github.io/pmd/pmd_userdocs_cpd.html) |
| JaCoCo | 0.8.15 | [JaCoCo changes](https://www.jacoco.org/jacoco/trunk/doc/changes.html) |
| OWASP Dependency-Check | Gradle plugin 13.0.0 | [official Gradle plugin documentation](https://dependency-check.github.io/DependencyCheck/dependency-check-gradle/) |

The isolated `sensorTest` source set uses JUnit 4 inherited from the existing
test configuration and ArchUnit JUnit 4. CPD runs through PMD's official Ant
task and fails when its XML report contains duplicate regions. Checkstyle,
CPD, and the runtime checks remain independently runnable when a compile task
fails; Error Prone remains integrated with Java compilation so its findings
are not hidden.

## Entry points

```text
.\gradlew.bat sensorCheck --no-daemon --console=plain
.\gradlew.bat sensorIntegration --no-daemon --console=plain
.\gradlew.bat sensorServerRuntime --no-daemon --console=plain
.\gradlew.bat sensorAll --continue --no-daemon --console=plain
pwsh -NoProfile -File scripts/workflow/Invoke-AutomatoneVerification.ps1 -TaskId BOOTSTRAP -Profile bootstrap
```

The controller adds `--continue`, captures raw output beside the report in
`<report-stem>.raw/` (so different report destinations/stems do not share logs), validates the JSON report with
PowerShell `Test-Json` against `.agents/verification/report.schema.json`, and
recomputes the source fingerprint before accepting the report as structurally
valid. It does not dispatch agents, repair code, commit, mutate
`.agents/STATE.yaml`, or assign acceptance.

`BOOTSTRAP` is the only autonomously eligible controller task. Product task
IDs are intentionally refused; their YAML front matter (including mapped
`depends_on` and list-valued `sensor_profiles`) is not parsed by this small
entry point, so no unsupported eligibility claim is made.

## Known availability and candidate observations

- `runGameTestServer` and project GameTest sources are absent, so
  `sensorIntegration` records `neoforge_gametest=UNVERIFIED` and exits
  nonzero. No no-op GameTest was added.
- No dedicated-server workload or spark observation is configured, so
  `sensorServerRuntime` records `spark_server_health=UNVERIFIED` and exits
  nonzero. No custom runtime telemetry was added.
- Dependency-Check is installed but intentionally opt-in via
  `-PrunDependencyCheck`; this prevents an unbounded NVD database operation
  during the bootstrap dry run. Skipping it is recorded as required
  `UNVERIFIED`, not `PASS`.
- The pre-tooling `clean test` run passed and is preserved in
  `.agents/evidence/bootstrap/tooling/baseline-compile-test.txt` with metadata
  in `baseline-compile-test.json`. The later candidate run exposed an existing
  Error Prone `FormatString` failure at `PathExecutor.java:458`, a Checkstyle
  trailing-whitespace finding at `MovementOption.java:44`, and eight CPD
  duplicate regions. These are recorded as candidate findings; this worker
  does not alter product code.
- The current report also preserves `accepted_baseline: null` and labels the
  dirty candidate. A `PASS` report from this controller is not an acceptance
decision.

## Repair cycle 1

`scripts/workflow/VerificationWorkflow.Tests.ps1` also runs the focused repair
regressions. Product preservation compares the complete `src/` inventory and
hashes; only `src/sensorTest/` and generated `graphify-out/` directories are
excluded. Missing, changed, or additional product files fail.

Reports use `baseline: "UNACCEPTED"` and `accepted_baseline: null`; captured
HEAD is candidate identity, not an accepted baseline. AC-8 remains UNVERIFIED
pending independent verification. Provisional PASS is still permitted and
never mutates STATE. Known task failures override premature PASS markers;
unrelated successful sensors retain PASS. A nonzero process with all selected
sensors nonblocking is a blocking harness failure, not an all-green report.

The actual `compileSensorTestJava.classpath` and `sensorTest.classpath` can be
checked without compiling or disabling Error Prone:

```text
.\gradlew.bat verifySensorClasspath --init-script scripts/workflow/VerifySensorClasspath.gradle --no-daemon --console=plain
```

The init-script check opens the resolved task-classpath JARs and requires
ArchUnit, JUnit, and Minecraft classes. It is a repair regression check, not
a new aggregate sensor. Cycle-1 evidence is under
`.agents/evidence/bootstrap/tooling/repair-1/`; prior draft evidence is retained.

## Final repair cycle 2

Report creation and validation share normalization of sensors, acceptance
criteria and failures. A required sensor FAIL, any acceptance FAIL, or any
non-environment failure record makes the report FAIL. Environment-only failure
records, missing/empty acceptance, UNVERIFIED criteria, or PASS criteria without
nonblank evidence make it INCOMPLETE unless a known failure already takes
precedence. Evidence text records a measurement claim; this CLI does not verify
the truth or completeness of externally supplied acceptance evidence.

Required SKIPPED remains INCOMPLETE because the CLI has no applicability-proof
verifier; merely supplying a skip rationale cannot waive it. Optional SKIPPED
remains nonblocking. All evidenced PASS results may yield provisional PASS in
a dirty development tree, never ACCEPTED and never a STATE change.

Automatic AC-1/2/3/4/5/7/8 mappings remain UNVERIFIED: task paths, run counts,
test-name text and schema success are not full-criterion proof. AC-6 reports
the complete product inventory measurement; an actual schema failure can fail
AC-5. This controller therefore cannot produce a complete BOOTSTRAP PASS by
itself. Final-cycle evidence is isolated under `tooling/repair-2/`.
