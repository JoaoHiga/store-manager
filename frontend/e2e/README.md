# Phase B — Full-Stack E2E (Playwright)

Drives the real React UI (Vite dev server) against the real Spring Boot backend, both booted by
Playwright itself, against a throwaway PostgreSQL container. This is the only test layer that
exercises the actual `fetch` call the frontend makes and the actual response it parses — the exact
boundary where both real bugs this SDD change exists for (`explicit-voucher-type-selection` and its
PR #22 follow-up) lived. Phase A (`infrastructure/src/test/java/com/storemanager/e2e/`) covers the
backend in isolation with Testcontainers; this covers the full path through a real browser.

## Prerequisites

- **Docker Desktop running** (WSL-integrated on Windows). `start-backend.sh` boots a dedicated,
  throwaway Postgres container on port **5433** — deliberately different from the real dev
  database on 5432, so a Phase B run never touches your actual local data.
- **Stop your normal `npm run dev` / backend** before running this suite. Phase B needs ports
  **5173** (frontend) and **8080** (backend) itself; `playwright.config.js` sets
  `reuseExistingServer: false` on purpose so a run fails loudly ("port already in use") instead of
  silently attaching to whatever else is already listening there. (This bit us once during
  development: an old leftover backend process from an earlier session got silently reused and the
  test wrote a real sale into the real dev database — see `apply-progress.md`'s PR2 Deviations.)
- `NUBEFACT_API_TOKEN` — optional. Without it, `IssueVoucherForSaleUseCase` still completes every
  sale (its own contract: an issuance failure never blocks or rolls back the sale) and persists the
  voucher as `PENDING` instead of `ACCEPTED`. This suite's assertions only check that the **chosen
  voucher type** (BOLETA/FACTURA) round-trips correctly through the real request → real response →
  real list — not that NubeFacT itself accepts the document — so it passes either way. Set this env
  var before `npm run test:e2e` if you want to test against a real NubeFacT sandbox account.

## Running

```bash
npm run test:e2e
```

This boots (in order Playwright manages itself): the Vite dev server, and `start-backend.sh`
(Postgres container on 5433 → Spring Boot on 8080 against it). Both are torn down after the run.

## Known limitation

If a run is interrupted (Ctrl-C, crash) before `start-backend.sh`'s cleanup trap fires, the
`store-manager-e2e-db` container can linger. The script defensively `docker rm -f`s any container
with that name before starting a new one, so the next run self-heals — but if you want to reclaim
resources immediately: `docker stop store-manager-e2e-db`.
