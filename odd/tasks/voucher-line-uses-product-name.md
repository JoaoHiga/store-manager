# Feature: voucher line uses the product name

Status: **done** (2026-10-18). Delivered in PR #58 (merge `294d19d`).

## Objective

Every sale line, and so every NubeFacT voucher line (`descripcion`), carries the product **name**, which is
required and at most 160 characters. Decided by the user on 2026-10-18.

## Problem

`RecordSaleUseCase.java:131` snapshots `product.getDescription()` into `SaleItem.description`, which
`VoucherRequestMapper` sends as the NubeFacT item `descripcion`. Product `description` is optional (at most
1000 characters), so:

- a product without a description produces a blank line, and NubeFacT rejects the voucher with code 21
  (`Item - descripcion no puede estar en blanco`, observed against the sandbox on 2026-10-18);
- a description longer than 250 characters makes the adapter throw `InvoiceFieldTooLongException`
  (NubeFacT limit), so the voucher is never issued.

## Scope

- `RecordSaleUseCase` snapshots `product.getName()` instead. `sale_items.description` is `VARCHAR(1000)`,
  so no migration is needed. The sales list (`SaleItemResponse`) shows the same snapshot.
- The NubeFacT adapter rejects a blank line description before calling NubeFacT, alongside its existing
  length check, so a blank value fails with a clear local error instead of a provider 400.
- Docs: `docs/requirements/sales/spec.md` (and invoicing if it covers the line text); remove the matching
  known risk from `docs/architecture.md`.
- Out of scope: sales already recorded with a blank snapshot keep it; their pending vouchers would still be
  rejected on retry. Recorded as a follow-up, not fixed here.

## Tasks

- [x] V1 Sale line snapshots the product name; adapter rejects a blank line description; specs and
      known risks updated. Test-first.

## Checks

- `mvn clean test`; `npm run test:e2e` (in `frontend/`); `node scripts/architecture-doc.mjs --check`.
- Forecast: ~100 authored lines, one PR.

## Progress

- 2026-10-18, V1 (route: inline, 9 files): `RecordSaleUseCase.toSaleItem` snapshots `product.getName()`
  onto `SaleItem.description`; `NubeFactInvoicingAdapter.validateLineItems` throws
  `InvoiceFieldBlankException` when a line's `description` is `null` or blank; updated `SaleItem` and
  `RecordSaleUseCase` javadocs, `docs/requirements/sales/spec.md`, `docs/requirements/invoicing/spec.md`,
  and `docs/architecture.md` (removed the blank-`descripcion` known risk and regenerated domain counts).
  - RED: `RecordSaleUseCaseTest` failed (`expected: "iPhone 12" but was: "Usado, buen estado"`,
    `expected: "Funda" but was: null`); `NubeFactInvoicingAdapterTest` failed to compile
    (`InvoiceFieldBlankException` missing).
  - GREEN: `mvn clean test` (22 tests in `RecordSaleUseCaseTest`, 16 in `NubeFactInvoicingAdapterTest`,
    153 total in `infrastructure`), `node scripts/architecture-doc.mjs --check`, and `npm run test:e2e`
    (3/3 passed).
- 2026-10-18: **CLOSED.** Delivered in PR #58 (merge `294d19d`); CI 5/5 green on the PR.
