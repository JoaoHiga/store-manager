# Tasks: Buyer Identification Hardening

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~500-700 (new validator + tests, `Sale`/`Voucher` changes, migration, adapter fix, DTO, frontend) |
| 400-line budget risk | Medium-High |
| Suggested split | Single PR if it lands under budget; otherwise split backend (validator/domain/migration/adapter) from frontend |
| Delivery strategy | `ask-on-risk` (session preflight, still cached) |

Decided: single PR with `size:exception` (user's explicit choice; actual diff ~870 lines).

## Phase 1: Validator + Domain (`Sale`, `Voucher`)
- [x] 1.1 RED: `BuyerDocumentValidatorTest` — real RUC `20131312955` (research.md) accepted; a RUC with a flipped check digit rejected; RUC with an invalid prefix rejected; DNI of 7/9 digits or non-numeric rejected; DNI of exactly 8 digits accepted; CE/Pasaporte blank rejected, reasonable value accepted; no-document with `total < 700` accepted, `total >= 700` rejected → `application/.../sales/domain/BuyerDocumentValidator.java`
- [x] 1.2 GREEN: `BuyerDocumentValidator` — DNI/RUC/CE/Pasaporte rules, RUC modulo-11 check digit per research.md's algorithm
- [x] 1.3 RED: `SaleTest` — new scenarios: no buyer doc under S/700 allowed; no buyer doc at/above S/700 rejected; malformed DNI/RUC rejected via `Sale.createNew`
- [x] 1.4 GREEN: `Sale.java` — compute `total` before the buyer-reference check (reorder), delegate to `BuyerDocumentValidator` instead of the current unconditional non-blank check
- [x] 1.5 RED: `VoucherTest` — `createNew`/`reconstitute` accept `null` buyer fields without throwing
- [x] 1.6 GREEN: `Voucher.java` — drop the two non-blank checks on buyer fields
- [x] 1.7 `infrastructure/.../db/migration/V6__make_buyer_document_nullable.sql`: relax `NOT NULL` on `sales.buyer_document_type`/`buyer_document_number` and `vouchers.buyer_document_type`/`buyer_document_number`; update `SaleJpaEntity`/`VoucherJpaEntity` column annotations to `nullable = true`
- [x] 1.8 `SaleRepositoryAdapterTest`/`VoucherRepositoryAdapterTest` (`@DataJpaTest`): a sale/voucher with `null` buyer fields round-trips through the real H2 database after `V6`

## Phase 2: Wiring, Adapter, API
- [x] 2.1 RED: `NubeFactInvoicingAdapterTest` — issuing a boleta with `null` buyer document type maps to NubeFacT's `-` code and doesn't throw
- [x] 2.2 `NubeFactInvoicingAdapter.mapDocumentType` — verified already null-safe (predates this change, `sales-invoicing` PR3 commit `4d5a0d8`); no production fix needed, added a doc comment instead (see design.md correction, RDD finding `R3-nubefact-adapter-gap` disputed and resolved)
- [x] 2.3 `RecordSaleRequest.java`: drop `@NotBlank` on `buyerDocumentType`/`buyerDocumentNumber` (now optional; real validation is domain-side)
- [x] 2.4 `SaleControllerTest`: `POST /api/sales` with no buyer fields succeeds for a low-total sale, returns 400 for a high-total one; a malformed RUC/DNI returns 400
- [x] 2.5 `IssueVoucherForSaleUseCaseTest`: regression test — a sale with no buyer document still resolves `VoucherType.BOLETA` (proves the existing null-safe `equalsIgnoreCase` logic keeps working, no change needed there)

## Phase 3: Frontend
- [x] 3.1 `RecordSalePage.jsx`: `BUYER_DOCUMENT_TYPES` → `['DNI', 'RUC', 'CE', 'PASAPORTE']`; add a "Sin documento" choice, contextually suggested/enabled when the running total is under S/700 (soft-guided client-side, backend is the real enforcer); inline format hints for DNI (8 dígitos) and RUC (11 dígitos)
- [x] 3.2 `RecordSalePage.test.jsx`: covers the new document type options and the "Sin documento" path
- [x] 3.3 Full regression: `mvn test` (217 tests, BUILD SUCCESS) and `npm test` (46 tests) both green

## Delivery
- [x] RDD review: `R3-nubefact-adapter-gap` (BLOCKER) disputed as a false positive (verified via `git blame`), corrected honestly, re-reviewed and approved with only non-blocking WARNING/SUGGESTION follow-ups.
- [x] PR opened: https://github.com/JoaoHiga/store-manager/pull/19, base `docs/archive-sales-invoicing` (#18, stacked, not yet merged) — NOT merged.
