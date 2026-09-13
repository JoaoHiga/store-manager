# Design: Product Catalog Hardening

## Technical Approach

Four small, independent point-fixes closing gaps the first RDD review found in `product-catalog`. No new capability, no new endpoint — tightened validation and one error-translation fix.

## Architecture Decisions

| Decision | Alternative(s) | Choice | Rationale |
|---|---|---|---|
| Where to catch the category-name race | In the use case (pre-check with a retry); in the controller (generic exception mapping) | Catch `DataIntegrityViolationException` in `CategoryRepositoryAdapter.save()`, translate to `DuplicateCategoryNameException` | Closest to where the failure actually originates (the DB constraint); the use case and controller stay unchanged — the controller's existing `@ExceptionHandler(DuplicateCategoryNameException.class)` already produces the right 409 |
| `costPrice` validation depth | Domain-only | Domain constructor + DTO `@PositiveOrZero` + DB `CHECK` (new `V2` migration) | Matches the existing pattern for `salePrice`/`stockQuantity` — validating at only one layer would be an inconsistency, not a simplification |
| String length bounds | Domain-level | DTO-level `@Size(max = …)` only | The domain layer validates no field's length today (not even `name`'s non-blank check duplicates as a length check); DTO is where `@NotBlank` already lives for these fields, so `@Size` joins the same layer |
| Frontend error surfacing | New toast/notification component | Reuse the existing `role="alert"` element `CategoriesPage` already renders on `formError` | Matches the concrete gap the reviewer named (no visible failure state) without pulling in the separately-deferred full toast system |

## Data Flow (category-name race fix)

    POST /api/categories (two concurrent requests, same name)
        │
        ▼
    CreateCategoryUseCase.execute() — both pass findByName (neither committed yet)
        │
        ▼
    CategoryRepositoryAdapter.save() — INSERT
        ├─ first request: commits → 201
        └─ second request: DataIntegrityViolationException (unique violation)
              │
              ▼
           caught, rethrown as DuplicateCategoryNameException
              │
              ▼
           CategoryController's existing @ExceptionHandler → 409 (not 500)

## File Changes

| File | Action | Description |
|---|---|---|
| `infrastructure/.../persistence/CategoryRepositoryAdapter.java` | Modify | Catch `DataIntegrityViolationException`, rethrow `DuplicateCategoryNameException` |
| `application/.../domain/Product.java` | Modify | Validate `costPrice == null \|\| costPrice.signum() >= 0` |
| `infrastructure/.../web/CreateProductRequest.java`, `UpdateProductRequest.java` | Modify | `@PositiveOrZero costPrice`, `@Size(max=160) name`, `@Size(max=80) brand` |
| `infrastructure/.../web/CreateCategoryRequest.java` | Modify | `@Size(max=120) name` |
| `infrastructure/src/main/resources/db/migration/V2__add_cost_price_check.sql` | Create | `ALTER TABLE products ADD CONSTRAINT chk_products_cost_price_non_negative CHECK (cost_price IS NULL OR cost_price >= 0)` |
| `frontend/src/pages/ProductsPage.jsx` | Modify | `onError` on all 4 mutations, shared `role="alert"` error element |

## Interfaces / Contracts

No new endpoints. Behavior changes on existing ones:
- `POST /api/categories`: a concurrent duplicate now always resolves to one `201` + one `409`, never a `500`.
- `POST /api/products`, `PUT /api/products/{id}`: `costPrice < 0` → `400`; `name`/`brand` over length → `400`.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `Product` costPrice validation | `ProductTest` — reject negative, allow null/zero/positive |
| Integration | Adapter race-condition translation | `CategoryRepositoryAdapterTest` — insert same name twice directly via the adapter, assert the second throws `DuplicateCategoryNameException` (deterministic proxy for the concurrent case, since true concurrency isn't practical to assert in a unit test) |
| Integration | Controller validation | `ProductControllerTest`/`CategoryControllerTest` — negative costPrice → 400, over-length name/brand → 400 |
| Frontend | Mutation failure visibility | `ProductsPage.test.jsx` — mock a rejected mutation, assert the error text renders |

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary in this change.

## Migration / Rollout

`V2__add_cost_price_check.sql` adds a `CHECK` constraint. No existing data to migrate — still pre-first-deploy.

## Open Questions

None.
