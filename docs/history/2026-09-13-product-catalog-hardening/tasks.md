# Tasks: Product Catalog Hardening

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~180-250 (4 small point-fixes + their tests) |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | auto-chain |
| Chain strategy | n/a (single PR, under budget) |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: n/a
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | All 4 hardening fixes, backend + frontend | PR 1 (single) | `mvn test && (cd frontend && npm test)` | Manual: two near-simultaneous `curl` POSTs with the same category name | Revert the ~6 touched files + delete `V2__add_cost_price_check.sql` |

## Phase 1: Backend Validation & Race Fix — ✅ DONE
- [x] 1.1 RED: `ProductTest` — reject negative `costPrice`, accept null/zero/positive → `application/.../domain/Product.java`
- [x] 1.2 GREEN: add `costPrice` check to `Product`'s constructor
- [x] 1.3 `infrastructure/.../db/migration/V2__add_cost_price_check.sql`: `CHECK (cost_price IS NULL OR cost_price >= 0)`
- [x] 1.4 `CreateProductRequest`/`UpdateProductRequest`: `@PositiveOrZero costPrice`, `@Size(max=160) name`, `@Size(max=80) brand`
- [x] 1.5 `CreateCategoryRequest`: `@Size(max=120) name`
- [x] 1.6 RED: `CategoryRepositoryAdapterTest` — save the same name twice, assert the 2nd throws `DuplicateCategoryNameException`
- [x] 1.7 GREEN: `CategoryRepositoryAdapter.save()` catches `DataIntegrityViolationException`, rethrows `DuplicateCategoryNameException`

## Phase 2: Backend Tests (controller-level) — ✅ DONE
- [x] 2.1 `ProductControllerTest`: negative `costPrice` → 400 (create + update)
- [x] 2.2 `ProductControllerTest`: over-length `name`/`brand` → 400 (create + update)
- [x] 2.3 `CategoryControllerTest`: over-length `name` → 400

## Phase 3: Frontend Error Visibility — ✅ DONE
- [x] 3.1 RED: `ProductsPage.test.jsx` — mock a rejected `createProduct`, assert an error message renders
- [x] 3.2 GREEN: `ProductsPage.jsx` — `onError` on all 4 mutations (`createMutation`, `updateMutation`, `deactivateMutation`, `activateMutation`), shared `role="alert"` error element (same pattern as `CategoriesPage`)
- [x] 3.3 Repeat 3.1/3.2's assertion for `deactivateMutation` (one more test, reusing the same mock-and-assert shape)

## Phase 4: Verification — ✅ DONE
- [x] 4.1 `mvn clean test` — full backend suite green (62/62)
- [x] 4.2 `npm test` — full frontend suite green (22/22)
- [x] 4.3 Manual: fired two near-simultaneous `curl -X POST /api/categories` with the same name against the real running backend; confirmed one 201 + one 409, never a 500. Also confirmed negative `costPrice` → 400 and over-length `name` → 400.

## Phase 5: RDD Review Follow-up — ✅ DONE
The first real RDD review run against this change (lineage `review-aee0849acdb847e2`, approved/acknowledged) found 3 more non-blocking advisory findings:
- [x] 5.1 `description` had no length bound on any DTO — a real gap against this change's own `design.md`/`exploration.md` (which named it), narrowed to name/brand only during implementation. Fixed: `@Size(max=1000)`/`@Size(max=500)` added to product/category `description` fields; new `ProductControllerTest`/`CategoryControllerTest` cases.
- [x] 5.2 Investigated a claimed `NaN` gap in `ProductForm`'s sale-price validation. **Not reproducible**: a regression test (`fireEvent.change` with a non-numeric value) passed without any code change, because the real `<input type="number">` DOM element already sanitizes to `""` before the JS validation runs — the existing `!values.salePrice` check already catches it. Kept the test as a proof/regression guard; no source change.
- [x] 5.3 `findByActive(boolean)` (no-category-filter listing) was only tested via the in-memory fake, never at the real JPA/adapter level. Added `ProductRepositoryAdapterTest#findsOnlyActiveProductsAcrossAllCategoriesWhenNoCategoryFilterIsGiven`.

Final: 65/65 backend tests, 23/23 frontend tests, both builds green.
