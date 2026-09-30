# architecture-doc

**Status**: IN PROGRESS
**Engram mirror**: `odd/architecture-doc/tasks`
**Branch**: `docs/architecture-doc`

## Objective

A `docs/architecture.md` that explains the architecture, technologies and main flows of the project,
and stays current as the code changes.

## Problem

The owner is new to AI-generated code and needs a reliable reference to understand what is being built.
A hand-written document goes stale silently.

## Scope

- Narrative sections (concepts, flows, known risks): written by hand, kept current by an `AGENTS.md` rule.
- Factual sections (technology versions, domains, ports and adapters, REST endpoints, migrations, frontend
  structure): generated from the code by a script, verified in CI.

## Constraints

- No new dependencies: the generator uses plain Node (already required by the frontend).
- Generated sections live between `<!-- BEGIN GENERATED: <name> -->` / `<!-- END GENERATED: <name> -->`
  markers; everything outside them is hand-written and never touched by the script.

## Tasks

- [x] T1 Generator script `scripts/architecture-doc.mjs` (write mode + `--check`), with `node:test` tests.
  Acceptance: tests pass; `--check` exits 1 when a section is stale and 0 when current.
  Evidence: RED (module missing) then 5/5 GREEN; `--check` exit 0 current, exit 1 after removing a row. Commit `385c1d5`.
- [x] T2 `docs/architecture.md`: narrative + generated sections.
  Acceptance: `--check` passes on the committed file.
  Evidence: `--check` exit 0. Commit `e3f7baa`.
- [x] T3 CI job running the tests and `--check`; `AGENTS.md` rule for the narrative; `CLAUDE.md` importing
  `AGENTS.md` so Claude Code sessions get the same rules.
  Acceptance: CI green on the PR.

## Delivery

Strategy: `single-pr`. Forecast ~400 authored lines (mostly documentation).

## Progress

- 2026-09-29: created; T1-T3 done locally.

## Next step

Push, open PR, confirm the new CI job is green.
