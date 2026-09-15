# Apply Progress: Automated E2E Testing + Pre-Archive Gate

## PR1 — Phase A (Backend Integration E2E via Testcontainers): DONE

**Mode**: Strict TDD

### Completed Tasks
- [x] 1.1 Testcontainers deps added to `infrastructure/pom.xml`; `SaleFlowE2ETest` skeleton (RED confirmed: class didn't exist, `mvn test -Dtest=SaleFlowE2ETest` failed with "No tests matching pattern").
- [x] 1.2 `@Testcontainers`/`@Container`/`@ServiceConnection` `PostgreSQLContainer` wiring — GREEN, Flyway runs all 6 real migrations against real Postgres.
- [x] 1.3 FACTURA+RUC succeeds / FACTURA without RUC → 400 — GREEN, characterizes existing `RecordSaleUseCase` behavior at the new integration layer.
- [x] 1.4a `voucherType` added to `SaleResponse` — RED confirmed (`expected: "BOLETA" but was: null`), then GREEN.
- [x] 1.4b List reflects each sale's own voucher type — GREEN. Also **refutes** `SalesListPage.jsx`'s comment claiming `voucherStatus` is always null in the list — confirmed stale, not a live bug (see Deviations).
- [x] 1.5 Void (ACCEPTED → CANCELLED) and retry-voucher (PENDING → ACCEPTED) scenarios — GREEN, characterizes existing `VoidSaleUseCase`/`RetryPendingVoucherUseCase` behavior at the new layer.
- [x] 1.6 Refactor: extracted `recordSale(...)` helper, removed duplicated response-parsing across 4 test methods — still GREEN after refactor.

### Files Changed
| File | Action | What Was Done |
|------|--------|----------------|
| `infrastructure/pom.xml` | Modified | Added `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` (test scope) |
| `infrastructure/src/test/java/com/storemanager/e2e/SaleFlowE2ETest.java` | Created | 6 tests: record BOLETA/FACTURA, reject FACTURA without RUC, list reflects voucher type, void, retry-voucher |
| `infrastructure/src/main/java/com/storemanager/sales/infrastructure/web/SaleResponse.java` | Modified | Added `voucherType` field, populated from `voucher.getType().name()` |

### TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.2 | `SaleFlowE2ETest.java` | Integration (Testcontainers) | N/A (new file) | ✅ Written (class-not-found) | ✅ Passed | ➖ Single scenario | ✅ Clean |
| 1.3 | `SaleFlowE2ETest.java` | Integration | ✅ existing suite unaffected | ✅ Written | ✅ Passed | ✅ 2 cases (RUC accepted, non-RUC rejected) | ✅ Clean |
| 1.4a-1.4b | `SaleFlowE2ETest.java` + `SaleResponse.java` | Integration | ✅ `SaleControllerTest` 24/24 still passing after the field addition | ✅ Written (`expected: "BOLETA" but was: null`) | ✅ Passed | ✅ 2 cases (BOLETA, FACTURA) | ✅ Clean |
| 1.5 | `SaleFlowE2ETest.java` | Integration | ✅ existing suite unaffected | ✅ Written | ✅ Passed | ✅ 2 cases (void, retry) | ✅ Clean |

### Test Summary
- **Total tests written**: 6 (`SaleFlowE2ETest`)
- **Total tests passing**: 6/6, plus full `infrastructure` module regression: 96/96 (0 failures, 0 errors)
- **Layers used**: Integration (6, Testcontainers + real Postgres + Flyway)
- **Approval tests** (refactoring): None — no refactoring tasks in PR1
- **Pure functions created**: 0 (test infrastructure + one DTO field addition)

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused test command and exact result | `mvn -pl infrastructure -am test -Dtest=SaleFlowE2ETest -Dsurefire.failIfNoSpecifiedTests=false` → Tests run: 6, Failures: 0, Errors: 0 |
| Runtime harness command/scenario and exact result | Real Testcontainers PostgreSQL 16.15 container, real Flyway migration of all 6 project migrations, real Spring context boot — confirmed via surefire report and Flyway migration log |
| Rollback boundary | `infrastructure/pom.xml`'s 3 new test-scope deps, the new `SaleFlowE2ETest.java` file, and the additive `voucherType` field on `SaleResponse` — all independently revertable without touching PR2/PR3 |

### Deviations from Design
1. **Environment gap discovered and fixed, not in original design**: local Docker Engine 29.7.2 + Testcontainers 1.19.8 (Spring Boot 3.3.4's managed version) hit the known `testcontainers-java` Docker-29.x incompatibility (confirmed via `testcontainers/testcontainers-java#11212`, GitHub's fix: upgrade to 2.0.x, no 1.x backport). Worked around locally with `System.setProperty("api.version", "1.44")` in a static initializer rather than upgrading to Testcontainers 2.0.x (which renames every artifact and may not be compatible with Spring Boot 3.3.4's `spring-boot-testcontainers` module — a bigger, riskier change than this PR's scope). **Flagged as a risk for PR3 (CI)**: GitHub Actions' `ubuntu-latest` may run a different Docker Engine version than local; if CI hits the same incompatibility, the same `api.version=1.44` workaround applies, or PR3 may need to pin an older Docker Engine on the runner. This must be verified when PR3 is implemented, not assumed fixed by this workaround alone.
2. **`InvoicingProvider` mocked via `@MockBean`, not tested against real NubeFacT** — this was already the explicit design decision (Phase A/B split), not a deviation, restated here for clarity.
3. **Discovered and refuted a suspected pre-existing bug**: `SalesListPage.jsx`'s comment claims `GET /api/sales` "currently always returns `voucherStatus: null`". `listReflectsEachSalesOwnVoucherType` proves this is false today — the real list response correctly returns both `voucherStatus` and the newly-added `voucherType`. This comment is stale (likely predates PR4 of `sales-invoicing` wiring invoicing synchronously into `RecordSaleUseCase`) and should be corrected as a small follow-up — not fixed in this PR since it's outside Phase A's scope (backend-only).

### Issues Found
None blocking. See Deviations #1 (CI risk to verify in PR3) and #3 (stale frontend comment, follow-up).

### Remaining Tasks
- [ ] Phase 2 (PR2): Playwright full-stack E2E
- [ ] Phase 3 (PR3): CI workflow + archive gate rule (must also verify Deviation #1's Docker/Testcontainers compatibility on `ubuntu-latest`)

### Workload / PR Boundary
- Mode: chained PR slice (stacked-to-main, user-confirmed)
- Current work unit: PR1 — Phase A
- Boundary: starts from `main` (post-`explicit-voucher-type-selection`/PR#22 fix), ends with a self-contained, independently mergeable Testcontainers integration suite + the `voucherType` field
- Estimated review budget impact: ~230 lines authored (pom.xml +11, SaleResponse.java +2/-0 net, SaleFlowE2ETest.java ~215) — well within the 400-line budget for this slice

### Status
6/6 PR1 tasks complete. Ready for PR2 (Phase B — Playwright).
