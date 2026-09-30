# Proposal: Product Catalog Hardening

## Intent

Fix four real, non-blocking findings surfaced by the first RDD review of `inventory-foundation` (lineage `review-c8ffa22f196fdd5e`, approved/acknowledged): a category-creation race condition, an unvalidated negative cost price, unbounded string lengths reaching the DB raw, and silent mutation failures in the frontend. None were candidate-caused-and-blocking at review time, but all are real gaps worth closing before they surface in the shop's real usage.

## Scope

### In Scope
- Translate a concurrent-duplicate-category-name DB constraint violation into the existing clean 409 path instead of an unhandled 500
- Validate `costPrice >= 0` at domain, DTO, and DB layers (matching how `salePrice`/`stockQuantity` are already validated)
- Bound `name`/`brand`/`description` DTO field lengths to match their DB column widths
- Surface mutation failures (create/edit/deactivate/reactivate) in the frontend UI instead of silently swallowing them

### Out of Scope
- Full toast/notification UX system (already deferred separately in `inventory-foundation`'s proposal — this only adds the minimal `role="alert"` surfacing the reviewer flagged as the concrete gap)
- Any new capability or endpoint — this is hardening `product-catalog`, not extending it

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `product-catalog`: adds reliability/validation requirements (race-safe category creation, cost-price bounds, DTO length bounds, visible mutation failures) to the existing capability's behavior.

## Approach

1. `CategoryRepositoryAdapter.save()` catches `DataIntegrityViolationException` from the unique-constraint insert, re-throws `DuplicateCategoryNameException` — no use-case or controller change needed.
2. `Product`'s constructor validates `costPrice` (null or ≥ 0); `@PositiveOrZero` added to both `*ProductRequest` DTOs; new Flyway `V2__add_cost_price_check.sql` adds the DB `CHECK`.
3. `@Size(max = …)` added to `CreateCategoryRequest.name`, `*ProductRequest.name`/`.brand`, matching `V1__init_catalog.sql`'s exact column widths.
4. `ProductsPage`'s four `useMutation` calls gain `onError`, surfaced via the same `role="alert"` element pattern `CategoriesPage` already uses.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `infrastructure/.../persistence/CategoryRepositoryAdapter.java` | Modified | Catch + translate constraint violation |
| `application/.../domain/Product.java` | Modified | costPrice validation |
| `infrastructure/.../web/{CreateProductRequest,UpdateProductRequest,CreateCategoryRequest}.java` | Modified | `@PositiveOrZero`, `@Size` |
| `infrastructure/src/main/resources/db/migration/V2__add_cost_price_check.sql` | New | DB CHECK constraint |
| `frontend/src/pages/ProductsPage.jsx` | Modified | `onError` on 4 mutations + error display |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| New migration numbering collides with a future change | Low | Confirmed `V1` is the only existing migration; `V2` is next in sequence |
| DTO length limits drift from DB column widths over time | Low | Copied directly from `V1__init_catalog.sql` at write time; both live in the same PR |

## Rollback Plan

Each of the 4 fixes is independent and additive/corrective — revert the specific file(s) or `git revert` the merge commit(s). The new migration (`V2`) would need a compensating down-migration only if it had already run against a real deployed database; it has not (still pre-first-deploy).

## Dependencies

None beyond what `inventory-foundation` already established.

## Success Criteria

- [ ] Two concurrent identical-name category creates: exactly one succeeds (201), the other returns 409, neither returns 500
- [ ] Creating/updating a product with `costPrice < 0` returns 400 at the API layer
- [ ] Creating/updating a product/category with an over-length `name`/`brand` returns 400, never reaches the DB
- [ ] A failed mutation on `ProductsPage` (create/edit/deactivate/reactivate) shows a visible error instead of silently doing nothing
- [ ] `mvn test` and `npm test` both pass
