# Proposal: Automated E2E Testing + Pre-Archive Gate

## Intent

Two real bugs (`Sale.reconstitute` sharing derive-logic so only BOLETA ever showed in the list; `salesApi.recordSale` dropping `voucherType` entirely) reached `main` after their changes were archived, because neither the backend unit tests, the mocked frontend unit tests, nor a mandatory pre-archive check caught a UI↔API contract break. Close that gap with phased automated E2E coverage of the sale-record → voucher-type → list path, and make passing E2E a structural precondition for archiving any future change — not a note that gets deferred.

## Scope

### In Scope
- **Phase A**: Testcontainers (real Postgres + Flyway) integration tests for `SaleController`: record sale as BOLETA, record sale as FACTURA (RUC), list reflects the correct voucher type, void, retry-voucher.
- **Phase B**: Playwright full-stack E2E driving the real React UI against the real running backend and the real NubeFacT sandbox, covering the same path through an actual browser.
- Minimal GitHub Actions workflow (`.github/workflows/`) running both suites on push/PR — this repo currently has no CI at all.
- New `rules.archive` entry in `openspec/config.yaml` requiring the automated E2E suite pass before archive, plus a required task template for future `tasks.md` delivery checklists.

### Out of Scope
- Extending coverage to inventory/categories or other document types beyond BOLETA/FACTURA+RUC.
- Visual regression or load/performance testing.
- Replacing or rewriting the existing backend/frontend unit test suites.

## Capabilities

### New Capabilities
None — this is test/CI infrastructure, not user-facing behavior.

### Modified Capabilities
None — no `sales`/`invoicing` requirement changes; the archive-gate rule is a process constraint recorded in `openspec/config.yaml`, not a spec delta.

## Approach

1. Add Testcontainers (`postgresql` module) to `infrastructure/pom.xml`; write `SaleControllerE2ETest` using `@ServiceConnection` so Flyway runs real migrations against a real Postgres (confirmed zero-config pattern — see `research.md` C1).
2. Add Playwright as a frontend devDependency; configure `webServer` as an array booting both the Vite dev server and the Spring Boot app (confirmed native support — see `research.md` C2), pointed at the NubeFacT sandbox via a CI secret.
3. Add `.github/workflows/e2e.yml` running Phase A + Phase B on `ubuntu-latest` (Docker preinstalled, no extra setup — see `research.md` C3); pin a `testcontainers-java` version known to avoid the Docker 29.1 regression (`research.md` gap).
4. Add `rules.archive: - "Automated E2E suite must pass before archive; do not archive with E2E marked pending"` to `openspec/config.yaml`.

## Affected Areas

| Area | Impact | Description |
|------|--------|--------------|
| `infrastructure/pom.xml` | Modified | Add Testcontainers test-scope dependency |
| `infrastructure/src/test/java/.../SaleControllerE2ETest.java` | New | Phase A integration suite |
| `frontend/package.json` | Modified | Add Playwright devDependency |
| `frontend/e2e/` (new dir) | New | Phase B Playwright specs + config |
| `.github/workflows/e2e.yml` | New | CI running both suites |
| `openspec/config.yaml` | Modified | New `rules.archive` entry |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Docker Desktop not running locally blocks Phase A/B silently | Medium | Document as explicit prerequisite; CI always has Docker |
| `testcontainers-java` + Docker 29.1 regression (GH issue #11491) | Low-Medium | Pin a verified-compatible version in design phase |
| NubeFacT sandbox flakiness/rate limits break CI runs (user chose real sandbox over a stub) | Medium | Accepted tradeoff; document retry/backoff expectations in design |
| CI scope creep beyond "run these two suites" | Low | Keep workflow minimal — no linting/build matrix expansion here |

## Rollback Plan

All changes are additive (new test files, new CI workflow, new config rule) with zero production code impact. Revert the PR(s); no data or runtime behavior is affected.

## Dependencies

- Docker Desktop running locally (WSL-integrated) for developers running Phase A/B.
- NubeFacT sandbox credentials available as a GitHub Actions secret for Phase B CI runs.

## Success Criteria

- [ ] Phase A suite passes locally and in CI, covering BOLETA/FACTURA record + list + void + retry-voucher against real Postgres.
- [ ] Phase B suite passes locally and in CI, covering the same path through a real browser against the real NubeFacT sandbox.
- [ ] `.github/workflows/e2e.yml` runs both suites on every push/PR.
- [ ] `openspec/config.yaml` has the new archive-gate rule; future `sdd-archive` runs are expected to honor it.
