# Archive Report: automated-e2e-testing

**Archived**: 2026-09-15
**Archived to**: `openspec/changes/archive/2026-09-15-automated-e2e-testing/`

## Final State

All 17 tasks across 3 chained PRs are complete and merged to `main`:

| PR | Scope | Status |
|---|---|---|
| [#23](https://github.com/JoaoHiga/store-manager/pull/23) | Phase A — Testcontainers backend integration E2E | Merged |
| [#24](https://github.com/JoaoHiga/store-manager/pull/24) | Phase B — Playwright full-stack E2E | Merged |
| [#25](https://github.com/JoaoHiga/store-manager/pull/25) | CI workflow + archive-gate rule | Merged |

`verify-report.md`'s recorded verdict (**PASS**) predates one final-state update worth noting explicitly, per this phase's Final-State Authority: PR3's own post-merge CI runs (against final `main`, after PR1+PR2 landed) surfaced one real, transient flake — a `page.waitForResponse` timeout on the BOLETA scenario on a resource-constrained runner — that did not reproduce on an immediate re-run of the identical commit. This was diagnosed as CI-runner timing flakiness, not a code defect (the FACTURA scenario in the same run, and both scenarios on every other run including two after, passed cleanly). Mitigated with `retries: process.env.CI ? 1 : 0` in `playwright.config.js`, added and merged as part of PR3. The final CI run before merge (https://github.com/JoaoHiga/store-manager/actions/runs/35020286619) passed all 3 jobs cleanly with that fix in place. This does not change `verify-report.md`'s PASS verdict — it is recorded here because it happened after that report was written.

## Specs Synced

None. `proposal.md` explicitly declared "New Capabilities: None" / "Modified Capabilities: None" — this change adds test/CI infrastructure covering the existing `sales`/`invoicing` capabilities' already-specified behavior; it introduces no new spec-level requirements. No `openspec/specs/` files were touched.

## Archive Contents

- exploration.md ✅
- research.md ✅
- proposal.md ✅
- design.md ✅ (no `specs/` — by design, see above)
- tasks.md ✅ (17/17 tasks complete)
- apply-progress.md ✅ (3 PR sections: PR1, PR2, PR3)
- verify-report.md ✅ (PASS)

## Mechanical Copy Verification

```
$ git mv openspec/changes/automated-e2e-testing openspec/changes/archive/2026-09-15-automated-e2e-testing
$ diff -r <pre-move snapshot> openspec/changes/archive/2026-09-15-automated-e2e-testing
$ echo $?
0
```

Empty diff — byte-identical move, confirmed.

## Retroactive Application of This Change's Own Gate

`openspec/config.yaml`'s new `rules.archive` entry (added by this same change, PR3) reads: *"Automated E2E suite must pass before archive; do not archive with E2E marked pending."* This archive is the first one that rule applies to — and it applies to itself first: `mvn clean test` (238/238), `npm test` (50/50), and `npm run test:e2e` (2/2, both locally and independently on CI) all passed before this archive ran, with no E2E work left pending.

## Known Follow-Ups (not fixed in this change, flagged for a future one)

1. `SalesListPage.jsx` has a stale code comment claiming `voucherStatus` is always `null` in the sales list — refuted by this change's own `SaleFlowE2ETest`. Low-risk cosmetic cleanup.
2. `frontend/e2e/start-backend.sh`'s Docker container cleanup on an interrupted run is best-effort, not guaranteed (documented limitation in `frontend/e2e/README.md`, self-heals on the next run).

## SDD Cycle Complete

The change has been fully planned (explore → research → propose → design → tasks), implemented across 3 chained PRs with real RED→GREEN TDD evidence, independently verified (PASS), and archived. Ready for the next change.
