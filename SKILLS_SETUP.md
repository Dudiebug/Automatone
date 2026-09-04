# Skill Setup Notes

The workflow assumes two external skills/tools are available to the coding agent. Do not vendor their source into this repository unless you intentionally want project-scoped copies and their licenses/maintenance model are acceptable.

## old-coder

Purpose in this workflow:
- evidence-first implementation;
- explicit test/gauntlet plan before coding;
- truthful reporting of what actually ran;
- final evidence rather than self-attested completion.

Typical install command documented by the upstream project:

```bash
npx skills add https://github.com/amazingang/old-coder --skill old-coder
```

If your agent host uses project-local skill directories, install/copy it using that host's supported mechanism instead.

Upstream:
`https://github.com/AmazingAng/old-coder`

## Graphify-Labs Graphify

Purpose in this workflow:
- persistent structural map of the repository;
- scoped `query`, `path`, and `explain` preflight;
- impact/relationship context before implementation;
- incremental post-change graph refresh;
- duplicate-responsibility/topology awareness complementary to deterministic CPD/architecture checks.

Graphify-Labs currently documents the PyPI package as `graphifyy` while the CLI is `graphify`.

A typical Codex-oriented project setup is:

```bash
uv tool install graphifyy
graphify codex install --project
```

If Graphify is already installed/configured, do not reinstall it blindly.

Useful repository commands once installed/configured:

```bash
graphify query "<question>"
graphify path "<A>" "<B>"
graphify explain "<concept>"
graphify update .
```

Upstream:
`https://github.com/Graphify-Labs/graphify`

## Important distinction

Graphify is primarily a **state/structure sensor**. It is not by itself a hard correctness gate. Use deterministic sensors such as tests, ArchUnit, SpotBugs, CPD, and GameTests for binary acceptance conditions wherever possible.
