# QUALITY-CLEANUP candidate evidence

Status: prerequisite acceptance PENDING. No M2 product code has started.

The candidate repairs tick sequence lock ownership, settings save lock ownership,
null-world handling and exception propagation, scanner height coordinates,
schematic array ownership and enum encoding ownership. Explicit switch cases
preserve native pause and precomputed mask behavior. The workflow now uses focused
task checks and one independent clean milestone gate under the approved policy.

Retained development evidence is under
`.agents/evidence/quality-cleanup-20260904/`: focused RED/GREEN logs accompany the
repairs; `playerfeet-null/GREEN.raw.log` measures 25 passing server GameTests,
including scanner parity, movement parity and ownership controls. Its Checkstyle
log passes. Q2 native publication and pathing controls retain their original
assertions and passing focused results. These results are development evidence,
not the final clean gate.

The raw report retained at `gate-tests/ACTUAL-main-raw.xml` contains 75 findings,
zero analyzer errors, zero missing classes and 421 analyzed classes. Nine original
findings are removed: 18, 19, 30, 34, 35, 63, 70, 76 and 77. Exact remaining
dispositions are recorded below; unresolved findings remain blocking.

## Eligibility extraction correction

Original e81 report SHA-256:
`fdcc041917c5c056bbe1fc7cfbf28eee487236d8a16236afc10a29f4b80ba129`.
The source is the independently retained `M2-entry-20260904/independent/reports/spotbugs/main.xml`.
Row 81 incorrectly included child `<Type>` text when extracting the `type`
attribute in PowerShell. Its sole corrected value is `BC_IMPOSSIBLE_CAST`.
This changes no finding, analyzer version, source identity or eligibility scope.

The canonical UTF-8/LF eligibility digest is now
`873d6dc2e5ba53d148210b159ddd92ba9c787f04975ec97595936ce73870d50e`,
pinned in both the gate and approval manifest. The regression fixture retains the
original finding's two `<Type>` children. The unchanged test fails before the
correction (`metadata-81/02-red-metadata.log`) and passes afterward
(`metadata-81/03-green.log`), including the existing exact identity and fail-closed
controls. No analyzer rerun was needed for this metadata correction.

Workflow-only checks are retained in
`.agents/evidence/workflow-proportional-20260904/EVIDENCE.md`; the combined workflow
tests passed. No Java behavior was changed by that workflow update.

The independent verifier must review exact dispositions and source contracts,
then run the union of default, architecture_sensitive and runtime_minecraft
profiles once from the clean candidate, with relevant gate controls. Unresolved
findings or unavailable required measurements block acceptance.

## Remaining repairs selected by the controller

The independent review identified nine unresolved findings. The controller chose
two root-cause repairs, covered by the existing authorization to fix discovered
bugs, before running the cleanup gate:

- IDs 47–49: make the three built-in parser lists immutable, retaining their
  fields, types, values and order. `IArgParserManager.getRegistry()` remains the
  supported parser extension path; mutation of shared constants can corrupt
  ordinary parsing and is not a documented extension contract.
- IDs 79–84: stop packaging the compile-time Schematica/Litematica placeholder
  APIs as production classes. Their current presence on the runtime classpath
  makes the existing presence guards report missing integrations as installed.
  Keep the helper APIs and optional integration source; compile against a
  separate compile-only source set. This repairs artifact ownership and does not
  port either optional mod or exclude an actual product defect from analysis.

Focused regressions measured parser ownership and absent-integration/runtime
artifact behavior before and after these repairs. Final analyzer confirmation and
the clean applicable profile remain PENDING.

## Exact warning dispositions

The fresh independent `luna_old_coder` verifier reviewed clean candidate
`dbf134334b9e84da2309235c84daabcc19ceb7a1` against the approved seven-condition
exception policy and the retained unsuppressed report. Its exact result is
preserved at `independent-warning-review/proposed-approvals.json` in the cleanup
evidence directory, with `review-record.json` recording an INCOMPLETE milestone.
The retained proposal's byte SHA-256 is
`6d553480447ed6d64e9d5533d0166f40e974757a7cbbb2cfd8c7a05c9f89891f`.
The controller inspected the 66 explicit approvals and confirmed that each
approved source file still has its independently recorded canonical hash.

`config/verification/spotbugs-approved.json` retains those exact IDs, reviewer,
source hashes and per-finding contract rationales. The pinned eligibility
inventory supplies each complete pattern/class/member/signature/location/message
identity. No additional finding is approved, and later source changes invalidate
affected approvals. IDs 47–49 and 79–84 were rejected and selected for repair;
none is in the approval manifest.

The approved contracts cover native owner identity, live event payloads and
configuration, registry values, server tick ownership, mutable native calculation
outputs, documented command behavior and input-validation constructors. Existing
runtime/provider/ownership tests are supporting evidence where applicable;
source-only rationales do not replace the required deterministic sensors. The
full clean milestone gate remains PENDING.

Parser ownership repair: the four focused tests retain ordinary boolean/numeric
parsing and exercise replacement of all three shared lists. Before the repair,
three protection checks failed; after the three `List.of` substitutions, all four
pass (`parser-list/FOCUSED-RED.raw.log` and `FOCUSED-GREEN.raw.log`).

Optional integration repair: the original focused test measured two failures in
three tests, and the jar check found bundled third-party placeholders. Separating
the existing placeholder source into a compile-only source set removed those jar
entries. The first runtime rerun exposed eager resolution of an absent Litematica
subtype during helper verification. Using the API's exact `WorldSchematic` return
type for the local variable removes that unnecessary subtype conversion while
preserving the helper API and optional implementation. The unchanged final
presence tests pass 3/3 (`optional-schematic-ownership/FOCUSED.GREEN2.raw.log`).
The jar contract passes with zero forbidden entries and both helpers present;
its dedicated verification task also executes and passes with an up-to-date jar
(`JAR.GREEN.raw.log`, `JAR.GREEN.cached.raw.log`). Stub Checkstyle coverage remains
required; the production analyzer receives those compile-time contracts as
auxiliary classes rather than treating them as shipped product classes.

Graphify's incremental update completed after the parser/build changes (4,512
nodes, 11,732 edges). Its Groovy parser partially extracted six build/test scripts;
these advisory graph limitations do not stand in for Gradle compilation or the
deterministic architecture checks.

The independent full profile on clean `2661017a9ca63e043444ae14aee249601a71f255`
passed compilation, unit tests, style, Error Prone, architecture, duplication and
all 25 server GameTests. The exact gate and jar controls passed. SpotBugs alone
failed on two Litematica helper stores: its auxiliary compile-only API methods
unconditionally threw `LinkageError`, falsely making following integration code
unreachable. The raw analysis was complete (68 findings, no errors/missing classes).

The controller replaced the 13 fake throwing method bodies in the six Litematica
compile-only API files with bodyless declarations, retaining the same method
names, parameters, return types and static/instance contracts. Java's `native`
modifier expresses a declaration here; these classes remain absent from the
artifact/runtime, and the optional mod supplies the real Java implementations.
No native runtime binding or optional integration implementation is introduced.
The failed analyzer result is the regression for this contract-modeling defect.
Affected compilation, stub style, analysis and artifact checks remain PENDING;
the passing runtime/unit/architecture evidence can be retained because their
product source and runtime ownership are unchanged.
