# Proposal: Buyer Identification Hardening

## Intent

Add real format validation for buyer documents (DNI, RUC with its check digit, lighter validation for Carnet de Extranjería/Pasaporte) and support Peru's "boleta simple" case — a sale under S/ 700 can be recorded with no buyer identification at all. Found missing while manually clicking through the running `sales-invoicing` app: the archived spec never defined format rules or the no-document case, only "RUC → factura, everything else → boleta" with buyer reference unconditionally required.

## Scope

### In Scope
- DNI format validation: exactly 8 numeric digits.
- RUC format validation: 11 digits, prefix in `{10, 15, 17, 20}`, valid modulo-11 check digit.
- Carnet de Extranjería / Pasaporte: light validation (non-blank, bounded length) — no strict format confirmed by research, deliberately not over-validated.
- `Sale`'s buyer reference becomes conditional: optional when the sale's total is below S/ 700, required (and format-validated) at or above it.
- `sales_documents` schema: `buyer_document_type`/`buyer_document_number` columns become nullable (currently `NOT NULL`).
- `IssueVoucherForSaleUseCase`/`NubeFactInvoicingAdapter`: a third outcome for "no document" (boleta simple), mapped to NubeFacT's `-` document-type code.
- Frontend: `BUYER_DOCUMENT_TYPES` expands to DNI/RUC/CE/Pasaporte, plus a "Sin documento" option shown/relevant only when the running total is under S/ 700.

### Out of Scope
- Legal sign-off — a real Peruvian accountant should still validate these specific rules before real invoicing goes live (already told to the user in an earlier session).
- Making the S/ 700 threshold admin-configurable — it's a named constant for now, same "OSE-adapter-owned-knowledge, not scattered" principle as `sales-invoicing`'s design.md.
- Backfilling/fixing existing sales already in the database — this change affects new sales going forward only.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `sales`: "Record a Sale" requirement changes — buyer reference is now conditionally required (by total, not unconditionally), and format-validated when present.
- `invoicing`: "Voucher Type Determination" requirement gains a third case (no document → boleta simple).

## Approach

1. A dedicated `BuyerDocumentValidator` (or equivalent) in `application/.../sales/domain/`: validates DNI (8 digits), RUC (11 digits + prefix + mod-11 check digit), CE/Pasaporte (non-blank, bounded length), and allows `null`/absent only when the caller confirms the sale total is under S/ 700.
2. `RecordSaleUseCase` computes the sale total before constructing `Sale` (it already does, for stock/pricing), then applies the S/ 700 rule when deciding whether a blank buyer reference is acceptable, and runs the validator when a document is present.
3. `Sale`'s constructor keeps enforcing only its own structural invariants (per the existing pattern from `sales-invoicing`) — the total-dependent conditional rule belongs in `RecordSaleUseCase`, not `Sale` itself, since `Sale` doesn't natively know about the S/ 700 business rule's external, revisable nature.
4. New Flyway migration making `sales.buyer_document_type`/`buyer_document_number` nullable; `SaleJpaEntity` annotations updated to match.
5. `IssueVoucherForSaleUseCase.determineType`: `null`/blank buyer document type → boleta simple (still `VoucherType.BOLETA`, just with a `null` buyer document on the `Voucher`/`VoucherRequest`).
6. `NubeFactInvoicingAdapter.mapDocumentType`: explicit `null`-safe branch returning NubeFacT's `-` code for the no-document case (today's default branch already returns `-` for unmapped types, but a `null` input would NPE on `.toUpperCase()` before reaching it — needs an explicit null check first).
7. Frontend: expand the document-type selector, add a "Sin documento" choice that's only sensible under S/ 700 (soft-guided, not hard-blocked client-side — the backend is the real enforcer), and inline format hints for DNI/RUC.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `application/.../sales/domain/BuyerDocumentValidator.java` | New | DNI/RUC/CE/Pasaporte format rules, RUC mod-11 check digit |
| `application/.../sales/domain/Sale.java` | Modified | Accepts a nullable buyer reference structurally |
| `application/.../sales/usecase/RecordSaleUseCase.java` | Modified | S/ 700 threshold rule, calls the validator |
| `application/.../invoicing/usecase/IssueVoucherForSaleUseCase.java` | Modified | `determineType` handles a null/blank buyer document type |
| `infrastructure/.../invoicing/infrastructure/nubefact/NubeFactInvoicingAdapter.java` | Modified | Null-safe `mapDocumentType`, confirm/use the `-` code |
| `infrastructure/.../sales/infrastructure/web/RecordSaleRequest.java` | Modified | Buyer fields no longer unconditionally `@NotBlank` |
| `infrastructure/.../sales/infrastructure/persistence/SaleJpaEntity.java` | Modified | `nullable = true` on both buyer columns |
| `infrastructure/src/main/resources/db/migration/V6__make_buyer_document_nullable.sql` | New | Relax `NOT NULL` on `sales.buyer_document_type`/`buyer_document_number` |
| `frontend/src/pages/RecordSalePage.jsx` | Modified | Document type options, "Sin documento", format hints |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| RUC check-digit algorithm implemented incorrectly (off-by-one on weights, wrong exception handling) | Medium | Test against SUNAT's own real RUC (`20131312955`) from research.md, not just synthetic values; triangulate with at least one more real, publicly-known RUC |
| S/ 700 boundary handled inclusively/exclusively wrong | Low | Explicit boundary tests: exactly S/700 (documented required), S/699.99 (documented optional) |
| CE/Pasaporte light validation lets garbage through | Accepted | Deliberate tradeoff per research — no authoritative strict format found; false rejection of a real document is worse |
| Existing tests assuming buyer reference is always present now need updating | Medium | Full regression run (`mvn test`, `npm test`) as the safety net at every step |

## Rollback Plan

The DB migration only relaxes a constraint (`NOT NULL` → nullable) — reversible without data loss since no existing rows have `NULL` in these columns yet. Code changes are additive/conditional logic; reverting the specific commits restores the unconditional-required behavior.

## Dependencies

Builds on the archived `sales-invoicing` change (modifies its `sales` and `invoicing` capabilities). No other dependencies.

## Success Criteria

- [ ] A DNI shorter/longer than 8 digits, or containing non-digits, is rejected with a clear validation error.
- [ ] A RUC with a wrong check digit is rejected; SUNAT's own real RUC (`20131312955`) is accepted.
- [ ] A sale under S/ 700 can be recorded with no buyer document.
- [ ] A sale at or above S/ 700 without a buyer document is rejected.
- [ ] Voucher issuance correctly resolves FACTURA (RUC), BOLETA (DNI/CE/Pasaporte), or boleta simple (no document, NubeFacT `-` code).
- [ ] Frontend offers DNI/RUC/CE/Pasaporte and a contextual "Sin documento" option.
- [ ] `mvn test` and `npm test` both pass.
