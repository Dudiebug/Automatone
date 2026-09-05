# M2 controller evidence and dependency decision

Status: BLOCKED at the milestone gate; M2.1 through M2.4 are COMPLETE.
Stop before M3. Accepted prerequisite remains QUALITY-CLEANUP
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
- Both built artifacts are separate: Automatone has 466 baritone classes and
  zero worker classes; worker has eight worker classes and zero baritone
  classes. Worker metadata requires the separate Automatone dependency.
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

## Decision required before further implementation

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

The approved plan says to keep pinned dependency versions. EXECUTION_STRATEGY.md
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

Controller confirms all M2 product criteria and the independent ownership review.
The unresolved dependency gate prevents milestone acceptance. No broad profile
was repeated after the NVD fetch repair. All other measured evidence remains
applicable because the repair changed only vulnerability-data acquisition.
