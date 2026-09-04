# WORKFLOW-PROPORTIONAL — proportional verification

Human-approved workflow-only change, 2026-09-04. No product implementation.

Update AGENTS.md, EXECUTION_STRATEGY.md, relevant task/role templates and the
PowerShell runner to select focused task checks, record milestone deferrals as
PENDING, and support one clean fresh milestone profile without duplicate runs.
Keep architecture, valid regressions, failure reporting and explicit exceptions.
Use Luna only for small Old Coder test work; parent owns implementation.

Focused acceptance: PowerShell workflow regression checks demonstrate explicit
selection/filter forwarding, pending deferrals, risk-based broad selection,
deduplicated clean milestone execution, failure/UNVERIFIED integrity and no
automatic task state or baseline mutation. Check that active documentation and
role instructions match the runner. No Java suite, GameTest, SpotBugs, CPD,
mutation or duplicate independent review is needed for these workflow-only edits.

Apply cadence to QUALITY-CLEANUP without restarting valid completed checks.
Its unresolved findings and prerequisite milestone acceptance remain PENDING
before M2. Keep one evidence record under
`.agents/evidence/workflow-proportional-20260904/`.
