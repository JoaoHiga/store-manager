# Proposal: Explicit Voucher Type Selection

## Intent

Replace the automatic "RUC → factura, else → boleta" derivation with an explicit choice made when recording a sale, constrained by the one real SUNAT rule: factura requires a RUC buyer; boleta does not (and remains available even to a RUC buyer). Also fix an unrelated but currently-live bug found in the same testing pass: `Sale.reconstitute` re-runs `BuyerDocumentValidator` on every load, so any sale row written before `buyer-identification-hardening` shipped now crashes `GET /api/sales`/`GET /api/sales/{id}`.

## Scope

### In Scope
- `RecordSaleRequest`/`RecordSaleUseCase`/`IssueVoucherForSaleUseCase`: accept an explicit `voucherType` (BOLETA/FACTURA) at sale-recording time instead of deriving it.
- Validation: FACTURA requires `buyerDocumentType == RUC`; reject otherwise. BOLETA is always allowed regardless of document type (including RUC, or no document under the S/700 threshold).
- `Sale.reconstitute`: stop re-validating `BuyerDocumentValidator` on load — validation is a create-time invariant, not a rehydration check.
- Frontend: explicit Boleta/Factura selector in `RecordSalePage.jsx`, with Factura constrained to RUC.
- `invoicing/spec.md`'s "Voucher Type Determination" requirement rewritten to reflect the real rule.

### Out of Scope
- Any change to `NubeFactInvoicingAdapter` — `issueBoleta`/`issueFactura` are already separate provider calls; the fix is entirely about which one the caller invokes, not the adapter itself.
- Retry logic (`RetryPendingVoucherUseCase`) — a voucher's `type` is fixed at creation and never re-derived on retry; unaffected.
- Any new DB migration — `voucher.type` already exists as a stored column; this only changes how its value is decided at creation.
- Backfilling the legacy `sales.id = 1` row's malformed RUC — the reconstitute fix makes it loadable again as-is; no data change needed.

## Capabilities

### Modified Capabilities
- `sales`: `Record a Sale` gains a `voucherType` input and its FACTURA/RUC constraint.
- `invoicing`: `Voucher Type Determination` requirement rewritten — driven by explicit choice, not derived from document type.

## Approach

1. `RecordSaleRequest` gains a required `voucherType` field (`"BOLETA"` or `"FACTURA"`).
2. `RecordSaleUseCase.execute` gains a `voucherType` parameter, threaded through unchanged to `IssueVoucherForSaleUseCase.execute`.
3. `IssueVoucherForSaleUseCase.execute(Sale sale, VoucherType requestedType)`: validates `requestedType == FACTURA` implies `sale.getBuyerDocumentType()` equals `"RUC"` (case-insensitive) — throws `IllegalArgumentException` otherwise. Replaces the old private `determineType` heuristic entirely.
4. `Sale.reconstitute` stops calling `BuyerDocumentValidator.validate(...)` — only `createNew` does. The shared private constructor keeps its structural checks (non-empty items, non-negative fields) but the buyer-document format/threshold check moves to be create-only.
5. Frontend: a "Tipo de comprobante" radio/select (Boleta / Factura) alongside the existing document-type selector; selecting Factura auto-forces/locks `buyerDocumentType` to RUC (can't submit Factura with anything else, including "Sin documento").

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `application/.../sales/domain/Sale.java` | Modified | `reconstitute` no longer calls `BuyerDocumentValidator`; only `createNew` does |
| `application/.../invoicing/usecase/IssueVoucherForSaleUseCase.java` | Modified | Takes an explicit `requestedType`; validates FACTURA requires RUC; drops `determineType` |
| `application/.../sales/usecase/RecordSaleUseCase.java` | Modified | New `voucherType` parameter, threaded to `IssueVoucherForSaleUseCase` |
| `infrastructure/.../sales/infrastructure/web/RecordSaleRequest.java` | Modified | New required `voucherType` field |
| `infrastructure/.../sales/infrastructure/web/SaleController.java` | Modified | Passes `request.voucherType()` through |
| `frontend/src/pages/RecordSalePage.jsx` | Modified | New Boleta/Factura selector, constrained to RUC for Factura |
| `openspec/specs/invoicing/spec.md` | Modified | "Voucher Type Determination" requirement rewritten |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Existing tests assume the old auto-derivation | Medium | Full regression (`mvn test`, `npm test`) at every step; update `IssueVoucherForSaleUseCaseTest` explicitly |
| The reconstitute fix accidentally weakens `createNew`'s validation too (shared constructor) | Low | Explicit test: `createNew` still rejects a malformed document; `reconstitute` accepts the same malformed value without throwing |
| Frontend allows submitting Factura with a non-RUC document (client-side only, backend still enforces) | Low | Backend is the real enforcer either way (matches `buyer-identification-hardening`'s precedent for the S/700 rule) |

## Rollback Plan

No schema change. Reverting the specific commits restores the old auto-derivation and the reconstitute bug (an acceptable rollback since both are already-known states, not novel risk).

## Dependencies

Builds on `sales-invoicing` and `buyer-identification-hardening` (both archived). No other dependencies.

## Success Criteria

- [ ] A RUC buyer can be issued either a boleta or a factura, by explicit choice.
- [ ] A non-RUC buyer (DNI/CE/Pasaporte/no-document) can only be issued a boleta; requesting factura is rejected.
- [ ] `GET /api/sales` and `GET /api/sales/{id}` succeed even when a sale row has a buyer document that fails current-format validation (legacy data).
- [ ] `createNew` still rejects a malformed buyer document at creation time.
- [ ] Frontend offers the Boleta/Factura choice, constrained to RUC for Factura.
- [ ] `mvn test` and `npm test` both pass.
