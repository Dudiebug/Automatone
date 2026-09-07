# Mining Jobs

## Configure a job

Search registered blocks by name or registry ID and select up to 128 target types.
A finite quantity from 1 to 1,000,000 counts matching source blocks successfully
destroyed across all selected targets. Drops, pickups, and existing inventory do
not count toward that quota. Select **Unlimited** for a job with no quota.

Workers use native Automatone target discovery, pathfinding, movement, breaking,
and placement. They need suitable real tools and any building supplies required by
the route. Job behavior can also depend on the worker's mining settings.

## Controls and states

- **Start** begins a new run and resets its completed count.
- **Pause** stops work while retaining the job and progress.
- **Resume** continues the paused run from its remaining amount.
- **Stop** cancels the run and retains the completed count for display.
- **Apply job** copies targets and quantity to selected workers; replacing active
  work requires confirmation.

Finite work ends at the requested source-block count. Unlimited work continues until
paused, stopped, or terminated by an error. A job that was running at normal server
shutdown resumes after restart and chunk readiness. Paused and terminal jobs do not.

Common terminal messages explain missing targets, unreachable remaining targets,
disabled breaking, interruption, cancellation, completion, or an internal failure.
See [Troubleshooting](https://github.com/Dudiebug/Automatone/wiki/Troubleshooting) for
specific actions.
