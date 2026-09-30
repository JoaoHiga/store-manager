## Exploration: inventory-foundation

### Current State

Greenfield repository — `git init` only, zero source code, zero existing schema or conventions. `sdd-init` recorded the confirmed stack (Java + Spring Boot backend, React frontend) and domain (multi-category retail inventory: cellphones, phone accessories, speakers, cases, tempered-glass protectors, computer accessories, other related items — not category-specialized). No codebase discovery applies; this exploration is about scoping the first buildable slice and its domain/architecture shape.

### Affected Areas

- N/A — no existing files. This change will create the initial backend and frontend project structure.

### Approaches

**MVP domain-modeling approach:**

1. **Per-category vertical slice first** — build full CRUD for one category (e.g. cellphones) end-to-end, extend to other categories later.
   - Pros: fastest single working demo, proves the hexagonal + React wiring once.
   - Cons: artificial split — the categories share almost identical fields (name, SKU, price, stock, category), so category-first slicing adds a rework step for no real benefit.
   - Effort: Low-Medium

2. **Flexible/EAV attribute schema for all categories at once** — generic `Product` with a dynamic attribute system so any category can carry arbitrary extra fields.
   - Pros: never needs a schema migration when a new product type shows up.
   - Cons: real over-engineering for a small shop that explicitly does not need per-category specialization yet; harder for a Java-strong-but-frontend-beginner owner to reason about; slower to ship.
   - Effort: Medium-High

3. **Single generic `Product` entity with a `Category` reference, common fields only** — one entity (SKU, name, description, category, price, cost, stock quantity), no per-category custom fields for now.
   - Pros: matches what the user already told us ("sin ser de especialidad por ahora"), simplest correct model, ships fast, easy to extend later if a real need for per-category fields appears.
   - Cons: none material for this stage.
   - Effort: Low

**Inventory-tracking approach:**

1. **Mutable stock counter only** (`Product.stockQuantity`, edited directly).
   - Pros: simplest possible, fastest to ship.
   - Cons: no audit trail of why stock changed — loses the actual point of "managing inventory" once the shop is doing real sales/restocks.
   - Effort: Low

2. **Stock-movement ledger** (`StockMovement`: product, type IN/OUT, quantity, reason, timestamp; current stock derived or cached).
   - Pros: correct domain model for inventory (Screaming Architecture: the domain concept IS "stock moves in and out"), gives traceability for free later (who sold/restocked what, when).
   - Cons: bigger first change if built immediately alongside catalog CRUD.
   - Effort: Medium

### Recommendation

Scope **this first change ("inventory-foundation")** to: `Category` + `Product` entities (approach 3 above — generic schema, no per-category fields), basic CRUD (create/edit/list/delete, filter by category), and a plain `stockQuantity` field (approach 1 for tracking) — plus the Spring Boot Hexagonal skeleton and the React SPA skeleton wired end-to-end against it. Defer the stock-movement ledger, low-stock alerts, and sales tracking to a **follow-up change** once the foundation is proven and shippable. This keeps the first change small enough for the chained-PR budget, gives a working demo fast, and avoids modeling movements before the catalog itself exists.

**Backend shape (Hexagonal, single Maven module, package-by-layer — not multi-module, to keep build complexity low while learning):**
- `domain`: `Product`, `Category` entities/value objects; `ProductRepositoryPort`, `CategoryRepositoryPort` interfaces.
- `application`: use-case services (`CreateProductUseCase`, `ListProductsUseCase`, `UpdateStockUseCase`, etc.) implementing input ports, depending only on domain ports.
- `infrastructure`: Spring Data JPA adapters implementing the output ports, REST controllers (input adapters) with DTOs separate from domain entities, Spring Boot wiring/config.

**Frontend shape (React SPA, Vite):**
- Atomic design component structure (atoms/molecules/organisms) + container/presentational split, matching the user's stated interest.
- A thin API service layer wrapping calls to the Spring Boot REST API.
- Recommend **TanStack Query** for server-state (loading/error/cache) instead of hand-rolled `useState`/`useEffect` fetching — meaningfully less boilerplate for someone learning React, defer Redux/Zustand-style global state until a real need appears (none yet for a CRUD app).
- Open question for `sdd-propose`/`sdd-design`: TypeScript vs. plain JavaScript for the frontend. Not decided here — flagging because the user's Java background may make TypeScript's static typing feel more natural, but it is one more thing to learn at once.

### Risks

- Zero workspace-level test command exists yet (Strict TDD is currently `false`, failed closed). The first `sdd-apply` batch MUST scaffold real `mvn`/`./mvnw test` and `npm test` commands so Strict TDD can activate for all following work.
- Combining backend scaffold + frontend scaffold + basic CRUD in one change can exceed the 400-line PR budget — `auto-chain` delivery strategy is already selected, so `sdd-tasks` should plan the slice boundaries (e.g. backend skeleton → frontend skeleton → wiring) accordingly.
- Scope creep risk: resist adding per-category fields or a movement ledger into this first change; both have a clear follow-up change instead.

### Ready for Proposal

Yes. Scope is clear: `Category` + `Product` CRUD with a plain stock quantity field, Spring Boot Hexagonal skeleton, React SPA skeleton (atomic design + TanStack Query), wired end-to-end. Ready for `sdd-propose` (research is optional here — this is architecture/scoping reasoning, not a claim needing external evidence).
