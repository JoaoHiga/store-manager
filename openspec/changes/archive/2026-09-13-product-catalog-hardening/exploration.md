## Exploration: product-catalog-hardening

### Current State

Four non-blocking findings surfaced by the first real RDD review of `inventory-foundation` (lineage `review-c8ffa22f196fdd5e`, lens `review-reliability`, approved/acknowledged). Each already has exact file:line evidence from that review — this exploration is about picking the fix approach per finding, not rediscovering the problem.

### Findings and Approaches

**1. `CreateCategoryUseCase` check-then-act race (WARNING)**
Two concurrent identical-name creates both pass `findByName`; the second INSERT hits the DB's `categories.name` UNIQUE constraint, raising an unhandled `DataIntegrityViolationException` (500) instead of the tested `DuplicateCategoryNameException` path (409).
- **A. Catch the constraint violation in `CategoryRepositoryAdapter.save()`**, translate to `DuplicateCategoryNameException` there. — Low effort, single file, keeps the use case unchanged, the controller's existing `@ExceptionHandler(DuplicateCategoryNameException.class)` already covers it.
- B. DB-level `INSERT ... ON CONFLICT DO NOTHING` + re-check. — More moving parts, no real benefit at this scale.
- C. Application-level locking. — Overkill for a two-request race on an admin CRUD form.
- **Recommendation: A.**

**2. Negative `costPrice` unvalidated (WARNING)**
Accepted silently at every layer (domain constructor, DTOs, DB).
- **A. Validate at all three layers**, mirroring how `salePrice`/`stockQuantity` are already handled: `Product` constructor check, `@PositiveOrZero` on both `*Request` DTOs, new Flyway migration adding a `CHECK` constraint. — Consistent with the existing pattern for the other numeric fields; low effort.
- B. Domain-only validation. — Leaves the DB layer as a silent gap, inconsistent with `salePrice`'s existing `CHECK`.
- **Recommendation: A.** New migration `V2__add_cost_price_check.sql` — `V1` already shipped in the archived `inventory-foundation` change, so it's edited-in-place no longer; a fresh migration is the correct move now.

**3. DTOs don't bound string length against DB column widths (SUGGESTION)**
An over-length `name`/`brand`/`description` passes Bean Validation, reaches Postgres, and raises an unhandled `DataIntegrityViolationException` (500) instead of a clean 400.
- **A. Add `@Size(max = …)` to each DTO field, matching the exact column widths** already in `V1__init_catalog.sql` (`categories.name` 120, `products.name` 160, `products.brand` 80, `categories.description` 500, `products.description` 1000). — Direct, no new dependency, no migration needed (widths already exist).
- **Recommendation: A.**

**4. `ProductsPage` mutations swallow errors silently (SUGGESTION)**
`createMutation`/`updateMutation`/`deactivateMutation`/`activateMutation` have no `onError`, unlike `CategoriesPage`'s, which sets `formError`. A failed request (including the 400s from findings 1-3, once those exist) is indistinguishable from a slow success.
- **A. Add `onError` to all four mutations, surface via the same `role="alert"` pattern `CategoriesPage` already uses** — one shared error message area on the page. — Consistent with the existing pattern, no new UI component; this is the concrete mechanism gap the reviewer flagged, not the full toast-notification polish already deferred in `proposal.md`.
- B. Build the full toast/notification system now. — That's the already-deferred, separate "UX feedback for mutations" item; out of scope here, don't fold it in.
- **Recommendation: A.**

### Risks
- Finding 2's new migration must not collide with `V1` — confirmed `V1__init_catalog.sql` is the only migration on disk; `V2` is next.
- Finding 3's length limits must exactly match `V1`'s column widths (already fixed, just needs copying correctly) to avoid a DTO accepting what the DB would reject, or rejecting what the DB would accept.

### Ready for Proposal
Yes — all four fixes are small, well-evidenced by the review, and each has one clear recommended approach. No external research needed (internal bug fixes, not a claim needing outside evidence).
