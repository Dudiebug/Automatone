# Task Specs

This directory contains durable executable task specifications distilled from the approved product plan.

The bootstrap workflow should generate one file per plan task, preserving existing task IDs such as `M1.1`, `M1.2`, etc.

Task specs are not a replacement for the master plan. They are the controller-facing compilation of one bounded task into:

- objective;
- dependencies;
- source/reference anchors;
- allowed and forbidden scope;
- preflight questions;
- acceptance criteria;
- sensor profiles;
- blocker conditions.

A task may be marked `READY` only when every blocking acceptance criterion has an observable evidence path.
