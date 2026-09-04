# Java / NeoForge Sensor Stack

This is the preferred sensor *shape*, not a pinned dependency manifest. Resolve compatible tool/plugin versions against the repository's actual Gradle, Java, Minecraft, and NeoForge versions during bootstrap.

## Code and build sensors

| Sensor | Preferred tool | Primary question | Typical role |
| --- | --- | --- | --- |
| Compile/type/API | Gradle + javac | Does it compile against the declared platform? | hard gate |
| Unit behavior | JUnit / existing framework | Does isolated logic satisfy behavior? | hard gate |
| Source policy | Checkstyle | Did source structure/style policy regress? | hard gate |
| Compile-time correctness | Error Prone | Did suspicious Java constructs appear at compile time? | hard gate |
| Bytecode static analysis | SpotBugs | Did likely correctness bugs appear after compilation? | hard gate |
| Architecture | ArchUnit | Did forbidden dependency/layer relationships appear? | hard gate |
| Duplication | PMD CPD or equivalent | Did copied/structurally duplicated implementation appear? | hard gate/new-code gate |
| Coverage | JaCoCo | Did tests actually exercise changed behavior? | selected threshold/measurement |
| Dependency risk | OWASP Dependency-Check or equivalent | Did dependency vulnerability risk change? | dependency-change gate |
| Structural state | Graphify | What already exists, what depends on what, what topology changed? | pre/post advisory sensor |

## In-server sensors that require no custom Automatone instrumentation

These are the preferred first-line runtime sensors. Do **not** build an Automatone telemetry/probe subsystem just to obtain these measurements.

| Sensor | Built in / existing? | What it observes | Development required? | Role |
| --- | --- | --- | --- | --- |
| NeoForge GameTests | NeoForge framework | Actual Minecraft/NeoForge behavior | No sensor framework development; task-specific tests may still need to be written | behavioral hard gate when selected |
| spark | Existing server profiler/mod | TPS/MSPT, hot paths, CPU, memory/GC health | No Automatone code; install/configure compatible server build | runtime/performance gate or advisory |
| Minecraft JFR | Built into Minecraft/JVM server tooling | Tick/runtime events, CPU samples, allocations/GC and deep profiling evidence | None | deep diagnostic/advisory by default |
| Vanilla `/debug` profiler | Built into dedicated server | Server tick profiler output for a bounded workload | None | quick profiling/advisory |

### Zero-development runtime profile

Use the `server_runtime_observability` sensor profile when the goal is to observe the live server **without creating any new sensor code**.

Recommended flow:

1. Run a dedicated verification server with a compatible server-side spark build.
2. Establish an idle/baseline observation window.
3. Run the task-defined Automatone workload.
4. Capture spark server-health/profiler evidence.
5. If spark indicates a regression or unexplained hot path, collect Minecraft JFR and/or vanilla `/debug` profiling evidence.
6. Compare candidate measurements to the accepted baseline under the same workload/environment.

The verifier should retain the raw reports/recordings and summarize only the measurements relevant to the task.

### Important limitation

Off-the-shelf profilers answer questions such as:

- Did MSPT/TPS regress?
- What methods or tick sections became hot?
- Did CPU, allocation, memory, or GC pressure increase?
- Is the worker/pathing workload creating unexpected server cost?

They do **not** by themselves prove product semantics such as:

- exactly three source blocks were mined;
- cancellation stopped at the correct boundary;
- ownership checks rejected a forged request;
- exactly one Automatone runtime exists per worker.

Use existing/project GameTests for those behavioral assertions. If a task-specific GameTest does not already exist, mark the behavioral measurement `UNVERIFIED`; do not invent a custom telemetry subsystem merely to make the verifier green.

## Why several sensors instead of one analyzer

The tools measure different failure modes. Static analysis cannot prove Minecraft runtime behavior; a profiler cannot prove exact product semantics; a GameTest cannot reliably detect forbidden package coupling; coverage cannot prove correctness; Graphify can reveal responsibility overlap but should not replace deterministic duplication or architecture gates.

## Initial project-specific hard checks to pursue

Where the code layout permits an honest deterministic rule:

1. server source must not depend on `net.minecraft.client..`;
2. NeoForge server mining/runtime code must not depend on Fabric/Quilt/Cardinal Components runtime ownership;
3. consumer worker/product packages must not introduce project-owned pathfinding/scanner implementations that duplicate native Automatone responsibilities;
4. authorization and world mutation must remain server authoritative;
5. milestone-specific GameTests must directly measure exact quantities, cancellation, lifecycle, persistence, and security semantics described by the approved plan;
6. performance-sensitive tasks should include a repeatable server workload and baseline/candidate spark measurement before acceptance.

If a rule cannot be expressed reliably yet, keep it as a verifier/source-review check and create a dedicated architecture-observability improvement rather than encoding a misleading test.
