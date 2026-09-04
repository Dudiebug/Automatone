# Helper Agent Template — Milestone Gate

This is the same single fresh independent milestone verification described in
VERIFIER_AGENT.md, not a second review after a full preliminary run. Do not create
another reviewer when that verification already supplied valid clean evidence.

Use the finished clean candidate, the applicable milestone profile union and
milestone criteria. Run that profile once, deduplicating shared tasks. Return
failures for scoped repair; rerun only checks those repairs could invalidate.
Do not edit the candidate while grading it.

Required checks must pass or have explicit human exceptions, with no unresolved
scope/architecture violation, before ACCEPTED. Confirm source identity and
remaining obligations in the existing evidence record. No separate duplicate
integration report is required. QUALITY-CLEANUP uses this gate before M2.
