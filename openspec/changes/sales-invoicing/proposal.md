# Proposal: Sales & Invoicing (Peru)

## Intent

Add the ability to record a sale against the existing product catalog and issue a SUNAT-compliant electronic voucher (boleta or factura) for it, through an OSE (NubeFacT as the reference/first adapter — the port stays provider-agnostic). This turns the app from a catalog/inventory tool into one that can actually close a sale end to end for the shop.

## Scope

### In Scope
- Record a sale: line items referencing existing products, quantity, unit price snapshotted at sale time, computed totals, buyer reference (document type + number).
- Determine boleta vs. factura by buyer document type (RUC → factura, everything else → boleta).
- Issue the voucher via an `InvoicingProvider` port, with NubeFacT as the concrete adapter (schema confirmed in `research.md`).
- Real-time, per-sale voucher emission (no resumen diario/batching — confirmed out of scope for now).
- If the OSE call fails or times out, the sale still completes; the voucher is persisted `PENDING` and retried separately — the sale is never blocked by an external service.
- Basic void flow: a completed sale can be cancelled, which issues SUNAT's comunicación de baja for an already-accepted voucher and marks both the voucher and the sale accordingly (soft state, not deletion — matching `catalog`'s existing soft-delete convention).
- Series/correlativo numbering owned by our own `Invoicing` domain (NubeFacT accepts whatever we send; it does not assign numbers).

### Out of Scope
- Resumen diario / boleta batching (explicitly deferred).
- Building a direct SUNAT SEE integration (already decided: OSE only).
- Supporting more than one OSE/PSE provider at once, or a provider-selection UI.
- Payment processing / payment gateway integration — a sale records a payment method reference only, nothing more.
- Sales reporting/analytics beyond listing.
- The exact retry mechanism for `PENDING` vouchers (scheduled job vs. on-demand) — a `sdd-design` decision, not fixed here.

## Capabilities

### New Capabilities
- `sales`: record and list sales transactions against the product catalog.
- `invoicing`: issue and void SUNAT-compliant electronic vouchers (boleta/factura) for a sale, through an OSE.

### Modified Capabilities
None — `product-catalog` is only read from (`Sale` references `Product` by id), not changed.

## Approach

1. New bounded context `com.storemanager.sales` (packages mirroring `catalog`'s layout in `application`/`infrastructure`): `Sale` domain entity (line items with product reference, quantity, price snapshot, subtotal; buyer reference; computed total), `SaleRepositoryPort`/`SaleRepositoryAdapter`, `RecordSaleUseCase` (validates line items against `ProductRepositoryPort` — product exists and is active, stock available — then snapshots price and computes totals; does not itself call invoicing).
2. New bounded context `com.storemanager.invoicing`: `Voucher` domain entity (type, series/correlativo, buyer doc type/number, status `PENDING`/`ACCEPTED`/`REJECTED`/`VOIDED`, PDF/XML/CDR links, hash), `InvoicingProvider` port (`issueBoleta`, `issueFactura`, `issueCreditNote`, `checkStatus`, `voidVoucher`), `NubeFactInvoicingAdapter` (infrastructure, maps to/from NubeFacT's confirmed JSON schema), `VoucherRepositoryPort`/`VoucherRepositoryAdapter`, a `VoucherSequence` per series+document-type (same concern as `catalog`'s existing product-SKU sequence).
3. `IssueVoucherForSaleUseCase`: triggered after a sale is recorded. Builds a `VoucherRequest` from the `Sale`, determines boleta vs. factura by buyer document type, calls `InvoicingProvider`. On success: persists `ACCEPTED` with PDF/XML/CDR links. On failure/timeout: persists `PENDING` — the sale itself is not rolled back or blocked.
4. `VoidSaleUseCase`: for a sale with an `ACCEPTED` voucher, calls `InvoicingProvider.voidVoucher(...)` (SUNAT comunicación de baja); once accepted, marks the voucher `VOIDED` and the sale `CANCELLED`.
5. Test doubles mirror the existing pattern (`InMemoryCategoryRepository`-style fakes) — `InvoicingProvider` gets a fake/in-memory implementation for unit/use-case tests, so no real NubeFacT credentials are needed to run `mvn test`. A real-NubeFacT-sandbox smoke test is reserved for the `verify` phase, not unit tests.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `application/.../sales/**` | New | `Sale` domain, `SaleRepositoryPort`, `RecordSaleUseCase` |
| `application/.../invoicing/**` | New | `Voucher` domain, `InvoicingProvider` port, `IssueVoucherForSaleUseCase`, `VoidSaleUseCase` |
| `infrastructure/.../sales/**` | New | `SaleRepositoryAdapter` (JPA), sales REST controller |
| `infrastructure/.../invoicing/**` | New | `NubeFactInvoicingAdapter`, `VoucherRepositoryAdapter` (JPA) |
| `infrastructure/.../config/UseCaseConfiguration.java` | Modified | Register new use case beans |
| `infrastructure/src/main/resources/db/migration/V3__*.sql`, `V4__*.sql` | New | `sales`, `sale_items`, `vouchers` tables |
| `infrastructure/src/main/resources/application.properties` | Modified | NubeFacT API base URL + token (externalized, not committed as a literal secret) |
| `frontend/src/pages/**` | New | Sale-recording UI, sale list/detail with voucher status |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| No real NubeFacT sandbox credentials during `apply`/most of `verify` | Medium | Hexagonal boundary already isolates this — unit/use-case tests run against a fake `InvoicingProvider`; one manual smoke test against NubeFacT's real sandbox is reserved for `verify`, not the whole test suite |
| Feature size (2 new bounded contexts + external integration) likely exceeds the 400-line single-PR budget | Medium | `sdd-tasks`' Review Workload Forecast will flag this; delivery strategy is already set to `ask-on-risk`, so you'll be asked to split before `apply` if needed |
| SUNAT rule drift (e.g. the confirmed Jan-2027 validation postponement) | Low | By design, OSE/SUNAT-specific knowledge (tax codes, unit-of-measure codes, etc.) lives only in `NubeFactInvoicingAdapter`, never in `Sales`/`Invoicing` domain logic |
| `PENDING` vouchers left unretried indefinitely | Medium | Retry mechanism is a required part of this change (exact shape decided in `sdd-design`), not an afterthought |

## Rollback Plan

Both bounded contexts are additive — no existing `product-catalog` code is modified beyond adding new beans to `UseCaseConfiguration`. Reverting is dropping the new packages, migrations, and config beans; `product-catalog` is unaffected either way.

## Dependencies

`Sale` reads `Product` via the existing `ProductRepositoryPort` (id, active status, current stock) — no changes needed to `product-catalog` itself.

## Success Criteria

- [ ] A sale for a buyer with a DNI produces an `ACCEPTED` boleta with PDF/XML/CDR links persisted.
- [ ] A sale for a buyer with a RUC produces an `ACCEPTED` factura.
- [ ] If the (fake, in tests) `InvoicingProvider` fails or times out, the sale still completes and the voucher persists as `PENDING`.
- [ ] A completed sale with an `ACCEPTED` voucher can be voided: the voucher becomes `VOIDED`, the sale becomes `CANCELLED`, no rows are deleted.
- [ ] `mvn test` and `npm test` both pass; one manual smoke test against NubeFacT's real sandbox is documented at `verify`.
