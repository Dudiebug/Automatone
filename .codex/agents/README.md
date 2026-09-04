# Automatone helper roles

Five project-local TOML role coordinators correspond to .agents/templates. They do not duplicate the Old Coder implementation: implementation, repair and verification execution route to the existing luna_old_coder role (GPT-5.6 Luna / Max). Mapper performs read-only Graphify preflight. The verifier and integration reviewer must use contexts separate from the implementer.

Standalone project roles use the documented name, description and developer_instructions fields: https://learn.chatgpt.com/docs/agent-configuration/subagents (checked 2026-09-01). No global config or permissions were changed. These are dispatchable roles, not a background service; installing them does not start work or accept results.

The current task's tool inventory did not expose newly installed custom names. Bootstrap therefore used a host-provided fixed Luna/Max role carrying luna_old_coder instructions. Discovery/reload of these five exact names in a new Codex task is UNVERIFIED; use the explicit fallback in AGENTS.md if unavailable, or stop. TOML validation alone is not a dispatch test.

Parent retains task selection, state transitions and user authority. No role can start another milestone, commit a dirty baseline, relax sensors or turn an unavailable check into PASS.
