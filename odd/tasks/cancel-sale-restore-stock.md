# Feature: cancelling a sale restores product stock

Status: **in progress** on branch `fix/cancel-sale-restore-stock` (2026-10-18).

## Objective

When a completed sale with an `ACCEPTED` voucher is voided (`POST /api/sales/{id}/void` → `CancelSaleUseCase`),
each sold product's stock is restored by the sold quantity atomically alongside the `CANCELLED` sale status,
and the frontend invalidates its cached products query so the UI reflects the restored stock immediately.

## Problem

`CancelSaleUseCase.execute` voids the voucher through `VoidSaleUseCase` and marks the `Sale` as `CANCELLED`,
but never adds the sold units back to each product's `stockQuantity` (`docs/architecture.md` known risk).
In addition, `SalesListPage.jsx` updates the cached `['sales']` list on void success without invalidating
`['products']`.

## Scope

- `ProductRepositoryPort.incrementStock(Long productId, int quantity, Instant now)` implemented in
  `ProductRepositoryAdapter` and `ProductJpaRepository` as a single conditional `UPDATE` (`stock_quantity =
  stock_quantity + :quantity WHERE id = :productId`), regardless of whether the product has been deactivated
  since the sale and without changing `costPrice`.
- `CancelSaleUseCase`: after `VoidSaleUseCase` confirms the void with the invoicing provider, runs the sale
  `CANCELLED` transition and the per-product stock restoration inside `TransactionRunner.inTransaction(...)`.
  Any persistence failure rolls back the transaction and throws `SaleCancellationInconsistentException`.
- `SalesListPage.jsx`: invalidates `['products']` on `voidMutation` success.
- Docs: update `docs/requirements/sales/spec.md` ("Void a Sale") and `docs/architecture.md` (main flow and
  known risks).

## Tasks

- [x] R1 Restore product stock atomically in `CancelSaleUseCase` via `ProductRepositoryPort.incrementStock`,
      invalidate `['products']` on void in `SalesListPage.jsx`, and update specs and architecture doc.
      Test-first.

## Checks

- `mvn clean test`
- `npm test` and `npm run lint` and `npm run build` (in `frontend/`)
- `npm run test:e2e` (in `frontend/`)
- `node scripts/architecture-doc.mjs --check`

## Progress

- 2026-10-18, R1 (route: inline, 14 files): added `ProductRepositoryPort.incrementStock` implemented via a
  single-statement JPQL `UPDATE` in `ProductJpaRepository` and `ProductRepositoryAdapter` (works for both
  active and inactive products without changing `costPrice`); updated `CancelSaleUseCase.execute` to run the
  sale `CANCELLED` update and per-product stock restoration inside `TransactionRunner.inTransaction(...)`
  after `VoidSaleUseCase` confirms the void; invalidated `['products']` in `SalesListPage.jsx` on void
  success; updated `docs/requirements/sales/spec.md` and `docs/architecture.md`.
  - RED: `SalesListPage.test.jsx` failed (`expected "invalidateQueries" to be called with arguments:
    [ { queryKey: [ 'products' ] } ]`); backend tests failed to compile until `incrementStock` and the
    4-arg `CancelSaleUseCase` constructor were implemented.
  - GREEN: `mvn clean test` (7/7 in `CancelSaleUseCaseTest`, 14/14 in `ProductRepositoryAdapterTest`, 154
    total in `infrastructure`), `npm test` (86/86), `npm run lint`, `npm run build`,
    `node scripts/architecture-doc.mjs --check`, and `npm run test:e2e` (3/3).
