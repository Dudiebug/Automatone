# M2 controller evidence and dependency decision

Status: ACCEPTED by the controller on 2026-09-05; M2.1 through M2.4 are COMPLETE.
Accepted candidate: `30561e6bad934087848cc252796b352c0630444d`.
The same independent milestone verifier supplied passing repair evidence; all
required criteria are satisfied. M3 has not started.
Accepted prerequisite was QUALITY-CLEANUP
`fe41d20bae6adfbc68d03aae4d865831b8b07fad`.

Product candidate: `70de937f7cb419bf62ee73fe4019209db945f51c`.
Dependency-fetch repair candidate: `647e5efbfc3eff89e7f5f7e4a248f84f18a18d47`.
The latter changes only the NVD feed setting and its comment, not product/test
source, dependency versions, analyzer scope, rules or thresholds.

## Completed work and independent measurements

- M2.3 completed at `cf3783b5`: worker-local control suppression, native input
  consumption, preserved vanilla physics and grounded idle/cancel behavior.
  M2.4 completed at `70de937f`: native flat/rise/cancel/removal scenarios. Its
  final three consecutive fresh worker servers each passed all 14 GameTests.
  Task details and earlier fixture failures remain in M2.3/M2.4-EVIDENCE.md.
- The 17 concurrent policy/template/agent files were preserved byte-for-byte
  and committed separately at `854a6900`. Snapshot:
  `.agents/evidence/M2/concurrent-policy-hashes.json`.
- One fresh independent Terra verifier ran the union of default,
  architecture_sensitive, runtime_minecraft and dependency_change against the
  clean product candidate. The runner uses `-TaskId M2.4 -Scope Milestone`;
  an initial `-TaskId M2` lookup failed before sensors because no M2.md exists.
- PASS: compile, Error Prone, Checkstyle, SpotBugs, 85 JUnit/architecture cases
  (zero failures/errors/skips, including four architecture cases per project),
  zero CPD regions, 25 library GameTests and 14 worker GameTests.
- Both built artifacts are separate. The earlier counts of 466 baritone and
  eight worker ZIP entries included directories; actual class counts are 407
  baritone and seven worker classes, with no cross-bundled classes. Worker
  metadata requires the separate Automatone dependency.
- The verifier independently CONFIRMED the two already human-approved exact
  WorkerContext EI_EXPOSE_REP exceptions. Source/eligibility hashes match; raw
  analysis retains exactly those two findings, no errors or missing classes.
  Live entity/controller identity is required by the approved host contract.
  This condition is satisfied; no renewed approval is needed for these two.

Independent record with full hashes, commands and review rationale:
`.agents/evidence/M2/independent-review.json`. Original union measurements:
`.agents/evidence/M2/Milestone-report.json` and its raw checks.txt. They remain
unchanged; subsequent evidence does not overwrite the original INCOMPLETE result.

## Remaining blocking measurement

The first dependency scan could not update NVD. Investigation of published
Dependency-Check 13.0.0 and its client 9.0.6 source found an empty default API
key passed as a header. A keyless NVD request and official feed metadata both
returned HTTP 200. The committed repair uses the analyzer's supported official
NVD 2.0 feed setting; it does not skip or suppress analysis.

The same verifier then ran ONLY `sensorDependency -PrunDependencyCheck
--no-daemon --no-parallel --continue --console=plain`. This exited 1 after
2m02s: both scans completed, then failed the unchanged CVSS 7 threshold.
Each report has 37 vulnerable dependency entries, 766 finding occurrences,
68 distinct blocking CVE IDs, and zero suppressed findings. These are scanner
matches, not 766 independently validated vulnerabilities; repeated component
matches and possible false positives require triage. They are newly measured
findings, not established newly introduced M2 debt. Existing root dependency
versions and gradle.properties are unchanged from the accepted prerequisite.

Raw: `.agents/evidence/M2/dependency-followup-647e5efb.raw.log`.
Frozen HTML reports: `.agents/evidence/M2/dependency-reports/{library,worker}.html`.
Extracted dependency rows and blocking IDs:
`.agents/evidence/M2/dependency-diagnostics/`.

Concrete scope examples from the report's dependency provenance:

| Dependency | Scope requiring review |
| --- | --- |
| Netty 4.1.97.Final modules | Minecraft 1.21.1 / NeoForge 21.1.249 runtimeClasspath and serverLegacyClasspath, not merely test tooling |
| Commons Compress 1.18 | NeoForm Runtime 2.0.24 external build tools |
| JST bundle 2.0.10 shaded Netty 4.2.0.RC2 | NeoForm external build tools; generic Netty CPE matches need module-level validation |
| InstallerTools 2.1.2 shaded Guava/BeanUtils | Bundled build-tool dependency findings; a normal top-level version constraint may not replace shaded copies |

## Initial scope blocker (resolved by user authorization)

Read-only follow-up triage (no dispositions or suppressions applied):

- `srgutils-0.4.15.jar` and `mergetool-1.1.7-fatjar.jar` each match
  CVE-2023-33245 and CVE-2021-35054 through a generic Minecraft CPE. Their
  published POMs identify a Java mapping utility and a jar-merging utility;
  inspection found 41 and 232 classes respectively, with no Minecraft server
  classes. These four occurrences per report are strong product-identity
  mismatch candidates. The CVEs describe older Minecraft server world-file
  handling, not these utilities. This is static triage, not an approved exception.
- Do not dismiss all tool findings: the resolved `plexus-utils:3.3.0` contains
  `Expand.extractFile`, and `javap -c -p` confirms an absolute-path string-prefix
  check. The upstream fix for CVE-2025-67030 replaces that check with canonical
  paths and a directory separator. Component identity and vulnerable code are
  credible here; build-tool reachability and remediation remain unverified.
  Primary fix: https://github.com/codehaus-plexus/plexus-utils/commit/6d780b3378829318ba5c2d29547e0012d5b29642.

This bounded triage does not change the failing dependency verdict or authorize
pin changes. No additional broad checks were run.

The original approved plan said to keep pinned dependency versions. EXECUTION_STRATEGY.md
requires human authority for product-plan changes, accepting baseline debt or
waiving checks. The two approved WorkerContext exceptions do not authorize
dependency exceptions. Therefore M2 is NOT ACCEPTED, and no thresholds,
suppression rules or dependency pins were changed to turn this failure green.

Proposed bounded next scope: triage these measured runtime/build-tool matches;
identify exact false positives and genuinely affected components; prepare the
smallest dependency/toolchain remediation with deployment impact made explicit;
allow changes to currently pinned versions only where required by that approved
remediation; rerun affected checks using retained independent evidence elsewhere.
Do not apply blanket suppressions, exclude build-tool configurations, lower the
CVSS threshold or accept baseline debt implicitly. Any necessary exact exception
must have its own evidence and authority. Stop before M3 throughout.

The user subsequently granted full permission to remediate dependency/toolchain
findings and change blocking workflow provisions on 2026-09-05. Commit `4617fbf7`
records the expanded plan/workflow scope. No renewed approval is required for
these repairs. This does not authorize lowering thresholds or entering M3.

## Authorized dependency repair candidate

`gradle/dependency-remediation.gradle` applies compatible fixes to both projects,
including generated NeoForm and analysis configurations. Netty's BOM also
publishes the runtime alignment to Gradle consumers. Product Java is unchanged.

| Component | Resolved repair | Evidence / scope |
| --- | --- | --- |
| Netty | 4.1.97.Final -> 4.1.137.Final | Official Netty 2026-08-06 security release; server runtime libraries |
| Log4j | 2.22.1 -> 2.26.0 | Apache security fixes for CVE-2026-34478/34479/34480; align all modules |
| Plexus Utils | 3.3.0 -> 3.6.1 | Upstream CVE-2025-67030 fix while retaining the bundled XML API |
| HTTP Core 5 | 5.1.3 -> 5.4.3 | CVE-2026-54399/54428 fixes; CPD tool dependencies |
| Error Prone dataflow | 3.41.0-eisop1 -> 3.49.5-eisop1 | Replaces shaded Guava 30.1.1 with 33.1.0.2; compilation passes |
| Commons Compress | 1.18 -> 1.28.0 | Resolved legacy MergeTool dependency; that legacy tool is unused by MC 1.21.1 |

Primary references: https://netty.io/news/2026/08/06/4-1-137-Final.html,
https://logging.apache.org/security.html,
https://github.com/codehaus-plexus/plexus-utils/releases/tag/plexus-utils-3.6.1.
Pinned published Maven artifacts and NVD descriptions in the retained raw
reports establish the other versions and findings.

Exact dispositions live in `config/verification/dependency-dispositions.xml`.
Each rule selects an immutable Maven artifact path, including its content-hash
directory and shaded-POM suffix where applicable, and enumerates CVE IDs.
Notes retain the actual parent SHA256 and primary references. No CPE-wide,
configuration-wide, CVSS-based or future-CVE suppression is used.

| Exact artifact | Disposition and proof |
| --- | --- |
| JST 2.0.10 Netty buffer/common | 60 CVEs per component describe absent codec/handler/transport/resolver modules. The jar contains only 543 `io/netty/util` and 154 `io/netty/buffer` classes. Every CVE maps to upstream Netty advisories; raw mapping is `dependency-diagnostics/netty-module-dispositions.json`. |
| JLine reader/terminal 3.20.0 | Two Telnet CVEs per jar require absent `remote-telnet`/`TelnetIO`; the resolved graph has neither. |
| SrgUtils 0.4.15 / MergeTool 1.1.7 | Two Minecraft-server CVEs per jar are product-identity mismatches, as established above. |
| Mixin 0.15.2 shaded Guava | CVE-2023-2976 requires FileBackedOutputStream, absent from the minimized bundle. |
| JST 2.0.10 shaded Jackson | CVE-2026-54512/54513 require polymorphic typing. All non-Jackson bytecode has no typing-enable/PTV/JsonTypeInfo references. The only ObjectMapper consumers are IntelliJ `eventLog.LogEventSerializer`, `eventLog.SerializationHelper`, and `config.SerializationHelper`; they use concrete types/nodes, not polymorphic typing. |
| InstallerTools 2.1.2 shaded Guava / BeanUtils | Five CVEs require unused temp-file/Java-deserialization/bean-introspection features. All non-Guava bytecode has no references to the affected Guava methods/types. BeanUtils is referenced only by three embedded OpenCSV bean classes; no InstallerTools class references OpenCSV or BeanUtils. The outer tool IS executed, so this is feature unreachability, not absent-tool reasoning. |

The JST/InstallerTools proof is limited to NeoFormRuntime's supported standalone
`java -jar` launch. JST's manifest has no Class-Path, and its service descriptor
contains only its four embedded transformer providers. The JST `--classpath`
argument supplies parser symbols, not a process/plugin classpath. Reassess these
dispositions if the launch contract or immutable artifacts change.

Read-only helper and controller inspected published binary artifacts with
`javap` and jar/class inventories: NFR 2.0.24 `ExternalJavaToolAction`,
`ApplySourceTransformAction`, `ArtifactManager`; ModDevGradle 2.0.144
`ArtifactManifestEntry`, `NeoFormRuntimeTask`, `DependencyUtils`.
An attempted InstallerTools 4.0.12 override was rejected and removed: it breaks
NeoForm's MERGE_MAPPING arguments, and the selected-GAV artifact manifest can
miss the old requested coordinate and fetch 2.1.2 outside Gradle. The final
candidate retains the actual 2.1.2 tool and its exact feature dispositions.
The same manifest behavior means runtime version alignment does not imply all
NeoForm preprocessing symbol-classpath downloads change. Runtime classpaths
must be checked directly. Commons Compress's old legacy tool is not executed.

Focused commands and retained results under `.agents/evidence/M2/`:

- `dependency-repair-first.log`: same dependency sensor command as above, FAIL;
  compatible ordinary upgrades reduce 766 to 162 occurrences/project.
- `dependency-repair-compile.log`: `compileJava :worker:compileJava --no-daemon
  --no-parallel --console=plain`, exit 0. Compiler warnings remain in the raw
  log, including WorkerEntityController reference equality for required live
  identity; no warning rule was changed.
- `dependency-repair-second.log`: FAIL parsing the initial XML: its schema
  permits only one artifact selector. Repaired to the immutable full Maven
  artifact path; no measurement was claimed from the failed analyzer.
- `dependency-repair-third.log`: FAIL on Jackson CVEs with the subsequently
  rejected InstallerTools override; not acceptance evidence.
- `dependency-repair-final-focused.log`: dependency sensors, exit 0, 32s.
  Both projects pass CVSS 7. Root JSON has 321 dependency records, 136 exact
  suppressed occurrences and 15 remaining occurrences across nine CVEs below
  the existing blocking threshold. This is not a zero-vulnerability claim.
  Frozen JSON: `dependency-diagnostics/repair-final-focused.json`.

The independent pre-check caught four unused BeanUtils-CVE rules accidentally
generated for unrelated shaded POMs in InstallerTools. They matched no findings
and were removed before the verifier began Gradle. The final XML has ten rules;
the same independent verification will remeasure the narrower disposition set.
Of the 17 preserved concurrent policy files, only EXECUTION_STRATEGY.md now
differs, by the explicitly authorized dependency-remediation scope amendment.
The other 16 still match their saved byte hashes.

The independent union passed on `127ebdf0`, but both dependency analyses were
UP-TO-DATE despite the XML edit. The verifier's forced dependency-only rerun
exited 0 and measured the ten-rule candidate freshly (raw:
`dependency-repair-independent-rerun-127ebdf0.log`). This exposed a task-input
defect: the plugin tracked the suppression path, not its contents. The repair
declares the XML as an input and disables up-to-date skipping for explicitly
requested vulnerability analyses, because NVD data can also change without a
source edit. Ordinary non-security Gradle task caching is unchanged. The same
verifier must confirm normal dependency invocations really reanalyze; the
passing union/runtime evidence is not invalidated by this input-only repair.

## Controller acceptance

Independent final record:
`.agents/evidence/M2/dependency-repair-independent-final-review.json`.
Its retained bytecode/archive proof is
`dependency-disposition-independent-bytecode-30561e6.log` in the same directory.
The controller checked the current reports, artifact hashes, source/candidate
scope and prerequisite ancestry before accepting M2.

- The affected union at clean `127ebdf0` passed compile, Error Prone,
  Checkstyle, SpotBugs, ArchUnit, CPD and GameTests. Current JUnit XML totals
  77 library tests plus four architecture tests per project: 85 cases, zero
  failures/errors/skips. CPD has zero duplicate regions. There are no separate
  worker unit cases; worker behavior is covered by dedicated-server tests.
- All 25 library and 14 worker GameTests passed in the union. Two additional
  fresh worker servers each passed all 14 tests (`worker-server-final-run2-127ebdf0.log`
  and `worker-server-final-run3-127ebdf0.log`). Thus all three consecutive fresh
  worker runs cover native flat goal, rise, cancellation and removal.
- Actual GameTest classpaths use Netty 4.1.137.Final, Log4j 2.26.0 and Plexus
  3.6.1. Native path calculation/control/cancellation remain in Automatone;
  the worker has no replacement pathfinder, fake player or packet input layer.
- Both final artifacts were built and inspected: Automatone has 407 baritone
  classes and zero worker classes (SHA256
  `efe405f4944f3146862f06aebb19d455f126306bef260712cc58e934698f8fe5`);
  worker has seven worker classes and zero baritone classes (SHA256
  `42c948bb71d62beeee36e2527d7495086eaced601298a6b1d663de44af09ee60`).
- The two already-approved exact WorkerContext EI_EXPOSE_REP dispositions
  remain independently confirmed. No new worker SpotBugs exception was added.
- The final dependency-cache repair at clean `30561e6b` passed two normal,
  consecutive independent dependency invocations, 35s and 33s. Both analyses
  executed in each invocation; neither reused an up-to-date result. The scans
  resolved 321 root and 322 worker records, each retaining 15 active occurrences
  (nine CVEs, maximum CVSSv3 6.1) and 136 exact suppressed occurrences. Threshold
  remains 7. There is no zero-vulnerability claim or blanket/debt waiver.
- The independent verifier confirmed the ten exact disposition bounds through
  archive inventories, hashes, `jdeps` and `javap`. These are static assessments
  of the current standalone tools, not exhaustive dynamic tracing or proof
  about future artifacts/launches. InstallerTools' affected embedded classes
  still exist but are unreferenced by the supported operations; this limitation
  is retained. Reassess if the pinned artifact or supported launch/operation
  changes. Controller inspection also confines outer reflective class loading
  to ExtractInheritance, which is not one of this NeoForm configuration's
  MERGE_MAPPING/bundler_extract operations.
- The last repair changes only dependency analysis inputs/caching and evidence.
  It cannot invalidate the passing `127ebdf0` runtime, artifact, ownership,
  compile/static or test measurements. Those are reused explicitly, alongside
  the new dependency measurements. No additional full profile was run.

Graphify update succeeded (4,715 nodes / 12,572 edges; eight known Groovy parse
limits remain advisory). The controller accepts the milestone and advances the
accepted baseline to `30561e6b`; no required gate is PENDING or UNVERIFIED.
Final bookkeeping edits do not change the verified product or dependencies.

No external server deployment was requested or performed. Standalone NeoForge
installations do not consume Gradle version overrides automatically; release
deployment must align its runtime libraries to the verified Gradle classpath.
