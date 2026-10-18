# Feature: purchases (supplier stock entry)

## Objective

Let the store register merchandise bought from suppliers so stock goes up, cost price stays
accurate (weighted average), and every purchase is traceable to a supplier for accounting.

## Problem

- Stock only goes down (sales). There is no purchase, restock, or adjustment flow
  (`docs/architecture.md` known risk).
- `PUT /api/products/{id}` writes `stockQuantity` as an absolute value, so a sale or purchase
  recorded while the edit form is open is silently undone (`docs/architecture.md` known risk).
- There is no supplier concept; accounting needs to know who each purchase came from.

## Why

Without reliable stock entry the inventory is wrong within days, and alerts, profit, and trend
reports (the next features) all depend on trustworthy stock and cost.

## Scope

- Suppliers: create and list (name, RUC, optional contact data). Active/inactive.
- Purchases: one purchase = supplier + supplier invoice number + date + line items
  (product, quantity, unit cost). Recording it atomically increments stock and recomputes cost
  price as a weighted average.
- Stock is removed from the product update path (stock changes only through sales and purchases).
- Frontend: supplier management, record purchase form, purchase list.

## Out of scope (follow-ups)

- Accounting (purchase/sales ledgers, IGV). Separate feature.
- Voiding a purchase.
- Bug: cancelling a sale does not restore stock (`CancelSaleUseCase.java:30-38`). Separate fix.
- Manual stock adjustments (shrinkage, counts) and a stock-movement ledger.

## Constraints

- Hexagonal: `application` has zero Spring deps; follow the Sale module as the template
  (use case + port + JPA adapter + controller + per-controller exception handler).
- Stock/cost update must be a single atomic conditional UPDATE (same style as
  `decrementStockIfAvailable`); no `@Version` exists in the repo.
- New Flyway migrations start at V9; use `GENERATED ALWAYS AS IDENTITY` (H2-compatible).
  Never edit V1-V8.
- Wire use cases as `@Bean` in `catalog/infrastructure/config/UseCaseConfiguration.java`.
- Run `node scripts/architecture-doc.mjs` after adding domains/ports/controllers/migrations;
  update hand-written sections and remove fixed risks.
- Test-first: RED then GREEN for each behavior.

## Decisions

- Track suppliers (user, 2026-10-08): needed for accounting.
- Cost policy = weighted average (user, 2026-10-08):
  `newCost = (stock * oldCost + qty * unitCost) / (stock + qty)`, rounded HALF_UP to 2 decimals.
  When `oldCost` is null or stock is 0, `newCost = unitCost`.

## Tasks

- [x] T1 Remove stock from the product update path (backend use case, DTO, frontend form, spec,
      tests). Route: delegated (backend + frontend files). Commit `2510209`.
- [x] T2 Suppliers: domain, port, use cases (create, list, deactivate/reactivate), V9 migration,
      JPA adapter, REST controller, frontend page, tests, `docs/requirements/purchasing/spec.md`.
      Route: delegated (writer), plus an inline RUC extraction. Commit `1ee2058` on
      `feat/purchases-suppliers`.
- [x] T3b Remove cost price from the product update path (same shape as T1), so a stale edit
      cannot overwrite the weighted average. Added after T3 (user, 2026-10-08). Route: delegated.
      Commit `8bef16e` on `feat/purchases-record-split` (cherry-pick of `fa84c92`).
- [x] T3 Record purchase backend (originally `02a53aa` on `feat/purchases-record`; split on
      `feat/purchases-record-split` into `9f96c91` domain + use case, `3d7a343` persistence + V10,
      `6e803cf` REST): domain, use case with atomic stock increment + weighted
      average cost, V10 migration, JPA adapter, REST (record, list, detail), tests, spec.
      Route: delegated.
- [x] T4 Record purchase frontend: form page + purchase list, API module, Vitest tests.
      Route: delegated. Commit `16a3da0` on `feat/purchases-frontend`.
- [x] T5 Playwright E2E for the purchase flow, architecture doc regeneration + risks update.
      Route: delegated. Commit `037992c` on `feat/purchases-frontend`.
- [ ] T6 Backend review follow-ups (user, 2026-10-17): (a) narrow the supplier integrity mapping
      to `uq_suppliers_ruc`; (b) PUT product response shows stale stock; (c) deterministic date
      in `RecordPurchaseUseCaseTest`; (d) prove HALF_UP weighted-average rounding on PostgreSQL;
      (e) prove the duplicate-invoice mapping on PostgreSQL; (f) robust timing in
      `PurchaseConcurrencyPostgresTest`; (g) cover decimal digit limits; (h) trim the invoice
      number before the size check; (i) reject a null element in `items` with 400; (j) assert the
      response cost price. Route: delegated (one writer, several backend files). Commit `e21c04d`
      on `feat/purchases-frontend`. (d) and (e) proved with no fix needed; (g) found a real bug
      (quantity 1.5 truncated to 1), fixed with `WholeNumberDeserializer`. Verified: `mvn clean test`
      197 + 150 pass (parent re-run). RDD: range `16a3da0..e21c04d` medium, 523 lines, granted,
      lineage `review-48b2ff95e13cfd78`, approved and acknowledged. Reviewed boundary `e21c04d`.
      Advisory: (n) `purchase-flow.spec.js:105-106` stock assertion `toContainText('15')` is
      vacuous; (o) `WholeNumberDeserializer.java:21-25` rejects an integral float like `2.0`.
      Found outside scope: `SaleItemRequest` (`@Positive int quantity`) also truncates 1.5 to 1.
- [x] T7 Frontend review follow-ups: (k) show supplier/product load errors on the purchases page;
      (l) assert the list total; (m) test removing a middle line; integer-only quantity in the
      purchase form; (n) exact stock assertion in the E2E spec. Route: delegated. Commit `e2e4463`.
      (k) RED (2 failed) then GREEN; (q) needed no code change (form already enforced `/^\d+$/`),
      tests added and shown able to fail; (n) shown able to fail (`'1'` vs `'15'`). The "purchase
      form hides load errors" risk was removed from `docs/architecture.md`.
- [x] T8 Sales quantity truncation (user, 2026-10-17): global
      `spring.jackson.deserialization.accept-float-as-int: false`, test
      `SaleControllerTest.rejectsAFractionalQuantityWith400InsteadOfTruncatingIt` RED (NPE: the 1.5
      reached the use case) then GREEN, sales spec rule updated. Route: inline (one config line + one
      test). Commit `a400d18`.
      Final checks after T8: `mvn clean test` 197 + 151 pass; `architecture-doc.mjs --check` up to
      date; `npm run test:e2e` 3 passed; `npm test` 86 pass; `npm run build` ok.
      RDD assess (base `e21c04d`): medium, 95 lines, `under_budget`; `e2e4463..a400d18` not
      reviewed natively. PR4 = `16a3da0..a400d18` (`feat/purchases-frontend`).
- 2026-10-17: pushed (user). Stacked PRs: #31 `main` <- `feat/purchases`, #32 <- `feat/purchases-suppliers`,
  #33 <- `feat/purchases-record-split`, #34 <- `feat/purchases-frontend`; separate #35 `main` <-
  `chore/local-dev-setup`. CI running. Merging is the user's decision; after each merge the next PR
  retargets to `main`. The feature closes when #34 merges.
- 2026-10-18 reconciliation: T6 is unchecked above but was worked in `e21c04d` ("harden purchase and supplier
  validation from review follow-ups", merged to `main`; it introduces the `uq_suppliers_ruc` mapping of item
  (a)). The doc was not updated at the time. Items (a)-(m) were not re-verified one by one, so the checkbox
  stays unchecked.
- 2026-10-17: **CLOSED.** User chose to merge in order with merge commits: #31 `158193b`, #32 `61f9715`,
  #33 `b085741`, #34 `ffa6486`, #35 `aaf1a14`. Each child was retargeted to `main` before merging.
  #32 and #33 merged with SonarCloud failing on duplication (5.7% / 3.2%, gate 3%); follow-up is C4 in
  `odd/tasks/ci-improvements.md`. CI on `main` at `aaf1a14`: all four jobs success (closing rule met).
  Open follow-ups: (o) `WholeNumberDeserializer` rejects `2.0`; product decisions on English backend
  error messages and showing cost price in the UI; `PriceTag` USD/es-AR default.

## Acceptance criteria

- Recording a purchase increments each product's stock and sets weighted-average cost.
- Concurrent sale + purchase on the same product never loses an update.
- Editing a product cannot change its stock.
- A purchase requires an active supplier and at least one line with quantity > 0 and
  unit cost >= 0; inactive products are rejected.
- Purchases are listed with supplier, invoice number, date, and total.

## Checks

- `mvn clean test` (repo root)
- `npm test` and `npm run build` (frontend/)
- `npm run test:e2e` (frontend/, Docker) — required before closing.
- `node scripts/architecture-doc.mjs` and no stale generated sections.

## Delivery

- Forecast: ~1500 authored changed lines (> 400). Strategy: `ask-on-risk`; chain strategy
  `stacked-to-main` (user, 2026-10-08). Planned slices: PR1 = T1, PR2 = T2, PR3 = T3,
  PR4 = T4 + T5.
- Branch: `feat/purchases`.

## Progress

- 2026-10-08: exploration done, decisions recorded, branch created. Next: T1.
- 2026-10-08: T1 done, commit `2510209`. Verified: `mvn clean test` 145+105 pass;
  `npm test` 51 pass; `npm run build` ok; `architecture-doc.mjs --check` up to date.
  Stock column is `updatable=false`, so T3 needs an atomic JPQL increment.
  `rs-matrix/` (an unrelated nested clone) was moved to `~/projects/rs-matrix` (user).
  RDD assess (base `9d0714c`, committed-only, untracked excluded): medium, 227 lines,
  `review_due=false` (`under_budget`). The reviewed boundary stays at `9d0714c`. Next: T2.
- 2026-10-08: T2 done on branch `feat/purchases-suppliers` (stacked on `feat/purchases`), commit
  `1ee2058`. Deactivate/reactivate added because T3 must reject inactive suppliers. UI copy is
  Spanish to match the app. RUC validation moved to `shared/domain/RucValidator` so purchasing
  does not depend on the sales domain. Verified: `mvn clean test` 282 pass, 0 fail;
  `npm test` 64 pass; `npm run build` ok; `architecture-doc.mjs --check` up to date.
  RDD: assess medium, 1945 lines (T1+T2), `slice_budget_reached`. Consent granted. Lineage
  `review-ba9d2cd0c4626e6b`, 1 lens (reliability), approved and acknowledged (burned). The
  reviewed boundary is now `1ee2058`.
  Advisory follow-ups (non-blocking): (a) `SupplierRepositoryAdapter.java:35-38` maps every
  integrity violation to a duplicate RUC; narrow it to `uq_suppliers_ruc`. (b) The PUT product
  response can show stale stock (`ProductJpaEntity.java:41-46`).
  T3 must add the "inactive suppliers cannot be used in purchases" requirement to the
  purchasing spec. Next: T3, on a new stacked branch.
- 2026-10-08: T3 done on `feat/purchases-record` (stacked on `feat/purchases-suppliers`), commit
  `02a53aa`. Mapping: not found 404, inactive supplier/product 400, duplicate (supplier, invoice)
  409. Supplier and product names are snapshotted. The weighted average runs in one JPQL bulk
  UPDATE (`incrementStockAndAverageCost`), checked on H2 and on PostgreSQL 16 (Testcontainers).
  Verified: `mvn clean test` 335 pass (re-run by the parent); `architecture-doc.mjs --check` ok.
  RDD assess (base `1ee2058`): medium, 2515 lines, `slice_budget_reached`. Review is held until
  T3b lands, so T3 + T3b are reviewed together.
  T3b was added because a stale product edit overwrote the weighted-average cost; the user chose
  "cost only through purchases". Next: T3b (worker running), then review, then T4.
- 2026-10-17: T3 split into three reviewable commits on `feat/purchases-record-split`
  (`9f96c91`, `3d7a343`, `6e803cf`), T3b cherry-picked as `8bef16e`. The tree is identical to
  `backup/purchases-record-pre-split` (`fa84c92`). Verified: `mvn clean test` BUILD SUCCESS,
  infrastructure 142 pass, 0 fail (Docker up; `PurchaseConcurrencyPostgresTest` 4 pass);
  `architecture-doc.mjs --check` up to date. Slice boundary: PR3 = `9f96c91..8bef16e` (T3 + T3b).
  RDD assess (base `1ee2058`, committed-only, untracked excluded): medium, 2699 lines,
  `slice_budget_reached`. Next: native review of T3 + T3b, then T4.
- 2026-10-17: the full T3 + T3b range failed START with `lens_context_budget_exceeded`, so each
  commit was reviewed alone in a temporary detached worktree (since removed). All four: medium,
  consent granted, 1 lens (reliability), approved and acknowledged (burned):
  `9f96c91` `review-715965858611086d`, `3d7a343` `review-953d3b5250bdb2a4`,
  `6e803cf` `review-97957edb107acfda`, `8bef16e` `review-f41e47a4a7c8413f`.
  The reviewed boundary is now `8bef16e`.
  Advisory follow-ups (non-blocking):
  (c) `RecordPurchaseUseCaseTest.java:18` store date may make the test flaky.
  (d) `ProductJpaRepository.java:46-49` HALF_UP rounding of the weighted average is not proved
      on PostgreSQL.
  (e) `PurchaseRepositoryAdapter.java:61-65` duplicate-invoice mapping is not proved on
      PostgreSQL.
  (f) `PurchaseConcurrencyPostgresTest.java:136-141` timing threshold may be flaky.
  (g) `PurchaseControllerTest.java:93-113` decimal digits limits are not covered.
  (h) `RecordPurchaseRequest.java:17` invoice size is checked before trim.
  (i) `PurchaseController.java:47-50` a null element in `items` is not rejected cleanly.
  (j) `ProductControllerTest.java:184` the response cost price is not asserted.
  Next: T4 (frontend).
- 2026-10-17: T4 started on `feat/purchases-frontend` (stacked on `feat/purchases-record-split`).
  Route: delegated (one writer, multi-file frontend change; parent context past the backstop).
- 2026-10-17: T4 done, commit `16a3da0` (540 lines, mostly tests). `PurchaseForm` uses `noValidate`
  so its own validation messages render. UI copy is neutral Spanish ("Seleccione"), while
  `RecordSalePage` uses voseo ("Elegí"); pending a user call on consistency. No detail view yet
  (`getPurchase` exists). Verified: `npm test` 81 pass (parent re-run); `npm run build` ok;
  `architecture-doc.mjs --check` up to date after regeneration.
  RDD: medium, 543 lines, granted, 1 lens (reliability), lineage `review-c6040e8901734302`,
  approved and acknowledged (burned). The reviewed boundary is now `16a3da0`.
  Advisory follow-ups: (k) `PurchasesPage.jsx:19-26` supplier/product load errors are swallowed
  (empty selects, no message); (l) `PurchasesPage.test.jsx:53-60` list total not asserted;
  (m) `PurchaseForm.test.jsx:59-68` removing a middle line is untested.
  Side finding: `PriceTag` defaults to USD with es-AR formatting.
  Slice boundary: PR4 = `16a3da0` + T5. Next: T5 (Playwright E2E, docs/risks).
- 2026-10-17: UI copy decision (user): neutral Spanish everywhere, no voseo. The remaining voseo
  strings ("Elegí ..." in `RecordSalePage.jsx` and `ProductForm.jsx`) became "Seleccione ...".
  Test-first: `RecordSalePage.test.jsx` RED (1 failed), then GREEN; `npm test` 81 pass.
  Commit `408600c`. RDD assess: medium, 10 lines, `under_budget` (stays pending in the slice).
  Next: T5.
- 2026-10-17: T5 done, commit `037992c`. `frontend/e2e/purchase-flow.spec.js` seeds a product
  (stock 10, cost 100.00), creates a supplier and records 5 at 130 through the UI, then checks
  the list row, stock 15 and cost 110.00, and that a duplicate supplier + invoice gives a 409
  without changing stock or cost. Mutation check by the worker: expected cost 111 failed.
  `docs/architecture.md` purchase flow updated; new known risks: cost price not shown in the UI,
  backend error messages reach the UI in English with the supplier id, purchase form hides
  supplier/product load errors (follow-up (k)). `frontend/e2e/README.md` lists both specs and
  `npx playwright install chromium`.
  Verified: `npm run test:e2e` 3 passed (worker twice, parent re-run); `npm run build` ok;
  `npm test` 81 pass; `architecture-doc.mjs --check` up to date; backend unchanged since the
  last `mvn clean test` BUILD SUCCESS.
  RDD assess (base `16a3da0`, committed-only): medium, 177 lines, `under_budget`, so
  `408600c..037992c` is not reviewed natively.
  Slice boundaries: PR1 `2510209` (`feat/purchases`), PR2 `1ee2058` (`feat/purchases-suppliers`),
  PR3 `9f96c91..8bef16e` (`feat/purchases-record-split`), PR4 `16a3da0..037992c`
  (`feat/purchases-frontend`). All tasks done. Next: push and stacked PRs (user decision).
