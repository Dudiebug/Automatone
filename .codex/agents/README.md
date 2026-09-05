# Automatone helper roles

Astra is the primary implementer and controller. Terra/Luna delegation is mandatory for test writing; Sol implementation/repair is optional.
Use them only when delegation adds value. Astra may work directly, reassign or
take over without asking the user. Every helper escalates directly to Astra;
no redelegation or automatic handoff chain. Astra reviews changes and evidence
and approves satisfactory work autonomously; failures still require repair.

The implementer/repair roles default to Sol; verifier/integration roles default
to Terra. `luna_old_coder` and `terra_old_coder` are the required test-authoring roles.
Use explicit Terra/Luna dispatch when a test role is unavailable; do not substitute another test author. Saved definitions may require a new session to become available;
never claim a role or model ran unless dispatch confirms it.

Keep one fresh independent milestone verification context separate from the
implementation/test authors. The verifier and integration role describe the same
gate, not two runs. Automated GameTests require no user participation. Ask only
for actual manual checks, missing access/input, or unresolved decisions outside
the approved scope. AGENTS.md and EXECUTION_STRATEGY.md define the full policy.

All test writing and modifications, including fixtures and test-harness repairs, must go to Terra or Luna. Astra defines acceptance criteria, reviews and integrates tests, and may run existing checks. Astra and Sol must not author or edit tests. If a test helper stalls, escalate directly to Astra for reassignment to Terra/Luna; do not substitute another test author.
