# Design: Inventory Foundation — Product Catalog & Stock

## Technical Approach

Implements the `product-catalog` capability as a Hexagonal-Architecture backend (Spring Boot, multi-module Maven) plus a React SPA. The `application` module holds domain entities, ports, and use cases with zero framework dependency; the `infrastructure` module holds Spring Boot, JPA adapters, and REST controllers. The frontend calls the REST API through TanStack Query.

## Architecture Decisions

| Decision | Alternative(s) | Choice | Rationale |
|---|---|---|---|
| Backend module structure | Single module, package-by-layer | Multi-module Maven (`application` + `infrastructure`) | Build-enforced Hexagonal boundary — `application`'s `pom.xml` cannot depend on Spring, so the boundary can't silently erode (research C3; user-confirmed) |
| Product deletion | Hard delete (`DELETE` row) | Soft delete (`active` flag) | Preserves referential integrity for the future stock-movement ledger (user-confirmed) |
| SKU generation | Manual input; random UUID; derive from the primary key | Dedicated `product_sku_seq` DB sequence, formatted `PRD-000123` | Deterministic and race-free like reusing the primary key, but doesn't collide with it: an id-derived SKU can't satisfy `sku NOT NULL` (the id is only known *after* INSERT, but sku must be present *in* the INSERT) — corrected during PR3 implementation after the id-derivation approach failed against the real constraint. Still future-proofed — later just stop deriving and accept manual/barcode input instead |
| Frontend data fetching | Manual `useEffect` + `fetch` | TanStack Query | Removes hand-rolled loading/error/cache boilerplate for a frontend beginner (research C1) |
| Frontend scaffold/language | TypeScript-first | Vite + plain JavaScript | Vite is the current standard scaffold (research C2); JS-first matches beginner guidance (research C4) |
| Schema migrations | Hibernate `ddl-auto: update` | Flyway versioned migrations | Auditable, safe schema evolution from the very first table |
| Cross-origin access | No CORS config (default) | `WebConfig` allows `http://localhost:5173` on `/api/**` | Discovered missing during the PR5 E2E check: `curl` never exercises CORS (it's browser-enforced), so this gap was invisible to every backend test/manual check until the frontend actually needed it |

## Data Flow

    React SPA (TanStack Query)
        │ HTTP/JSON
        ▼
    *Controller (infrastructure.web) ──▶ DTO
        │
        ▼
    Use Case (application.usecase) ──depends on──▶ RepositoryPort (application.domain.port)
                                                          ▲ implements
                                                          │
                                            *RepositoryAdapter (infrastructure.persistence) ──▶ PostgreSQL

## File Changes

| File | Action | Description |
|---|---|---|
| `pom.xml` | Create | Maven parent aggregator; declares `application` + `infrastructure` modules |
| `application/pom.xml`, `.../domain/{Category,Product}.java` | Create | No Spring dependency; plain domain entities |
| `.../domain/port/{Category,Product}RepositoryPort.java` | Create | Output ports |
| `.../usecase/{Create,List,Update,Deactivate}*UseCase.java` | Create | Category & Product use cases |
| `infrastructure/pom.xml` | Create | spring-boot-starter-web, -data-jpa, postgresql, flyway |
| `.../persistence/{*JpaEntity,*JpaRepository,*RepositoryAdapter}.java` | Create | JPA mapping + port implementation |
| `.../web/{*Controller,*Dto}.java` | Create | REST input adapters |
| `.../resources/db/migration/V1__init_catalog.sql` | Create | Flyway: `categories`, `products` tables |
| `frontend/` (Vite scaffold) | Create | React SPA |
| `frontend/src/api/catalogApi.js` | Create | Fetch wrapper for the REST endpoints |
| `frontend/src/components/{atoms,molecules,organisms}/...` | Create | Atomic-design UI components |
| `frontend/src/pages/{Products,Categories}Page.jsx` | Create | Container components wiring TanStack Query to presentational components |

## Interfaces / Contracts

```
GET    /api/categories
POST   /api/categories
GET    /api/products?categoryId={id}
POST   /api/products
PUT    /api/products/{id}
PATCH  /api/products/{id}/deactivate
PATCH  /api/products/{id}/activate       # PR6 — see tasks.md
GET    /api/products?active=false        # PR6 — needed to find a product to reactivate
```

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | Use cases, domain validation | JUnit 5 + AssertJ in `application`, no Spring context |
| Integration | JPA adapters, REST controllers | `@DataJpaTest` (H2) + `@SpringBootTest`/MockMvc in `infrastructure` |
| Frontend | Components, API layer | Vitest + React Testing Library |

Backend test command: `./mvnw test` (root aggregator). Frontend test command: `npm test`. Both are new — `sdd-apply`'s first batch scaffolds them so Strict TDD can activate.

## Threat Matrix

N/A — no routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary in this change.

## Migration / Rollout

Greenfield: Flyway `V1__init_catalog.sql` creates `categories` and `products`. No existing data to migrate.

## Open Questions

None. Database engine confirmed with the user: **PostgreSQL** for the running app (H2 in-memory for tests), since the app is expected to run on a server/VPS reachable from outside the shop.
