# Design: Explicit Voucher Type Selection

## Technical Approach

The voucher's type (`BOLETA`/`FACTURA`) becomes an explicit input at sale-recording time instead of a value derived from the buyer's document type. `Voucher.type` already exists as a persisted field set once at creation and never re-derived (confirmed: `RetryPendingVoucherUseCase` retries issuance of the already-persisted type, never recomputes it) — so this change only touches how that value is decided, not its storage or its behavior on retry.

The one real constraint (per research.md) is one-directional: FACTURA requires a RUC buyer; BOLETA has no such constraint (a RUC buyer choosing BOLETA is valid). This validation needs both the requested type AND the sale's buyer document type at once, so it belongs in `IssueVoucherForSaleUseCase.execute` — the one place that already has both, right where the old `determineType` heuristic lived. It does not belong in `Sale` (a sale doesn't intrinsically "have" a voucher-type preference; that's an invoicing-layer decision, and `sales` doesn't depend on `invoicing`'s `VoucherType` type) nor in `Voucher`'s constructor (which only knows what it's given, not the business rule connecting it to the buyer document).

**Second, unrelated fix bundled into this change** (found in the same testing pass, not by the user — see exploration.md): `Sale`'s private constructor runs `BuyerDocumentValidator.validate(...)` unconditionally, so both `createNew` (correct — a new sale must satisfy current rules) and `reconstitute` (wrong — a row written years ago must still load even if the rules changed since) share the same check. This is a general class of bug: a domain constructor enforcing a *business* invariant should never gate *rehydration* of already-trusted, already-persisted data, only *creation* of new data. The fix separates the two call sites' behavior rather than the constructor's structure — `reconstitute` skips the business-rule check but keeps the structural ones (non-empty items, non-negative fields) that guard against genuine data corruption.

## Architecture Decisions

| Decision | Alternative(s) | Choice | Rationale |
|---|---|---|---|
| Where the FACTURA-requires-RUC check lives | Inside `Sale`'s constructor (matching `buyer-identification-hardening`'s precedent) | `IssueVoucherForSaleUseCase.execute` | Unlike the S/700 threshold (which only needs data `Sale` already owns), this check needs the *requested* voucher type — a fact that isn't and shouldn't become part of `Sale`'s own persisted state; `sales` staying independent of `invoicing`'s `VoucherType` matters more than co-locating this one check |
| How the buyer signals their choice | Keep the frontend hint-only "Sin documento" pattern, add voucher type as another soft-guided-only client hint with no explicit backend field | A first-class required `voucherType` request field, backend-enforced | The choice is the entire point of the fix — leaving it implicit again would just relocate the bug, not fix it; matches `buyer-identification-hardening`'s established stance that soft client hints are fine but the backend is always the real enforcer |
| `Sale.reconstitute` validation scope | Add a `skipValidation` flag param to the shared constructor | Two call sites (`createNew` calls `BuyerDocumentValidator.validate`, `reconstitute` doesn't), constructor keeps only structural checks | A boolean "skip validation" flag on a domain constructor is exactly the kind of footgun that gets accidentally passed wrong later; two named factory methods with clearly different bodies is more explicit and harder to misuse |
| Backward compatibility for `voucherType` | Make it optional, default to the old derivation when absent | Required, no default | This project's own frontend is the only caller; there's no external client to preserve compatibility for, and a silent default would reintroduce exactly the bug being fixed |

## Data Flow

    POST /api/sales  { items, buyerDocumentType?, buyerDocumentNumber?, voucherType }
        │
        ▼
    RecordSaleUseCase — validates products/stock (unchanged), builds Sale (unchanged:
    Sale still only knows about its own buyer reference, not voucherType)
        │
        ▼
    Sale persisted (unchanged schema — no new column)
        │
        ▼
    IssueVoucherForSaleUseCase.execute(sale, requestedType)
        requestedType == FACTURA?
          ├─ sale.getBuyerDocumentType() equalsIgnoreCase "RUC" → proceed
          └─ otherwise → IllegalArgumentException("Factura requires a RUC buyer document")
        requestedType == BOLETA → always proceeds, regardless of buyer document type
        │
        ▼
    Voucher.createNew(saleId, requestedType, sale.getBuyerDocumentType(), sale.getBuyerDocumentNumber(), now)
        │
        ▼
    InvoicingProvider.issueFactura(...) or .issueBoleta(...) — unchanged, already separate calls

Separately, unconditionally of the above:

    GET /api/sales, GET /api/sales/{id}
        │
        ▼
    SaleRepositoryAdapter → Sale.reconstitute(...) — no longer calls BuyerDocumentValidator;
    a legacy row with a since-invalidated buyer document loads successfully

## File Changes

| File | Action | Description |
|---|---|---|
| `application/.../sales/domain/Sale.java` | Modify | `reconstitute` no longer calls `BuyerDocumentValidator.validate`; only `createNew` does (two distinct private-constructor call sites, not a shared flag) |
| `application/.../invoicing/usecase/IssueVoucherForSaleUseCase.java` | Modify | `execute(Sale sale, VoucherType requestedType)`; validates FACTURA requires RUC; drops the `determineType` heuristic entirely |
| `application/.../sales/usecase/RecordSaleUseCase.java` | Modify | New `voucherType` parameter (as `VoucherType`, imported from `invoicing.domain` — `sales` already depends on `invoicing.usecase.IssueVoucherForSaleUseCase`, so this doesn't introduce a new cross-context dependency direction) threaded to `IssueVoucherForSaleUseCase.execute` |
| `infrastructure/.../sales/infrastructure/web/RecordSaleRequest.java` | Modify | New required `voucherType` field (`"BOLETA"`/`"FACTURA"`, validated via `@NotNull` + enum conversion) |
| `infrastructure/.../sales/infrastructure/web/SaleController.java` | Modify | Passes `request.voucherType()` through to `recordSaleUseCase.execute(...)` |
| `frontend/src/pages/RecordSalePage.jsx` | Modify | New "Tipo de comprobante" selector (Boleta/Factura); selecting Factura forces `buyerDocumentType` to RUC and disables "Sin documento"/other types |
| `openspec/specs/invoicing/spec.md` | Modify | "Voucher Type Determination" requirement rewritten: explicit choice, FACTURA-requires-RUC constraint, BOLETA available to any buyer including RUC |

## Interfaces / Contracts

`POST /api/sales` request body gains a required `voucherType` field (`"BOLETA"` or `"FACTURA"`, case-sensitive enum). Existing behavior changes:
- `voucherType: "FACTURA"` with `buyerDocumentType` other than `"RUC"` (or absent) → `400`, `IllegalArgumentException` message "Factura requires a RUC buyer document".
- `voucherType: "BOLETA"` with any buyer document type (including RUC) or no document (subject to the existing S/700 rule) → succeeds.
- Missing `voucherType` → `400` (Bean Validation `@NotNull`).

No other endpoint changes. `GET /api/sales`/`GET /api/sales/{id}` response shape is unchanged — only their previously-crashing behavior for legacy rows is fixed.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `Sale` | `createNew` still rejects a malformed buyer document (regression); `reconstitute` accepts the same malformed value without throwing (the actual fix, using the legacy row's real value as a concrete case) |
| Unit | `IssueVoucherForSaleUseCase` | FACTURA + RUC → succeeds, produces a `FACTURA` voucher; FACTURA + DNI/CE/Pasaporte/no-document → `IllegalArgumentException`; BOLETA + RUC → succeeds, produces a `BOLETA` voucher (the actual behavior change); BOLETA + DNI/no-document → succeeds as before |
| Unit | `RecordSaleUseCase` | Passes `voucherType` through unchanged (mock-verifies the call) |
| Integration | `SaleController` (MockMvc) | `POST /api/sales` with `voucherType: FACTURA` + non-RUC buyer → `400`; with `voucherType: BOLETA` + RUC buyer → `201`, resulting voucher type is `BOLETA`; missing `voucherType` → `400` |
| Integration | `SaleRepositoryAdapter` (`@DataJpaTest`) | A sale row seeded directly with a malformed `buyer_document_number` (bypassing `createNew`) round-trips through `reconstitute` without throwing |
| Frontend | `RecordSalePage.test.jsx` | Selecting "Factura" forces/locks the document type to RUC; selecting "Boleta" leaves all document type options (including RUC and "Sin documento") available |

## Threat Matrix

N/A — no new routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary; this only changes which of two existing, already-tested provider calls (`issueBoleta`/`issueFactura`) gets invoked, and removes an over-eager validation call on a read path.

## Migration / Rollout

No schema change. No data migration — the legacy `sales.id = 1` row becomes loadable again as-is once `reconstitute` stops re-validating it; its stored `buyer_document_number` remains the same (still not a real RUC), which is fine since format validation was never a requirement at the time it was written.

## Open Questions

- Should an already-`ACCEPTED` factura ever be "downgraded" to a boleta (or vice versa) after the fact? Out of scope — this change only affects the choice made at initial sale-recording time; changing a voucher's type after issuance would be a credit-note/reissuance scenario, not covered here.
