# BOOTSTRAP — Integrate verification workflow without product changes

## Metadata
- State: IMPLEMENTING
- Milestone: tooling only (not one of the 26 product tasks)
- Depends on: installed workflow bundle
- Risk: high (false acceptance is the primary failure mode)
- Sensor profiles: default, architecture_sensitive, dependency_change
- Authority: human request "run the bootstrap setup then"; BOOTSTRAP_GOAL.md in full.
- Detailed spec approval: not separately obtained (autonomous run); bootstrap scope is authorized.

## Objective and scope
Map the existing candidate, distill exactly 26 product task specs, configure compatible pinned verification tools and stable Gradle entry points, provide agent/controller entry points, and run a non-product verification dry run with independent evidence.

Allowed: build-only dependencies/configuration, verification tests/scripts, task metadata, helper-agent definitions, reports and explicit tooling blockers. Reuse existing JUnit 4 and Gradle. Target Checkstyle (source rules), Error Prone (compile diagnostics), SpotBugs (bytecode), ArchUnit (dependencies), PMD CPD (duplication), JaCoCo (coverage measurement), OWASP Dependency-Check (dependency risk). Resolve/pin versions from official sources and record reasons/compatibility. Unavailable runtime checks must fail closed with UNVERIFIED, not masquerade as tests.

Forbidden: changes to production Java/resources, existing product tests, approved plan, global sensor policy, AGENTS.md routing or execution strategy; no milestone implementation, commits, resets, suppressions/waivers or accepted-baseline invention; no global tool installation or custom runtime telemetry. M1.5 remains reserved for the user. Do not mark historical M1.1–M1.4 claims ACCEPTED without independent evidence.

## Acceptance / evidence mapping
| ID | Criterion | Measurement |
| --- | --- | --- |
| AC-1 | Existing graph is queried and code map is grounded in current source | saved graph query and mapping with source references; stale/missing graph facts disclosed |
| AC-2 | Exactly 26 plan task IDs, original requirements and milestone gates preserved | task inventory comparison to plan headings; each criterion maps to evidence; unknown dependencies labeled INFERRED/UNKNOWN |
| AC-3 | sensorCheck, sensorIntegration, sensorAll entry points resolve; runnable sensors execute, unavailable ones cannot pass | Gradle task runs/logs and per-sensor normalized status; no no-op passing tasks |
| AC-4 | Architecture gates detect actual forbidden dependencies and do not pass vacuously | positive/negative controls in verification-only tests; existing violations reported, not fixed |
| AC-5 | Controller produces schema-valid reports, selects only authorized task, does not auto-accept dirty state | dry run plus known FAIL/UNVERIFIED controls; exit codes and state/report assertions |
| AC-6 | Production sources/resources and pre-existing product tests unchanged | before/after SHA-256 inventory |
| AC-7 | Five role definitions preserve scope, Luna/Max Old Coder routing, independent verifier separation | exact definition/template comparison and independent review |
| AC-8 | Baseline debt and fresh verification limits are explicit | HEAD plus dirty snapshot fingerprint, versions, raw reports, independent verifier verdict; accepted baseline stays null |

## Failure handling
Tool incompatibility, missing vulnerability database, absent GameTests/runtime fixtures, existing architecture findings and dirty baseline are reportable blockers. Do not fix product code or weaken sensors. A bootstrap may deliver working tooling with an overall FAIL/INCOMPLETE candidate verdict; that is not permission to accept product tasks.

## Verification plan
Capture pre-tooling compile/unit result and product fingerprint; prove new gate failure paths; run configured sensors with raw output; verify normalized evidence against report.schema.json; repeat final tooling checks after last edits; fresh separate Luna verification-only invocation receives contract/spec/source identity/entry point, not builder reasoning. No new dependency ships inside the mod solely for verification.
