# Verification Report — explicit-voucher-type-selection

**Verdict: PASS**

## Tasks (tasks.md)

All tasks across Phases 1-5, the Post-review fix section, and the Delivery section are complete — 12/12 tracked implementation tasks, plus 2 post-review defect fixes found by RDD, plus the PR delivered.

## Success Criteria (proposal.md)

| Criterion | Status | Evidence |
|---|---|---|
| A RUC buyer can be issued either a boleta or a factura, by explicit choice | ✅ | `IssueVoucherForSaleUseCaseTest`, `RecordSaleUseCaseTest#aFacturaRequestForARucBuyerProducesAFacturaVoucher` |
| A non-RUC buyer can only be issued a boleta; requesting factura is rejected | ✅ | `IssueVoucherForSaleUseCaseTest`, `RecordSaleUseCaseTest#aFacturaRequestForANonRucBuyerIsRejectedAndNoSaleIsRecorded` |
| `GET /api/sales`/`GET /api/sales/{id}` succeed even when a sale row has a buyer document that fails current-format validation | ✅ | `SaleTest` (`reconstitute` accepts a malformed value `createNew` would reject), `SaleRepositoryAdapterTest` (seeded row round-trips through the real repository) |
| `createNew` still rejects a malformed buyer document at creation time | ✅ | `SaleTest` regression case, same test as above |
| Frontend offers the Boleta/Factura choice, constrained to RUC for Factura | ✅ | `RecordSalePage.jsx`, `RecordSalePage.test.jsx` |
| `mvn test` and `npm test` both pass | ✅ | Re-run directly on `main` post-merge: 232 backend tests (142 application + 90 infrastructure), BUILD SUCCESS; 50 frontend tests, all green |

## Design Fidelity (design.md)

- `IssueVoucherForSaleUseCase.execute(Sale sale, VoucherType requestedType)` replaces `determineType`, exactly as designed — confirmed the old heuristic method no longer exists.
- `Sale.reconstitute`/`createNew` split confirmed: only `createNew` calls `BuyerDocumentValidator.validate(...)`.
- `NubeFactInvoicingAdapter` confirmed untouched (`issueBoleta`/`issueFactura` were already separate calls, as design.md predicted).
- `RetryPendingVoucherUseCase` confirmed untouched (retries the already-persisted type, never recomputes).
- **Deviation from design.md, found and fixed during RDD review, not anticipated at design time**: design.md placed the FACTURA-requires-RUC validation solely inside `IssueVoucherForSaleUseCase.execute`. RDD's reliability lens found this ran *after* `saleRepository.save(sale)` with no `@Transactional` boundary, so a rejected request left a phantom `COMPLETED` sale persisted with no voucher (`R3-voucher-validation-after-sale-persist`, and its follow-up `R3-null-voucher-type-unguarded` for a null `voucherType`). Fixed by adding the same two checks to `RecordSaleUseCase.execute`, running before any product lookup or persistence — this is now the actual, correct architecture, and design.md's Data Flow section is understated relative to it (the implementation is more defensive than originally designed, which is the right direction for a found defect).

## RDD (receipt-driven development)

Three review passes on this candidate (`review-2ec6c6001d10b7ef` → escalated on `R3-voucher-validation-after-sale-persist` → `review-voucher-type-corr1` → fixed → escalated again on `R3-null-voucher-type-unguarded` → `review-voucher-type-corr2`/`corr3` → fixed → **approved**). Both escalated findings were genuine, verified, candidate-introduced defects (confirmed independently via direct code reading and the absence of any `@Transactional` boundary in the call chain before fixing), not false positives — unlike a prior disputed finding in `buyer-identification-hardening`. Final approval carries 3 non-blocking follow-ups (tracked in tasks.md's Delivery section): duplicated FACTURA/RUC check logic in two call sites instead of one delegating to the other; no test for a malformed (vs. missing) `voucherType` enum value; the frontend's "Sin documento" auto-uncheck-on-Factura transition is untested from its checked state. None block this change; recommended as small follow-up work.

## Known Gap: Merged Specs

`openspec/specs/sales/spec.md`'s "Record a Sale" requirement doesn't mention `voucherType` at all — it wasn't touched by this change (only `invoicing/spec.md`'s "Voucher Type Determination" was, per design.md's scope) since the `voucherType` field's contract lives entirely on the request/response and voucher-type-determination side, not on `Sale`'s own persisted state or structural invariants. This is consistent with the design (voucherType is deliberately NOT a `Sale`-owned concept) and not an oversight.

## Manual E2E

Task 5.5 from `sales-invoicing` (manual click-through, now covering all three shipped changes: `sales-invoicing`, `buyer-identification-hardening`, `explicit-voucher-type-selection`) remains pending — not blocking archive, same precedent as the prior two changes.
