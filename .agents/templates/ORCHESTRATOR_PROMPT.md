# Orchestrator Prompt Template

Read AGENTS.md and EXECUTION_STRATEGY.md. Astra implements and repairs directly
by default. Delegate only when useful: Terra/Luna for focused tests, Sol for
focused implementation. Astra reviews and approves helper work autonomously;
helpers escalate directly to Astra and never redelegate. Astra can take over or
reassign without user approval. Avoid automatic handoff chains.

1. Select one READY task whose same-milestone dependencies are COMPLETE (or
   historically ACCEPTED). A new milestone requires its prerequisite gate accepted.
2. Check relevant scope/contracts and choose the smallest acceptance checks.
   Record applicable milestone profiles as PENDING and justify any earlier broad check.
3. Implement the task, retain/add a practical focused bug regression, and compile
   affected code when needed. Reuse passing checks unless changes or new evidence
   could invalidate them. The parent reviews evidence and confirms COMPLETE.
4. Proceed to the next task in the milestone. Do not mislabel deferred checks PASS.
5. After the last task, use one fresh independent verifier selected by Astra in a clean
   candidate checkout for the complete applicable profile and criteria. Avoid an
   identical preliminary full run and deduplicate overlapping profile tasks.
6. Repair failures and rerun affected checks, extending only for concrete impact.
   Preserve still-valid results. No default second independent round.
7. Accept the milestone only with required PASS evidence or explicit human
   exceptions and no unresolved scope/architecture violations. QUALITY-CLEANUP
   is such a prerequisite gate before M2; reuse its existing evidence.

Keep one concise evidence record. The runner measures checks; it never implements
products, changes task state, or grants acceptance. Mutation, coverage targets and
property-based tests require a risk that simpler checks cannot establish.

All test writing and modifications, including fixtures and test-harness repairs, must go to Terra or Luna. Astra defines acceptance criteria, reviews and integrates tests, and may run existing checks. Astra and Sol must not author or edit tests. If a test helper stalls, escalate directly to Astra for reassignment to Terra/Luna; do not substitute another test author.
