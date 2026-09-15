# Tasks: Automated E2E Testing + Pre-Archive Gate

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~550-750 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (Phase A) → PR 2 (Phase B) → PR 3 (CI + archive gate) |
| Delivery strategy | ask-on-risk |
| Chain strategy | stacked-to-main (matches all 5 prior changes in this repo) — confirmed by user |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|------|------|-----------|----------------------|------------------|--------------------|
| 1 | Phase A: Testcontainers integration suite | PR 1 | `mvn -pl infrastructure test -Dtest=SaleFlowE2ETest` | Real Postgres via Testcontainers (Docker required) | New test class + pom dep, fully revertable alone |
| 2 | Phase B: Playwright full-stack E2E | PR 2 | `npm run test:e2e` (frontend) | Vite + Spring Boot (sandbox profile) + NubeFacT sandbox | New e2e dir + config + devDependency |
| 3 | CI workflow + archive gate rule | PR 3 | N/A — workflow validated by its own first CI run | GitHub Actions `ubuntu-latest` | New workflow file + config.yaml rule, independently revertable |

## Phase 1: Phase A — Backend Integration E2E (TDD)

- [x] 1.1 RED: Add `org.testcontainers:postgresql` + `junit-jupiter` (test scope) to `infrastructure/pom.xml`; write `SaleFlowE2ETest` skeleton asserting `POST /sales` (BOLETA) returns 201 — fails (no Testcontainers wiring yet).
- [x] 1.2 GREEN: Add `@Testcontainers`/`@Container`/`@ServiceConnection` `PostgreSQLContainer` wiring so Flyway runs real migrations; test passes.
- [x] 1.3 RED→GREEN: Add scenario — record FACTURA with valid RUC succeeds; record FACTURA without RUC returns 4xx.
- [x] 1.4a DISCOVERED: `SaleResponse` (`infrastructure/.../SaleResponse.java`) does not expose the voucher's `type` (BOLETA/FACTURA) at all — only `voucherStatus`. User confirmed: add `voucherType` field, RED-first. RED: E2E assertion `$.voucherType` fails (field doesn't exist). GREEN: add `voucherType` to the `SaleResponse` record, populate from `voucher.getType().name()` in `SaleResponse.from`, mirroring how `VoucherResponse` already exposes `type`.
- [x] 1.4b RED→GREEN: Add scenario — `GET /sales` after recording BOLETA+FACTURA reflects each with its correct `voucherType` (the exact bug class from `explicit-voucher-type-selection`). Also confirms/refutes the pre-existing "voucherStatus always null in list" gap noted in `SalesListPage.jsx`'s comment. **Result: refuted** — `voucherStatus`/`voucherType` are correctly populated in the real list response; the frontend comment is stale documentation, not a live bug (flagged as a follow-up, not fixed in this PR).
- [x] 1.5 RED→GREEN: Add scenarios for `POST /sales/{id}/void` and `POST /sales/{id}/retry-voucher`.
- [x] 1.6 REFACTOR: Extract shared test fixtures (sample product/category setup) if duplicated across scenarios.

## Phase 2: Phase B — Full-Stack Playwright E2E (TDD)

- [x] 2.1 Add `@playwright/test` devDependency + `test:e2e` script to `frontend/package.json`.
- [x] 2.2 Create `frontend/playwright.config.js` with multi-`webServer` (Vite dev server + `frontend/e2e/start-backend.sh`, a throwaway Postgres container on port 5433 + the real Spring Boot app against it — port 5432/real dev DB never touched).
- [x] 2.3 REVISED (no separate profile — no confirmed distinct sandbox base URL exists for NubeFacT; the existing default profile already reads `NUBEFACT_API_TOKEN` from the environment and degrades gracefully to `PENDING` when absent/invalid, per `IssueVoucherForSaleUseCase`'s own contract). Documented in `frontend/e2e/README.md` instead.
- [x] 2.4 RED: Wrote `frontend/e2e/sale-voucher-flow.spec.js` — real failure while `start-backend.sh`'s Maven invocation was misconfigured (see Deviations), then real failures debugging port/config issues before the stack ever ran correctly.
- [x] 2.5 GREEN: Both scenarios pass — the real POST/GET network responses (intercepted via `page.waitForRequest`/`page.waitForResponse`) show the exact voucher type chosen (BOLETA and FACTURA, triangulated), independent of whether NubeFacT itself accepts the document.
- [x] 2.6 Documented the required local env var (`NUBEFACT_API_TOKEN`), Docker Desktop prerequisite, and the port-collision incident in `frontend/e2e/README.md`.

## Phase 3: CI + Pre-Archive Gate

- [ ] 3.1 Create `.github/workflows/e2e.yml` running Phase A (`mvn -pl infrastructure test -Dtest=SaleFlowE2ETest`) and Phase B (`npm run test:e2e`) on push/PR; pass `NUBEFACT_SANDBOX_TOKEN` from repo secrets.
- [ ] 3.2 Pin a `testcontainers-java` version verified against the Docker 29.1 regression (check current release notes at implementation time — `research.md` gap).
- [ ] 3.3 Add `rules.archive: - "Automated E2E suite must pass before archive; do not archive with E2E marked pending"` to `openspec/config.yaml`.
- [ ] 3.4 Verify the new CI workflow runs green on its own PR before merge.

## Rules Applied

`openspec/config.yaml` has no `rules.tasks` yet (first change to add `rules.archive`) — no additional constraints to apply beyond this skill's defaults.
