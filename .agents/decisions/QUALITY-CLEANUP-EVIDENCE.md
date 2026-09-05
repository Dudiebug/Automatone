# QUALITY-CLEANUP acceptance evidence

Status: **ACCEPTED** by the controller at `fe41d20bae6adfbc68d03aae4d865831b8b07fad`.
The prerequisite is complete; M2.1 may begin. No cleanup checks remain deferred or
unverified. Evidence paths below are relative to `.agents/evidence/quality-cleanup-20260904/`.

## Changes and focused evidence

Repairs address tick/settings lock ownership, null-world handling and exception
propagation, scanner height coordinates, schematic/enum ownership, and native
pause/mask behavior. Retained practical RED/GREEN controls cover each correction.
Q2 publication/pathing controls retain their original assertions and passing
results in `Q2-focused-final.raw.log`, `Q2-mineprocess-final.raw.log` and
`Q2-pathing-final.raw.log`. Q4 movement/scanner parity results are retained in
`movement-parity/movement-parity-final.raw.log` and
`scanner-parity/FINAL-post-world-coordinate-fix.raw.log`.

The parser constants now use immutable lists: four focused tests pass, with three
protection checks failing before the correction (`parser-list/FOCUSED-RED.raw.log`
and `FOCUSED-GREEN.raw.log`). Optional integration placeholders now compile in a
separate compile-only source set and are absent from the product jar. Exact
`WorldSchematic` local typing avoids eager resolution of an absent optional class.
Presence regression: two failures before repair, 3/3 pass afterward
(`optional-schematic-ownership/FOCUSED.GREEN2.raw.log`). Jar controls verify zero
forbidden placeholder classes and both integration helpers, including a cached
jar invocation. Stub Checkstyle coverage remains required.

## Eligibility extraction correction

The independently retained original report at
`../M2-entry-20260904/independent/reports/spotbugs/main.xml` has SHA-256
`fdcc041917c5c056bbe1fc7cfbf28eee487236d8a16236afc10a29f4b80ba129`.
Row 81's extraction mistakenly included two child Type nodes; correcting its
pattern to `BC_IMPOSSIBLE_CAST` changes no finding, scope or analyzer version.
The canonical UTF-8/LF eligibility digest is
`873d6dc2e5ba53d148210b159ddd92ba9c787f04975ec97595936ce73870d50e`,
pinned in the gate and approval manifest. The fixture retains both child nodes;
unchanged gate tests fail before correction and pass afterward
(`metadata-81/02-red-metadata.log`, `03-green.log`).

## Exact warning dispositions

One fresh independent Luna verifier reviewed clean
`dbf134334b9e84da2309235c84daabcc19ceb7a1` against the approved exception policy.
Its 66 exact approvals are retained in
`independent-warning-review/proposed-approvals.json` (byte SHA-256
`6d553480447ed6d64e9d5533d0166f40e974757a7cbbb2cfd8c7a05c9f89891f`).
The controller inspected each approval and confirmed unchanged canonical source
hashes. `config/verification/spotbugs-approved.json` retains IDs, reviewer,
source hashes and individual contract rationales. The frozen eligibility inventory
supplies full finding identities; source changes invalidate affected approvals.
Rejected IDs 47–49 and 79–84 were repaired and are not approved.

The contracts cover native owner identity, live event payloads/configuration,
registries, server tick ownership, native calculation outputs, documented commands
and input-validation constructors. Source rationales complement deterministic
checks; they do not replace them. Final raw analysis measures exactly 66 approved
findings, zero unapproved findings, zero errors and zero missing classes across
407 analyzed classes. No new findings or blanket exclusions are accepted.

## Independent gate and controller decision

The full default, architecture_sensitive and runtime_minecraft profile ran once
on clean `2661017a9ca63e043444ae14aee249601a71f255`: compilation, 77 unit tests,
25 server GameTests, style, Error Prone, architecture and duplication passed.
Exact-disposition and jar gate controls passed. SpotBugs alone initially failed
on two stores rendered unreachable by fake throwing auxiliary API bodies.

The parent replaced 13 throwing bodies in six compile-only Litematica API files
with bodyless native declarations, preserving method ABI. These classes never
ship or run; the optional mod supplies actual Java implementations. No native
runtime binding is introduced. The same verifier reran affected compilation,
SpotBugs, stub Checkstyle and jar ownership on clean `fe41d20b`; all passed
(`independent-warning-review/affected-checks-fe41d20.raw.log`). Prior runtime,
unit and architecture evidence remains valid because product/runtime source did
not change. No identical second full profile ran.

Authoritative aggregate: `independent-warning-review/review-record.json`;
full command output: `independent-warning-review/review-record.raw/checks.txt`.
Raw/filtered analyzer XML, CPD XML and all 77 unit results are retained under
`independent-warning-review/review-record.raw/final-artifacts/`. Required sensors
PASS; failures, unverified and deferred lists are empty. The controller checked
Q2/Q4 evidence references, confirmed scope and architectural invariants, and
accepted the prerequisite. The verifier's PENDING_CONTROLLER_ACCEPTANCE field
records its role boundary; this decision supplies controller acceptance.

Graphify incremental update passed (4,512 nodes, 11,731 edges); six known partial
Groovy parser results remain advisory limitations. Workflow-only checks are
recorded once in `../workflow-proportional-20260904/EVIDENCE.md` and passed.
