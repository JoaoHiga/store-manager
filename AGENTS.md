# AGENTS.md

Guidance for AI coding agents working in this repository.

## Workflow

This repo uses **Organic Driven Development (ODD)**, not SDD/OpenSpec. SDD was retired on
2026-09-29; its artifacts were preserved as reference (see Documentation below).

- Small, understood work: explore, implement, check. No task artifact.
- Substantial work (two or more meaningful implementation steps): one feature document at
  `odd/tasks/<feature-name>.md`, mirrored to Engram topic `odd/<feature-name>/tasks`.
- Every task closes with a work-unit commit on a feature branch (Conventional Commits), with
  tests and docs alongside the behavior.
- Never merge PRs automatically; merging is always a separate, explicit user decision.

## Project context

- **Product**: inventory management web app for a family cellphone store (cellphones, phone
  accessories, speakers, cases, tempered-glass screen protectors, computer accessories, other
  related retail items). Peru electronic invoicing (SUNAT) via NubeFacT.
- **Backend**: Java 21 + Spring Boot REST API, multi-module Maven, Hexagonal Architecture.
  `application` has zero Spring dependencies; `infrastructure` holds Spring/JPA/REST adapters.
- **Frontend**: React 19 SPA (Vite, plain JS), atomic design + container/presentational,
  TanStack Query. Lives in `frontend/`.

## Conventions

- Follow existing code patterns.
- Never edit an already-applied Flyway migration (`infrastructure/src/main/resources/db/migration/`),
  not even its comments: Flyway validates checksums. Some of them reference old `openspec/changes/...`
  paths; the matching documents now live under `docs/history/<date>-<change>/`.
- Verify external integrations (NubeFacT, lookup providers) against the real service before
  designing around them; do not trust guessed field names or codes.

## Testing

TDD mode: **off as a gate** (no single command covers backend and frontend together), but work
has been done test-first in practice: write the failing test, confirm RED, then GREEN. Keep doing so.

| Scope | Command | Notes |
|---|---|---|
| Backend tests | `mvn clean test` | From the repo root. |
| Backend build | `mvn -q -DskipTests package` | |
| Frontend unit tests | `npm test` | In `frontend/` (Vitest). |
| Frontend build | `npm run build` | In `frontend/`. |
| Full-stack E2E | `npm run test:e2e` | In `frontend/` (Playwright). Needs Docker running; see `frontend/e2e/README.md`. |

**Closing rule**: the automated E2E suite must pass before a feature is closed. Never close a
feature with E2E marked pending. Twice, closing with manual E2E pending was followed by real bugs
found after merge.

## Documentation

- `docs/architecture.md`: how the system is built. Read it before architectural changes.
  - Generated sections (between `<!-- BEGIN GENERATED -->` markers) come from the code. Never edit them
    by hand: run `node scripts/architecture-doc.mjs` whenever a change touches dependencies, domains,
    ports, adapters, controllers, migrations or frontend structure. CI fails if they are stale.
  - Hand-written sections (concepts, wiring, flows, known risks) must be updated **in the same change**
    that makes them inaccurate. Add a risk to "Known risks and gaps" when you find one; remove it in the
    change that fixes it.
- `docs/requirements/<domain>/spec.md`: current requirements for `invoicing`, `product-catalog`
  and `sales`. Read these during exploration; update them when behavior changes.
- `docs/history/<date>-<change>/`: completed SDD changes (proposal, design, tasks, verify reports).
  Historical record only; do not edit.
- `odd/tasks/`: ODD feature documents for active or paused work.
