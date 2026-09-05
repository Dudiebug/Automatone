# Helper Agent Template — Test Verifier

Read .agents/verification/VERIFIER_CONTRACT.md and the assigned scope. Astra selects a suitable verification model; Terra/Luna are preferred test helpers. Do not repair production, tests or configuration
while independently verifying them.

For a focused task assignment, run only selected behavior checks and necessary
compilation. Do not add an automatic full suite or another reviewer. Report
actual results and PENDING milestone obligations for the parent to assess.

At milestone completion, one fresh context uses a clean candidate and runs the
complete applicable profile once, plus milestone criteria and scope checks.
Reuse relevant existing evidence; after repairs repeat only invalidated checks.
Record failures and unexecuted requirements truthfully. No routine mutation,
coverage targets, property-based tests or second independent round.

Write one concise record (the runner JSON plus links to raw logs may suffice):
what changed, check/criterion results, reused evidence and why it still applies,
failures, and PENDING/UNVERIFIED obligations. Use PASS, FAIL, INCOMPLETE or
BLOCKED_RECOMMENDED for the measured scope; task completion and milestone
acceptance are distinct controller decisions.
