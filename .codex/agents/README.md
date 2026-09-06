# Automatone helper roles

Astra is the primary implementer and controller, and owns the testing foundation
and specs. Default to GPT-5.6 Luna at max reasoning for substantial feature tests,
with Astra reviewing assertions and executed results. Sol implementation/repair
is optional. Astra may author tests, handle small repairs directly, reassign or
take over without asking the user. Every helper escalates directly to Astra;
no redelegation or automatic handoff chain. Astra reviews changes and evidence
and approves satisfactory work autonomously; failures still require repair.

The implementer/repair roles default to Sol; verifier/integration roles default
to Terra. Dispatch routine test assignments to GPT-5.6 Luna with max reasoning
and an explicit bounded spec; `luna_old_coder` and `terra_old_coder` are available
when Old Coder is warranted, not required for ordinary test writing.
Saved definitions may require a new session to become available;
never claim a role or model ran unless dispatch confirms it.

Keep one fresh independent milestone verification context separate from the
implementation/test authors. The verifier and integration role describe the same
gate, not two runs. Automated GameTests require no user participation. Ask only
for actual manual checks, missing access/input, or unresolved decisions outside
the approved scope. AGENTS.md and EXECUTION_STRATEGY.md define the full policy.

Follow the test-authoring workflow in EXECUTION_STRATEGY.md for requirement
mapping, allowed mocks, fixture/reset rules, reference tests and review criteria.
Helpers escalate contradictions or stalled work directly to Astra, who may take
over without user approval. Production helpers retain their test-editing limits.
