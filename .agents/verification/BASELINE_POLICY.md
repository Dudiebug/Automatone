# Baseline Policy

Static-analysis and quality tools are most useful when the controller can distinguish **pre-existing debt** from **candidate-introduced debt**.

## Initial baseline

Before product implementation begins:

1. run every configured default sensor against the accepted repository state;
2. capture exact tool versions/configuration and commit SHA;
3. record existing findings without altering the code merely to make the bootstrap green;
4. decide whether each sensor will initially gate on:
   - zero total findings; or
   - zero **new** findings relative to the approved baseline.

Human/planner approval is required to accept baseline debt for a required sensor.

## Rules

- Never regenerate the baseline after a candidate change and call the candidate's findings "existing".
- Never baseline secrets, known critical dependency vulnerabilities, or a deterministic failure that invalidates the target runtime merely for convenience.
- Prefer paying down easy baseline debt during a dedicated tooling/cleanup task rather than proliferating suppressions.
- Baseline files/reports must identify the commit they describe.
- If analyzer configuration changes materially, run a deliberate baseline migration and explain the delta.

## New-code policy

At minimum, candidate changes should not:
- introduce new high-confidence correctness bugs;
- introduce new architecture violations;
- increase duplicate implementation beyond the configured threshold;
- reduce required changed-code coverage below the approved threshold when coverage is selected;
- add a vulnerable dependency without an explicitly reviewed reason/mitigation.
