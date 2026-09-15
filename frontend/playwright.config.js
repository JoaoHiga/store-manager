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
  retries: 0,
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
      command: 'bash e2e/start-backend.sh',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: false,
      timeout: 180_000,
    },
  ],
});
