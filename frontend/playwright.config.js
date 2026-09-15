import { defineConfig } from '@playwright/test';

// Phase B (automated-e2e-testing, design.md): drives the real React UI
// against the real Spring Boot backend — the only layer that would have
// caught both real bugs found so far (a dropped `voucherType` in the
// frontend's outgoing request, and the list not reflecting the actual
// voucher type). `webServer` boots both real processes; Playwright waits for
// each `url`/`port` before running any test.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  fullyParallel: false,
  // Observed real CI flakiness (PR3, post-merge run against main): a
  // GET /api/sales response occasionally took >30s on a resource-constrained
  // runner, timing out one scenario while the identical code passed cleanly
  // on the very next run. One retry in CI absorbs that without masking a
  // real regression — a genuine bug fails both the run and its retry.
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
  },
  webServer: [
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      // Never reuse whatever happens to already be on this port — a stray
      // dev server (this project's own default port) would otherwise get
      // silently adopted and tested against instead of this branch's code.
      // Fail loud ("port already in use") instead of running against the
      // wrong process. See apply-progress.md PR2 Deviations for the
      // incident this guards against.
      reuseExistingServer: false,
      timeout: 60_000,
    },
    {
      // `reuseExistingServer: true` — unlike ports 8080/5173 (which a
      // developer's own unrelated dev servers could legitimately occupy,
      // the exact incident documented in apply-progress.md), port 5433 is
      // this suite's own exclusive convention; nothing else legitimately
      // binds it. Confirmed empirically (this session): when Playwright's
      // teardown reaches a foreground `docker run`, Docker's client only
      // forwards the signal to the container on SIGTERM/SIGINT — a harder
      // kill leaves it running orphaned, `--rm` notwithstanding, and
      // Playwright's own `reuseExistingServer: false` port pre-check then
      // refuses to even attempt starting a new one (it never runs the
      // command at all once it sees the port occupied). Reusing a leftover
      // instance here is safe: same image, same fixed schema via Flyway
      // (idempotent), and each test seeds its own uniquely-named fixtures.
      command:
        'docker run --rm --name store-manager-e2e-db -p 5433:5432 -e POSTGRES_DB=store_manager -e POSTGRES_USER=store_manager -e POSTGRES_PASSWORD=store_manager postgres:16-alpine',
      port: 5433,
      reuseExistingServer: true,
      timeout: 60_000,
    },
    {
      command: 'bash e2e/start-backend.sh',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: false,
      timeout: 180_000,
    },
  ],
});
