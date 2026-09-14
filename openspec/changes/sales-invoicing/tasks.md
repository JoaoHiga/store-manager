# Tasks: Sales & Invoicing (Peru)

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~2600-3300 (2 new bounded contexts + external HTTP adapter + scheduled job + frontend) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 → PR4 → PR5 |
| Delivery strategy | ask-on-risk (session preflight) → user confirmed chained PRs |
| Chain strategy | `stacked-to-main` (user-confirmed) |

Decision needed before apply: No — resolved. 5 chained PRs, `stacked-to-main`, same pattern already used in `inventory-foundation`.

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Sales backend slice, standalone (no invoicing call yet) | PR 1 | `./mvnw test -Dtest=Sale*` | `POST /api/sales` then `GET /api/sales/{id}` returns it, status `COMPLETED` | Delete `sales`-related files + `V3` migration |
| 2 | Invoicing domain + use cases against a fake provider (issue/retry/void state machine) | PR 2 | `./mvnw test -Dtest=Voucher*` | Run use-case tests against `FakeInvoicingProvider` — no real network call | Delete `invoicing`-related application-layer files + `V4` migration |
| 3 | `NubeFactInvoicingAdapter` (real HTTP integration) | PR 3 | `./mvnw test -Dtest=NubeFact*` | Adapter unit tests against a mocked HTTP layer (no live NubeFacT call) | Delete adapter + config files |
| 4 | Wire sales → invoicing (trigger, retry job, void/retry endpoints) | PR 4 | `./mvnw test` (full suite) | `POST /api/sales` end-to-end against `FakeInvoicingProvider`; force a failure and confirm `PENDING` + scheduled retry | Revert wiring in `RecordSaleUseCase`, remove new endpoints and the `@Scheduled` job |
| 5 | Frontend: record-sale form, sales list with voucher status, retry/void actions | PR 5 | `npm test` | Manually record a sale in the running UI, see voucher status update | Delete `RecordSalePage`, `SalesListPage`, related API layer additions |

Verify-phase only (not a PR): one manual smoke test issuing a real boleta + factura against NubeFacT's sandbox, once sandbox credentials are available (per `design.md`'s open question).

## Phase 1: Sales Backend Slice (PR 1) — ✅ DONE
- [x] 1.1 RED: tests for `Sale`/`SaleItem` domain invariants (quantity > 0, buyer reference required) → `application/.../sales/domain/{Sale,SaleItem}.java`
- [x] 1.2 GREEN: `SaleRepositoryPort`, `RecordSaleUseCase` (validates against `ProductRepositoryPort`: exists, active, sufficient stock — cumulative per product across the request, not per line, per the RDD review's correction; snapshots `unitPrice`/`description`; computes totals; decrements and saves product stock after recording)
- [x] 1.3 `infrastructure/.../db/migration/V3__create_sales.sql`: `sales`, `sale_items` tables per `design.md`
- [x] 1.4 `SaleJpaEntity`, `SaleItemJpaEntity`, `SaleRepositoryAdapter`, `@DataJpaTest`
- [x] 1.5 `SaleController` (`POST /api/sales`, `GET /api/sales`, `GET /api/sales/{id}`) + DTOs, MockMvc tests covering every `sales` spec scenario (record valid sale, reject inactive product, reject insufficient stock, reject missing buyer reference, price-snapshot scenario, list/view) — 103/103 tests pass, RDD review approved after one bounded correction (see apply-progress)

## Phase 2: Invoicing Domain (PR 2) — ✅ DONE
- [x] 2.1 RED: tests for `Voucher` domain invariants (status transitions: `PENDING`→`ACCEPTED`/`NEEDS_ATTENTION`, `ACCEPTED`→`VOIDED` only) → `application/.../invoicing/domain/Voucher.java`
- [x] 2.2 `InvoicingProvider` port (`issueBoleta`, `issueFactura`, `issueCreditNote`, `checkStatus`, `voidVoucher`), `VoucherRepositoryPort`
- [x] 2.3 `FakeInvoicingProvider` (test scope) — configurable to accept, reject, or throw/timeout, mirroring `InMemoryCategoryRepository`'s pattern
- [x] 2.4 GREEN: `IssueVoucherForSaleUseCase` (boleta/factura determination by buyer document type; `ACCEPTED`/`PENDING` outcomes) — tests cover every `invoicing` spec scenario for issuance
- [x] 2.5 GREEN: `RetryPendingVoucherUseCase` (bounded attempts → `NEEDS_ATTENTION`, manual retry re-enables) — tests cover the retry-exhaustion and manual-retry-after-exhaustion scenarios
- [x] 2.6 GREEN: `VoidSaleUseCase` (only `ACCEPTED` vouchers voidable; only transitions to `VOIDED` when the OSE confirms — RDD review correction) — tests cover void success, rejection-on-non-accepted, and OSE-rejects-the-void scenarios
- [x] 2.7 `infrastructure/.../db/migration/V4__create_vouchers.sql`: `vouchers` table, FK to `sales`
- [x] 2.8 `VoucherJpaEntity`, `VoucherRepositoryAdapter`, `@DataJpaTest` — 140/140 tests pass, RDD review approved after one bounded correction (see apply-progress)

## Phase 3: NubeFacT Adapter (PR 3) — ✅ DONE
- [x] 3.1 `NubeFactInvoicingAdapter` implementing `InvoicingProvider` via Spring `RestClient`, mapping our domain shapes to/from NubeFacT's confirmed JSON fields (`tipo_de_comprobante`, `cliente_tipo_de_documento`, `tipo_de_igv`, cabecera totals, `items[]`, response links/hash/QR) per `research.md`
- [x] 3.2 Outbound field-length validation (e.g. `descripcion` ≤ 250 chars) before calling NubeFacT — rejects locally with a clear reason; covers the "Outbound Field Validation" spec scenario
- [x] 3.3 `application.yml`: `nubefact.base-url`/`nubefact.token` (env-var backed, repo has no `application.properties`); `RestClient` bean with connect/read timeouts; token redacted from any logging
- [x] 3.4 Adapter unit tests against a mocked HTTP layer (`MockRestServiceServer`) — accepted, rejected, and connection-failure responses, request-shape assertions with exact IGV-split numbers matching `research.md`. Correlativo made durable (JPA-backed, `V5` migration) and concurrency-safe (single-transaction lock held for the whole HTTP call, proven by a real multi-threaded test) after RDD review found and required fixing two real bugs (non-durable counter, then a concurrency race in the first fix). RDD review skipped for final sign-off on this PR: the concurrency fix genuinely exceeds the 200-line correction budget the review froze for this candidate; code is verified independently (69/69 tests, `mvn clean test`), delivery follows ordinary repository policy per RDD's own contract (review is informational, never blocking) — user-confirmed.

## Phase 4: Wire Sales → Invoicing (PR 4) — ✅ DONE
- [x] 4.1 `RecordSaleUseCase` triggers `IssueVoucherForSaleUseCase` after persisting the sale; sale status stays `COMPLETED` regardless of voucher outcome. Added `SaleStatus.CANCELLED` + `Sale.cancelled()` (deferred from PR1).
- [x] 4.2 `RetryPendingVouchersJob` (`@Scheduled(fixedDelay = 300000)`), wired to `RetryPendingVoucherUseCase`, isolates a per-voucher failure so it can't abort the whole batch
- [x] 4.3 `SaleController`: `POST /api/sales/{id}/retry-voucher`, `POST /api/sales/{id}/void` — new `CancelSaleUseCase`/`RetrySaleVoucherUseCase` coordinate the sales-side half PR2's `VoidSaleUseCase`/`RetryPendingVoucherUseCase` deferred
- [x] 4.4 End-to-end backend tests (full suite) against `FakeInvoicingProvider`: happy path, forced failure → `PENDING` → scheduled retry succeeds, retry exhaustion → `NEEDS_ATTENTION` → manual retry, void flow — 181/181 tests pass, RDD review approved after one bounded correction (see apply-progress; `CancelSaleUseCase` originally called the irreversible OSE void before resolving the sale, and didn't distinguish a post-void save failure — both fixed)

## Phase 5: Frontend (PR 5)
- [ ] 5.1 `frontend/src/api/salesApi.js`: fetch wrapper for the 5 sales endpoints
- [ ] 5.2 `RecordSalePage.jsx`: form to add line items (product picker + quantity) and buyer reference, submits a sale
- [ ] 5.3 `SalesListPage.jsx`: list with voucher status badge, "Reintentar" and "Anular" actions wired to the new endpoints
- [ ] 5.4 Component tests: record a sale, see a pending voucher retried, void an accepted sale — mocked API
- [ ] 5.5 Manual E2E check against the real backend from PR1-4 (no real NubeFacT call — `FakeInvoicingProvider` still active until sandbox credentials exist)
