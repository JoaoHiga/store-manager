# Design: Sales & Invoicing (Peru)

## Technical Approach

Two new bounded contexts, `sales` and `invoicing`, added as packages inside the existing `application`/`infrastructure` modules — same Hexagonal shape `catalog` already establishes (framework-free domain + ports in `application`, adapters + Spring wiring in `infrastructure`). `Invoicing` talks to NubeFacT over HTTP through a single adapter behind `InvoicingProvider`; nothing OSE-specific leaks into `sales` or into `invoicing`'s own domain/use-case layer.

## Architecture Decisions

| Decision | Alternative(s) | Choice | Rationale |
|---|---|---|---|
| Bounded-context granularity | New Maven modules per context | Packages (`com.storemanager.sales`, `com.storemanager.invoicing`) inside `application`/`infrastructure` | Confirmed in proposal — project size doesn't justify a third/fourth Maven module; the existing two-module split already build-enforces the domain/framework boundary that matters |
| `PENDING`-voucher retry mechanism | Message queue/broker; on-demand-only (no automatic retry) | Spring `@Scheduled` job polling `PENDING` vouchers every 5 minutes, capped at 10 attempts before flagging `NEEDS_ATTENTION`, plus a manual "retry now" action reusing the same use case | A broker is disproportionate for one shop's ~500 docs/month with a single app instance; on-demand-only risks a `PENDING` voucher being forgotten. A capped scheduled retry plus a manual override covers both automatic recovery and an operator's need to force it right after fixing something (e.g. a wrong buyer document number) |
| HTTP client for `NubeFactInvoicingAdapter` | `RestTemplate`; `WebClient` | Spring's `RestClient` | Spring Boot 3.3.4 (confirmed in `pom.xml`) ships `RestClient` — synchronous, matches this non-reactive app, and is the currently-recommended replacement for `RestTemplate` without pulling in Reactor/`WebClient`'s reactive stack for a single blocking call |
| Voucher persistence | A status column merged onto `sales` | Separate `vouchers` table, FK to `sales.id` | A sale and its voucher have different lifecycles (a voucher can be `PENDING` while its sale is already `COMPLETED`; a void produces a second, related voucher-like record for the comunicación de baja) — merging them into one table would force nullable voucher-only columns onto every sale row |
| NubeFacT credential storage | Literal value in `application.properties`; hardcoded in the adapter | `application.properties` reads `${NUBEFACT_API_TOKEN}`/`${NUBEFACT_BASE_URL}` from the environment, never committed | Matches standard Spring Boot externalized-config practice; keeps the credential out of git history entirely |
| Sale line-item pricing | Re-read `Product.salePrice` live at invoice/report time | Snapshot `unitPrice` + `description` onto each `SaleItem` at sale time | A product's price changes over time; a past sale's total must never silently change because someone edited the catalog afterward — matches the exploration's stated risk |

## Data Flow

**Happy path**

    POST /api/sales
        │
        ▼
    RecordSaleUseCase — validates items against ProductRepositoryPort (exists, active, stock), snapshots price
        │
        ▼
    Sale persisted (status: COMPLETED) ──▶ SaleRepositoryAdapter
        │
        ▼
    IssueVoucherForSaleUseCase — builds VoucherRequest, picks Boleta/Factura by buyer doc type
        │
        ▼
    InvoicingProvider.issueBoleta/issueFactura ──▶ NubeFactInvoicingAdapter ──HTTP──▶ NubeFacT ──▶ SUNAT
        │
        ▼ (aceptada_por_sunat = true)
    Voucher persisted (status: ACCEPTED, pdf/xml/cdr links, hash)

**Failure/retry path**

    InvoicingProvider call throws/times out or aceptada_por_sunat = false
        │
        ▼
    Voucher persisted (status: PENDING) — Sale stays COMPLETED, never rolled back
        │
        ▼
    RetryPendingVouchersJob (@Scheduled, every 5 min) ──▶ RetryPendingVoucherUseCase ──▶ InvoicingProvider (same call)
        │                                                         ▲
        │                                              "Retry now" button (frontend) ──┘
        ▼
    ACCEPTED, or attempt count++ (→ NEEDS_ATTENTION at 10)

**Void path**

    POST /api/sales/{id}/void   (only when the sale's voucher is ACCEPTED)
        │
        ▼
    VoidSaleUseCase ──▶ InvoicingProvider.voidVoucher(...) ──▶ NubeFacT (comunicación de baja) ──▶ SUNAT
        │
        ▼ (accepted)
    Voucher → VOIDED, Sale → CANCELLED (soft state — no row deleted)

## File Changes

| File | Action | Description |
|---|---|---|
| `application/.../sales/domain/{Sale,SaleItem}.java` | Create | Immutable domain entities, `createNew`/`reconstitute` factories matching `catalog`'s pattern |
| `application/.../sales/domain/port/SaleRepositoryPort.java` | Create | `save`, `findById`, `findAll` |
| `application/.../sales/usecase/RecordSaleUseCase.java` | Create | Validates items via `ProductRepositoryPort`, snapshots price, computes totals |
| `application/.../invoicing/domain/Voucher.java` | Create | Status `PENDING`/`ACCEPTED`/`REJECTED`/`VOIDED`/`NEEDS_ATTENTION` |
| `application/.../invoicing/domain/port/{InvoicingProvider,VoucherRepositoryPort}.java` | Create | `issueBoleta`, `issueFactura`, `issueCreditNote`, `checkStatus`, `voidVoucher` |
| `application/.../invoicing/usecase/{IssueVoucherForSale,RetryPendingVoucher,VoidSale}UseCase.java` | Create | Orchestration described above |
| `infrastructure/.../sales/persistence/{SaleJpaEntity,SaleItemJpaEntity,SaleRepositoryAdapter}.java` | Create | JPA mapping |
| `infrastructure/.../sales/web/{SaleController,*Request,*Response}.java` | Create | REST input adapter |
| `infrastructure/.../invoicing/persistence/{VoucherJpaEntity,VoucherRepositoryAdapter}.java` | Create | JPA mapping |
| `infrastructure/.../invoicing/nubefact/NubeFactInvoicingAdapter.java` | Create | `RestClient`-based `InvoicingProvider` implementation; owns all NubeFacT-specific field mapping (`tipo_de_comprobante`, `cliente_tipo_de_documento`, `tipo_de_igv`, etc. per `research.md`) |
| `infrastructure/.../invoicing/scheduling/RetryPendingVouchersJob.java` | Create | `@Scheduled(fixedDelay = 300000)` |
| `infrastructure/.../config/UseCaseConfiguration.java` | Modify | Register new use case + `RestClient` beans |
| `infrastructure/src/main/resources/db/migration/V3__create_sales.sql` | Create | `sales`, `sale_items` tables |
| `infrastructure/src/main/resources/db/migration/V4__create_vouchers.sql` | Create | `vouchers` table, FK to `sales` |
| `infrastructure/src/main/resources/application.properties` | Modify | `nubefact.base-url=${NUBEFACT_BASE_URL}`, `nubefact.token=${NUBEFACT_API_TOKEN}` |
| `application/.../invoicing/usecase/FakeInvoicingProvider.java` (test scope) | Create | In-memory fake for unit/use-case tests, mirrors `InMemoryCategoryRepository` |
| `frontend/src/pages/{RecordSale,SalesList}Page.jsx` | Create | Sale-recording form + list with voucher status, "retry now" action |

## Interfaces / Contracts

```
POST   /api/sales                  # record a sale, triggers voucher issuance
GET    /api/sales?status={}        # list
GET    /api/sales/{id}             # detail, includes voucher status + PDF/XML/CDR links when ACCEPTED
POST   /api/sales/{id}/retry-voucher  # manual retry for a PENDING/NEEDS_ATTENTION voucher
POST   /api/sales/{id}/void        # void a sale with an ACCEPTED voucher
```

`InvoicingProvider` port:
```java
public interface InvoicingProvider {
    IssuedVoucher issueBoleta(VoucherRequest request);
    IssuedVoucher issueFactura(VoucherRequest request);
    IssuedVoucher issueCreditNote(CreditNoteRequest request);
    VoucherStatus checkStatus(String voucherReference);
    IssuedVoucher voidVoucher(String voucherReference, String reason);
}
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `Sale`/`SaleItem`/`Voucher` domain invariants | JUnit 5 + AssertJ, no Spring context |
| Unit | `RecordSaleUseCase`, `IssueVoucherForSaleUseCase`, `VoidSaleUseCase`, `RetryPendingVoucherUseCase` | Against `InMemorySaleRepository` + `FakeInvoicingProvider` — success, OSE failure/timeout, rejection, void, retry-exhaustion-to-`NEEDS_ATTENTION` paths all covered without a real NubeFacT call |
| Integration | JPA adapters, REST controllers | `@DataJpaTest` (H2) + `@SpringBootTest`/MockMvc, matching `catalog`'s existing pattern |
| Manual (verify phase only) | Real NubeFacT sandbox | One boleta + one factura issued against NubeFacT's actual sandbox environment, confirming the real response shape matches `research.md` and `NubeFactInvoicingAdapter`'s mapping |
| Frontend | Sale form, voucher status display, retry action | Vitest + React Testing Library |

## Threat Matrix

This change is the app's first outbound external-service boundary (every prior change was internal only), so this is not N/A:

| Concern | Risk | Mitigation |
|---|---|---|
| NubeFacT API token exposure | Credential leak via logs/VCS | Read only from environment (`${NUBEFACT_API_TOKEN}`), never logged (adapter logs request/response with the token field redacted), never committed |
| Buyer PII (name, document number) sent to a third party | Data exposure | Already inherent to using an OSE (SUNAT itself requires this data) — no mitigation beyond HTTPS transport and not persisting it anywhere beyond what `Voucher`/`Sale` already need |
| Outbound call hangs / NubeFacT outage | Sale-recording path blocked | `RestClient` configured with an explicit connect/read timeout; failure path is the designed `PENDING` state, never a blocked request thread |
| Unbounded retry storm against a rejecting/down OSE | Wasted load, could itself look like abuse to NubeFacT | Capped at 10 scheduled attempts before `NEEDS_ATTENTION`, then no further automatic calls until a human retries manually |
| Oversized/malformed field sent to NubeFacT (e.g. `descripcion` > 250 chars) | Rejected voucher, confusing error loop | `NubeFactInvoicingAdapter` validates against the field limits confirmed in `research.md` before sending, failing fast into `REJECTED` with a clear reason instead of relying on NubeFacT's own rejection message |

## Migration / Rollout

`V3__create_sales.sql` creates `sales` + `sale_items`; `V4__create_vouchers.sql` creates `vouchers` with a FK to `sales.id`. No existing data affected — purely additive tables.

## Open Questions

- Exact scheduled-retry interval (proposed: 5 minutes, 10-attempt cap) is a sensible default, not user-confirmed — flag for adjustment if the shop's real usage shows it's too aggressive/lax.
- Real NubeFacT sandbox credentials aren't available yet for the manual `verify`-phase smoke test — needed before that step, not before `apply` starts.
