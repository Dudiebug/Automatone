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
dispositions are still required; the actual approval manifest is empty.

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
