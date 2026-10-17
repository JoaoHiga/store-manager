# Phase B — Full-Stack E2E (Playwright)

Drives the real React UI (Vite dev server) against the real Spring Boot backend, both booted by
Playwright itself, against a throwaway PostgreSQL container. This is the only test layer that
exercises the actual `fetch` call the frontend makes and the actual response it parses — the exact
boundary where both real bugs this suite was built for (`explicit-voucher-type-selection` and its
PR #22 follow-up) lived. Phase A (`infrastructure/src/test/java/com/storemanager/e2e/`) covers the
backend in isolation with Testcontainers; this covers the full path through a real browser.

Specs:

- `sale-voucher-flow.spec.js` — a sale's chosen voucher type (BOLETA/FACTURA) round-trips through the
  real request, response and sales list.
- `purchase-flow.spec.js` — create a supplier, record a purchase, see it listed, check the raised stock
  and the weighted-average cost price (read through `GET /api/products`, since no page shows cost), and
  see a duplicate supplier invoice rejected with the backend's `409` message. It generates a random valid
  RUC per run, so it also passes against a reused Postgres container.

## Prerequisites

- **Playwright's Chromium** installed once per machine: `npx playwright install chromium` (in
  `frontend/`). Without it every spec fails at `browserType.launch` with "Executable doesn't exist";
  it also has to be re-run after a `@playwright/test` upgrade.
- **Docker Desktop running** (WSL-integrated on Windows). `playwright.config.js` boots a dedicated,
  throwaway Postgres container on port **5433** — deliberately different from the real dev
  database on 5432, so a Phase B run never touches your actual local data.
- **Stop your normal `npm run dev` / backend** before running this suite. Phase B needs ports
  **5173** (frontend) and **8080** (backend) itself; those two `webServer` entries use
  `reuseExistingServer: false` on purpose so a run fails loudly ("port already in use") instead of
  silently attaching to whatever else is already listening there. (This bit us once during
  development: an old leftover backend process from an earlier session got silently reused and the
  test wrote a real sale into the real dev database — see `apply-progress.md`'s PR2 Deviations.)
  The Postgres entry (port 5433) is the one exception: it uses `reuseExistingServer: true`, safely —
  see Known limitation below for why.
- `NUBEFACT_API_TOKEN` / `NUBEFACT_BASE_URL` — optional. Without them, `IssueVoucherForSaleUseCase`
  still completes every sale (its own contract: an issuance failure never blocks or rolls back the
  sale) and persists the voucher as `PENDING` instead of `ACCEPTED`. This suite's assertions only
  check that the **chosen voucher type** (BOLETA/FACTURA) round-trips correctly through the real
  request → real response → real list — not that NubeFacT itself accepts the document — so it
  passes either way.

  To test against a real NubeFacT sandbox account locally, without ever typing the token into a
  chat or shell history: create `.env.nubefact.local` at the repo root (that exact filename is
  gitignored) with:

  ```
  NUBEFACT_API_TOKEN=your-real-token
  NUBEFACT_BASE_URL=your-real-sandbox-url
  ```

  `start-backend.sh` sources it automatically before starting the backend; nothing else to export
  or configure. For CI, add
  `NUBEFACT_API_TOKEN` as a repository secret instead (`Settings → Secrets and variables → Actions`)
  — `.github/workflows/e2e.yml` already forwards it.

## Running

```bash
npm run test:e2e
```

This boots (in parallel, Playwright manages each as its own `webServer` entry): the Vite dev
server, a throwaway Postgres container (port 5433), and `start-backend.sh` (waits for that
Postgres port, then runs the real Spring Boot app against it). The frontend and backend are
reliably killed by Playwright after the run; the Postgres container is not (see below) — this is
expected, not a bug to chase further.

## Known limitation

The Postgres container **intentionally lingers after a run** rather than being torn down. This
was investigated directly (not just observed and shrugged off): a `docker run` in the foreground
only stops its container when the signal it receives is one Docker's CLI relays to the container
(SIGTERM/SIGINT) — whatever tears down Playwright's `webServer` processes in this environment
doesn't reliably do that, so `--rm` never gets the chance to fire. The same race existed even with
a bash-script-owned container + an EXIT/INT/TERM trap around `docker stop` — moving ownership to
Playwright's own process management didn't change the outcome, which is why this is documented as
an accepted characteristic rather than chased further with more signal-handling machinery.

Because a stray container on port 5433 is *always* this suite's own leftover (nothing else
legitimately binds that port — unlike 8080/5173, which a real dev server could), the Postgres
`webServer` entry uses `reuseExistingServer: true`: the next run reuses it directly instead of
erroring on a port collision. This is safe because every run is independent regardless of prior
state — Flyway's migrations are idempotent and each test seeds its own uniquely-named fixtures.

To reclaim the resources between sessions (not required for correctness): `docker stop
store-manager-e2e-db`.
