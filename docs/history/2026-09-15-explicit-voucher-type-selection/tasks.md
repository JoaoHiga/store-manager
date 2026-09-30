# Tasks: Explicit Voucher Type Selection

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~300-450 (use case changes, request/controller wiring, frontend selector, tests) |
| 400-line budget risk | Low-Medium |
| Suggested split | Single PR — smaller than `buyer-identification-hardening`, one cohesive behavior change across a short, already-wired call chain |
| Delivery strategy | `ask-on-risk` (session preflight, still cached) |

## Phase 1: Reconstitute Fix (`Sale`)

- [x] 1.1 RED: `SaleTest` — `reconstitute` with a malformed `buyerDocumentNumber` (e.g. the legacy value `"00165490fddfgdsgdf"` for `buyerDocumentType: "RUC"`) does not throw; `createNew` with the same malformed value still throws (regression)
- [x] 1.2 GREEN: `Sale.java` — split the private constructor's call sites: `createNew` calls `BuyerDocumentValidator.validate(...)`, `reconstitute` doesn't
- [x] 1.3 `SaleRepositoryAdapterTest` (`@DataJpaTest`): seed a row directly (bypassing `createNew`) with a malformed buyer document, confirm it loads via the repository without throwing

## Phase 2: Explicit Voucher Type (`IssueVoucherForSaleUseCase`, `RecordSaleUseCase`)

- [x] 2.1 RED: `IssueVoucherForSaleUseCaseTest` — FACTURA + RUC succeeds (voucher type `FACTURA`); FACTURA + DNI/CE/Pasaporte/no-document throws `IllegalArgumentException`; BOLETA + RUC succeeds (voucher type `BOLETA`); BOLETA + DNI/no-document succeeds as before
- [x] 2.2 GREEN: `IssueVoucherForSaleUseCase.execute(Sale sale, VoucherType requestedType)` — validates FACTURA requires RUC, drops `determineType`
- [x] 2.3 RED: `RecordSaleUseCaseTest` — `execute` accepts and forwards `voucherType` to `IssueVoucherForSaleUseCase.execute` unchanged (mock-verified)
- [x] 2.4 GREEN: `RecordSaleUseCase.execute(lines, buyerDocumentType, buyerDocumentNumber, voucherType)` threads the new parameter through

## Phase 3: API Wiring

- [x] 3.1 RED: `SaleControllerTest` — `POST /api/sales` with `voucherType: FACTURA` + non-RUC buyer → `400`; `voucherType: BOLETA` + RUC buyer → `201` with a `BOLETA` voucher; missing `voucherType` → `400`
- [x] 3.2 GREEN: `RecordSaleRequest.java` — new required `voucherType` field (`@NotNull`); `SaleController.record` passes it through

## Phase 4: Frontend

- [x] 4.1 `RecordSalePage.jsx`: add a "Tipo de comprobante" selector (Boleta / Factura); selecting Factura forces/locks the document-type selector to RUC and disables "Sin documento"; selecting Boleta re-enables every document type option including RUC
- [x] 4.2 `RecordSalePage.test.jsx`: covers the Factura-forces-RUC interaction and that Boleta leaves every document type option available
- [x] 4.3 Full regression: `mvn test` and `npm test` both green

## Phase 5: Specs

- [x] 5.1 `openspec/specs/invoicing/spec.md`: rewrite "Voucher Type Determination" — explicit choice at record time; FACTURA requires RUC; BOLETA available to any buyer including RUC; update/add scenarios accordingly

## Post-review fix

RDD's reliability lens (`R3-voucher-validation-after-sale-persist`) found the FACTURA-requires-RUC
check ran only inside `IssueVoucherForSaleUseCase.execute`, which is called AFTER
`saleRepository.save(sale)` in `RecordSaleUseCase.execute` — with no `@Transactional` boundary
around the use case, a rejected request still left a phantom `COMPLETED` sale persisted.
- [x] 6.1 `RecordSaleUseCase.execute` now validates FACTURA-requires-RUC first, before touching
  products or persistence — a rejected request never saves a sale or decrements stock.
- [x] 6.2 `RecordSaleUseCaseTest#aFacturaRequestForANonRucBuyerIsRejectedAndNoSaleIsRecorded` now
  actually asserts `saleRepository.findAll()` is empty and stock is untouched after the rejection
  (previously the test's name promised this but its body never checked it).
- [x] 6.3 (`R3-null-voucher-type-unguarded`, same causal class) `RecordSaleUseCase.execute` also
  rejects a `null` `voucherType` before persistence — `RecordSaleRequest`'s `@NotNull` keeps this
  off the REST API, but the use case is called directly too and must not rely on that DTO-layer
  guard alone; new test `aNullVoucherTypeIsRejectedBeforeAnySaleIsRecorded`.

## Delivery

- [x] RDD review: two BLOCKER/severe findings escalated (`unknown_causality`, candidate-introduced)
  across 3 review passes — `R3-voucher-validation-after-sale-persist` and its follow-up
  `R3-null-voucher-type-unguarded`, both fixed (see Post-review fix above). Final pass approved
  with 3 non-blocking follow-ups, none required before archive:
  - `R3-001` (WARNING): the FACTURA-requires-RUC check is now duplicated (hardcoded twice, in
    `RecordSaleUseCase` and `IssueVoucherForSaleUseCase`) instead of one delegating to the other —
    a future edit to only one location could silently reintroduce the phantom-sale bug class.
  - `R3-002` (SUGGESTION): no test proves the response for a `voucherType` value that's present
    but not a valid enum constant (only the missing-field case is tested).
  - `R3-003` (SUGGESTION): `RecordSalePage`'s auto-uncheck of "Sin documento" when switching to
    Factura is only tested from its already-unchecked steady state, not the actual transition.
- [ ] PR to be opened against `main`, single PR, not merged.
