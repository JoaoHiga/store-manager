# Tasks: Inventory Foundation — Product Catalog & Stock

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~1800-2400 (full-stack scaffold + CRUD + tests) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR1 → PR2 → PR3 → PR4 → PR5 |
| Delivery strategy | auto-chain |
| Chain strategy | stacked-to-main |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: stacked-to-main
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
| 1 | Backend Maven skeleton, Flyway migration, Spring Boot boots | PR 1 | `./mvnw -pl application,infrastructure test` | Boot the app, GET `/actuator/health` returns 200 | Delete `application/`, `infrastructure/`, root `pom.xml` |
| 2 | Category vertical slice (domain→REST) | PR 2 | `./mvnw test -Dtest=Category*` | `POST /api/categories` then `GET /api/categories` returns it | Delete Category-related files added in PR2 |
| 3 | Product vertical slice (domain→REST) | PR 3 | `./mvnw test -Dtest=Product*` | `POST /api/products` then `GET /api/products` returns it | Delete Product-related files added in PR3 |
| 4 | Frontend Vite skeleton, API layer, atoms/molecules | PR 4 | `npm test` | `npm run dev`, app loads at localhost | Delete `frontend/` |
| 5 | Catalog UI wired to real API (Categories + Products pages) | PR 5 | `npm test` | Manually create/edit/deactivate a product in the running UI | Revert `pages/` + `organisms/` added in PR5 |

## Phase 1: Backend Foundation (PR 1) — ✅ DONE
- [x] 1.1 Root `pom.xml`: Maven parent, declares `application` + `infrastructure` modules
- [x] 1.2 `application/pom.xml`: no Spring dependency; JUnit5 + AssertJ only
- [x] 1.3 `infrastructure/pom.xml`: spring-boot-starter-web, -data-jpa, postgresql, flyway-core
- [x] 1.4 `infrastructure/.../db/migration/V1__init_catalog.sql`: `categories`, `products` tables per design.md
- [x] 1.5 `infrastructure/.../StoreManagerApplication.java` + `application.yml` (Postgres, Flyway on)
- [x] 1.6 Trivial passing test in each module to prove `mvn test` runs — establishes the backend test command (2/2 tests pass; see apply-progress for evidence)

## Phase 2: Category Slice (PR 2) — ✅ DONE
- [x] 2.1 RED: test for `Category` creation validation (unique name) → `application/.../domain/Category.java`, `CategoryRepositoryPort.java`
- [x] 2.2 GREEN: `CreateCategoryUseCase`, `ListCategoriesUseCase`
- [x] 2.3 `CategoryJpaEntity`, `CategoryJpaRepository`, `CategoryRepositoryAdapter` (infrastructure), `@DataJpaTest`
- [x] 2.4 `CategoryController` + DTOs, `@SpringBootTest`/MockMvc covering spec scenarios (create, duplicate-name reject, list) — 17/17 tests pass, see apply-progress

## Phase 3: Product Slice (PR 3) — ✅ DONE
- [x] 3.1 RED: tests for Product validation (required fields, negative stock, invalid category) → `Product.java`, `ProductRepositoryPort.java`
- [x] 3.2 GREEN: `CreateProductUseCase` (SKU from dedicated `product_sku_seq` sequence — corrected from id-derivation, see apply-progress), `ListProductsUseCase` (filter by category, excludes inactive), `UpdateProductUseCase`, `DeactivateProductUseCase`
- [x] 3.3 `ProductJpaEntity`, `ProductJpaRepository`, `ProductRepositoryAdapter`, `@DataJpaTest`
- [x] 3.4 `ProductController` + DTOs, MockMvc tests covering every product-catalog spec scenario — 45/45 tests pass total, see apply-progress

## Phase 4: Frontend Foundation (PR 4) — ✅ DONE
- [x] 4.1 `npm create vite@latest frontend -- --template react`; add TanStack Query, Vitest, React Testing Library
- [x] 4.2 `frontend/src/api/catalogApi.js`: fetch wrapper for the 6 REST endpoints
- [x] 4.3 `frontend/src/components/atoms/`, `molecules/`: Button, Input, Badge (atoms), FormField, PriceTag (molecules)
- [x] 4.4 Trivial passing component test to prove `npm test` runs — 7/7 pass, see apply-progress

## Phase 5: Catalog UI (PR 5) — ✅ DONE
- [x] 5.1 `frontend/src/components/organisms/{ProductList,ProductForm,CategoryList}.jsx` (presentational)
- [x] 5.2 `frontend/src/pages/{Products,Categories}Page.jsx` (containers wiring TanStack Query to organisms) — plus `WebConfig` CORS fix found during E2E
- [x] 5.3 Component tests for organisms/pages: create, edit, deactivate a product against a mocked API — 18/18 pass, two real bugs found+fixed (stale form on edit, mutationFn arg passing), see apply-progress
- [x] 5.4 Manual E2E check: full flow against the real backend from PR1-3 — user confirmed full click-through (create/edit/deactivate) in their own browser, no issues

## Phase 6: Product Reactivation (PR 6) — ✅ DONE
- [x] 6.1 RED: test for `ReactivateProductUseCase` (idempotent, same pattern as `DeactivateProductUseCase`) → `application/usecase/ReactivateProductUseCase.java`
- [x] 6.2 `ProductRepositoryPort`/adapter generalized: `findByActive(boolean)`/`findByActiveAndCategoryId(boolean, Long)` replace `findAllActive`/`findActiveByCategoryId`
- [x] 6.3 `ProductController`: `PATCH /api/products/{id}/activate` + `GET /api/products` accepts `?active=false`
- [x] 6.4 Frontend: "Ver productos inactivos" toggle on `ProductsPage` + "Reactivar" button in `ProductList` for inactive rows, `activateProduct` in `catalogApi.js`
- [x] 6.5 Tests: backend 52/52, frontend 20/20 — see apply-progress
