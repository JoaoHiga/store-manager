# Verification Report — buyer-identification-hardening

**Verdict: PASS**

## Tasks (tasks.md)

All 3 phases complete — 18/18 tracked tasks checked, plus the Delivery section (RDD review, PR).

## Success Criteria (proposal.md)

| Criterion | Status | Evidence |
|---|---|---|
| DNI shorter/longer than 8 digits, or non-digit, rejected | ✅ | `BuyerDocumentValidatorTest` |
| RUC with wrong check digit rejected; SUNAT's real RUC `20131312955` accepted | ✅ | `BuyerDocumentValidatorTest` — verified against research.md's worked example |
| Sale under S/700 recordable with no buyer document | ✅ | `SaleTest`, `RecordSaleUseCaseTest`, `SaleControllerTest` |
| Sale at/above S/700 without buyer document rejected | ✅ | Same, boundary tested (exactly S/700, S/699.99) |
| Voucher issuance resolves FACTURA (RUC) / BOLETA (DNI/CE/Pasaporte) / boleta simple (no document, NubeFacT `-` code) | ✅ | `IssueVoucherForSaleUseCaseTest`, `NubeFactInvoicingAdapterTest#issueBoletaWithNullBuyerDocumentMapsToTheDashCodeAndDoesNotThrow` |
| Frontend offers DNI/RUC/CE/Pasaporte + contextual "Sin documento" | ✅ | `RecordSalePage.jsx`, `RecordSalePage.test.jsx` |
| `mvn test` and `npm test` both pass | ✅ | Re-run directly on `main` post-merge: 217 backend tests (131 application + 86 infrastructure), BUILD SUCCESS; 46 frontend tests, all green |

## Design Fidelity (design.md)

- Validation logic correctly lives in `Sale`'s own constructor via `BuyerDocumentValidator` (not `RecordSaleUseCase`), matching the design's correction of the original proposal — confirmed `Sale.java:38` delegates to `BuyerDocumentValidator.validate(...)` after computing `total`.
- `BOLETA_SIMPLE_THRESHOLD = 700.00` constant confirmed in `BuyerDocumentValidator.java:25`.
- `Voucher` constructor relaxed to accept null buyer fields, matching `Sale`.
- `NubeFactInvoicingAdapter.mapDocumentType`: design.md was corrected mid-review (see RDD note below) — the method's null-safety predates this change; no functional code change was needed or made, only a doc comment.
- `IssueVoucherForSaleUseCase.determineType`'s null-safe `equalsIgnoreCase` logic confirmed unchanged, as designed.

## RDD (receipt-driven development)

One BLOCKER finding (`R3-nubefact-adapter-gap`) was raised and disputed: it claimed `NubeFactInvoicingAdapter.mapDocumentType` needed a null-check that was, in fact, already present (added in `sales-invoicing` PR3, commit `4d5a0d8` — confirmed via `git blame`). Corrected honestly with a doc comment (not a fabricated code fix) and `design.md`'s inaccurate claim fixed. A fresh full re-review (successor lineage `review-buyer-doc-hardening-corr1`) approved with only non-blocking WARNING/SUGGESTION follow-ups, none of which block this change:

- Untested RUC check-digit special-case branches (raw result 10→0, 11→1) — no supplied test value hits either branch (both real-RUC test values resolve to `94 % 11 = 6`).
- `Voucher`'s removed non-blank buyer-field invariant is now unguarded inside `Voucher` itself (relies on `Sale`/`BuyerDocumentValidator` upstream); the blank-but-non-null case is no longer directly tested at the `Voucher` layer.
- Minor whitespace-handling inconsistency: DNI/RUC regex validation doesn't trim, while CE/Pasaporte's light validation does.

These are legitimate low-risk follow-ups, not defects blocking this change — recommend a small future task, not required before archive.

## Known Gap: Merged Specs Are Stale

`openspec/specs/sales/spec.md` and `openspec/specs/invoicing/spec.md` still reflect `sales-invoicing`'s original requirements (buyer reference unconditionally required; only RUC→factura/other→boleta). This change never went through `sdd-spec` (no delta-spec file was authored — `state.yaml` correctly records `specs: false`, following `product-catalog-hardening`'s precedent of skipping formal delta specs for a hardening change). **Archive must hand-update these two merged specs** to reflect: the conditional/format-validated buyer reference in `sales/spec.md`, and the boleta-simple case in `invoicing/spec.md`'s "Voucher Type Determination" requirement.

## Manual E2E

Task 5.5 from `sales-invoicing` (manual click-through) remains pending from the prior change and is still relevant here — the new document-type options and "Sin documento" flow have not yet been clicked through by a human. Not blocking archive (same precedent as `sales-invoicing`'s own verify).
