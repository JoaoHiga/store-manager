# Architecture

How store-manager is built: the pieces, the technologies, where each one lives and how a request moves
through the system.

> **Two kinds of content live in this file.**
> - Sections between `<!-- BEGIN GENERATED -->` / `<!-- END GENERATED -->` markers are **generated from the
>   code** by `node scripts/architecture-doc.mjs`. Never edit them by hand; re-run the script. CI fails if
>   they are out of date.
> - Everything else is **hand-written** and must be updated in the same change that alters it (see the
>   rule in `AGENTS.md`).

## 1. The big picture

```
┌──────────────────────┐   HTTP + JSON    ┌───────────────────────────┐     SQL     ┌────────────┐
│  Frontend (React)    │ ───────────────► │  Backend (Spring Boot)    │ ──────────► │ PostgreSQL │
│  what the cashier    │   /api/...       │  business rules           │             │  the data  │
│  sees and clicks     │                  └─────────────┬─────────────┘             └────────────┘
└──────────────────────┘                                │ HTTPS
                                                        ▼
                                                ┌────────────────┐
                                                │ NubeFacT (OSE) │ ──► SUNAT
                                                └────────────────┘
```

Three separate programs. The frontend **never** talks to the database; it always asks the backend. The
backend is the only piece that talks to PostgreSQL and to NubeFacT, the paid service that forwards
electronic vouchers (boletas, facturas) to SUNAT.

## 2. Technologies

### Backend (repo root, Java)

What each piece is for:

- **Java** — the backend language.
- **Maven** — builds the project and manages libraries. The project is split into two Maven modules,
  `application` and `infrastructure` (see section 3).
- **Spring Boot** — framework that runs the web server, connects to the database and wires the pieces
  together. Used **only** in `infrastructure`.
- **Spring Web** (`spring-boot-starter-web`) — receives HTTP requests in `*Controller` classes.
- **Spring Data JPA / Hibernate** (`spring-boot-starter-data-jpa`) — maps Java objects (`*JpaEntity`) to
  database tables, so most SQL is not written by hand. Hibernate only *validates* the schema
  (`ddl-auto: validate`); it never creates tables.
- **Bean Validation** (`spring-boot-starter-validation`) — checks request bodies (`@Valid`, `@NotNull`).
- **Actuator** — exposes `/actuator/health`.
- **PostgreSQL** — the database. Connection settings come from environment variables in
  `infrastructure/src/main/resources/application.yml`.
- **Flyway** — owns the database schema. Each change is a numbered SQL file (`V1__...sql`, `V2__...sql`)
  applied once, in order. **Never edit an applied migration**: Flyway checksums them and refuses to start.
- **JUnit 5 + AssertJ** — unit tests. **Testcontainers** — starts a real PostgreSQL in Docker for
  integration and E2E tests. **H2** — in-memory database for some Spring tests.

<!-- BEGIN GENERATED: tech-backend -->
| Technology | Module | Scope |
|---|---|---|
| Java 21 | root `pom.xml` | — |
| Spring Boot (BOM) | root `pom.xml` | — |
| junit-jupiter | `application` | test |
| assertj-core | `application` | test |
| spring-boot-starter-web | `infrastructure` | compile |
| spring-boot-starter-data-jpa | `infrastructure` | compile |
| spring-boot-starter-actuator | `infrastructure` | compile |
| spring-boot-starter-validation | `infrastructure` | compile |
| postgresql | `infrastructure` | runtime |
| flyway-core | `infrastructure` | compile |
| flyway-database-postgresql | `infrastructure` | compile |
| spring-boot-starter-test | `infrastructure` | test |
| h2 | `infrastructure` | test |
| spring-boot-testcontainers | `infrastructure` | test |
| postgresql | `infrastructure` | test |
| junit-jupiter | `infrastructure` | test |
<!-- END GENERATED: tech-backend -->

### Frontend (`frontend/`, JavaScript)

- **React** — builds the UI out of components.
- **Vite** — development server (`npm run dev`, port 5173) and production bundler (`npm run build`).
- **TanStack Query** — fetches data from the backend, caches it and tracks loading/error states. Used in
  pages through `useQuery` (reads) and `useMutation` (writes).
- **Vitest + Testing Library + jsdom** — component and unit tests (`*.test.jsx`), run with `npm test`.
- **Playwright** — opens a real browser against the real backend and database: full-stack E2E tests in
  `frontend/e2e/`, run with `npm run test:e2e`.
- **oxlint** — linter (`npm run lint`).

<!-- BEGIN GENERATED: tech-frontend -->
| Package | Kind |
|---|---|
| @tanstack/react-query | runtime |
| react | runtime |
| react-dom | runtime |
| @playwright/test | dev/test |
| @testing-library/jest-dom | dev/test |
| @testing-library/react | dev/test |
| @testing-library/user-event | dev/test |
| @types/react | dev/test |
| @types/react-dom | dev/test |
| @vitejs/plugin-react | dev/test |
| jsdom | dev/test |
| oxlint | dev/test |
| vite | dev/test |
| vitest | dev/test |
<!-- END GENERATED: tech-frontend -->

### Outside the code

- **NubeFacT** — electronic invoicing provider (OSE). Credentials come from environment variables
  (`NUBEFACT_BASE_URL`, `NUBEFACT_API_TOKEN`); locally from `.env.nubefact.local`, never committed.
- **GitHub Actions** — `.github/workflows/ci.yml` runs backend tests, frontend lint + tests + build,
  full-stack E2E and the architecture-doc check on every pull request and on every push to `main`. It does
  **not** deploy. A new push to a pull request cancels its stale run; every job has a timeout.
- **SonarQube Cloud** — automatic analysis (GitHub app) on `main` and every pull request, outside the
  workflow and without coverage. `.sonarcloud.properties` excludes the `*JpaEntity` classes from
  copy-paste detection, because they mirror their domain twins by design.
- **Dependabot** — `.github/dependabot.yml` opens weekly update PRs for GitHub Actions, npm (`frontend/`)
  and Maven.
- **Docker Compose** — `docker-compose.yml` runs the local development PostgreSQL on port 5432 with the
  same defaults as `application.yml`. How to run the whole stack locally: the root `README.md`.

## 3. Backend architecture: hexagonal (ports and adapters)

Think of a house with wall sockets:

- **The house** is the business: "a factura requires a RUC", "you cannot sell what is not in stock". It
  lives in the `application` module and is **plain Java**: it has no idea that Spring, PostgreSQL or
  NubeFacT exist. The Maven module has no Spring dependency, so this is enforced by the build, not by
  discipline.
- **The sockets are ports**: Java interfaces in `domain/port/` that say *what* the business needs
  ("save a sale", "issue a boleta") without saying *how*.
- **The appliances are adapters**: classes in the `infrastructure` module that implement a port with a
  concrete technology (JPA for PostgreSQL, HTTP for NubeFacT). Web controllers are adapters too, in the
  other direction: they translate HTTP into use-case calls.

Why bother? Swapping NubeFacT for another provider means writing one new adapter; the business rules do
not change. And the rules can be tested with in-memory fakes, without a database or network
(see `application/src/test/.../InMemory*Repository.java`).

**Dependency rule:** `infrastructure` depends on `application`, never the reverse.

### Domains

The backend is split into four business domains. Each one has the same internal layout:

```
<domain>/
├── domain/           entities and their rules            (application module)
├── domain/port/      interfaces the domain needs          (application module)
├── usecase/          one class per business action        (application module)
└── infrastructure/   adapters: web/, persistence/, ...    (infrastructure module)
```

- **catalog** — products and categories.
- **sales** — recording, listing and cancelling sales.
- **invoicing** — electronic vouchers (boleta/factura), their issuance, retries and voiding via NubeFacT.
- **purchasing** — suppliers and purchases. A purchase is how stock comes in: it increments product stock
  and recomputes the cost price.
- **shared** — not a business domain: cross-cutting rules and ports every domain may use, such as
  `RucValidator` (SUNAT RUC format and check digit) and `TransactionRunner` (run a unit of work
  atomically).

<!-- BEGIN GENERATED: backend-domains -->
| Domain | Domain model (`domain/`) | Ports (`domain/port/`) | Use cases (`usecase/`) | Infrastructure (files) |
|---|---|---|---|---|
| `catalog` | `Category`, `Product` | `CategoryRepositoryPort`, `ProductRepositoryPort` | `CreateCategoryUseCase`, `CreateProductUseCase`, `DeactivateProductUseCase`, `ListCategoriesUseCase`, `ListProductsUseCase`, `ReactivateProductUseCase`, `UpdateProductUseCase` | `config/ (2)`, `persistence/ (7)`, `web/ (8)` |
| `invoicing` | `Voucher`, `VoucherStatus`, `VoucherType` | `InvoicingProvider`, `VoucherRepositoryPort` | `IssueVoucherForSaleUseCase`, `RetryPendingVoucherUseCase`, `VoidSaleUseCase` | `config/ (1)`, `nubefact/ (9)`, `persistence/ (5)`, `scheduling/ (1)` |
| `purchasing` | `Purchase`, `PurchaseItem`, `Supplier` | `PurchaseRepositoryPort`, `SupplierRepositoryPort` | `CreateSupplierUseCase`, `DeactivateSupplierUseCase`, `GetPurchaseUseCase`, `ListPurchasesUseCase`, `ListSuppliersUseCase`, `ReactivateSupplierUseCase`, `RecordPurchaseUseCase` | `persistence/ (7)`, `web/ (10)` |
| `sales` | `BuyerDocumentValidator`, `Sale`, `SaleItem`, `SaleStatus` | `SaleRepositoryPort` | `CancelSaleUseCase`, `GetSaleUseCase`, `ListSalesUseCase`, `RecordSaleUseCase`, `RetrySaleVoucherUseCase` | `persistence/ (4)`, `web/ (7)` |
| `shared` | `RucValidator` | `TransactionRunner` | — | `transaction/ (1)` |
<!-- END GENERATED: backend-domains -->

### Ports and the adapters that implement them

<!-- BEGIN GENERATED: ports-adapters -->
| Port (interface) | Declared in | Implemented by (adapter) |
|---|---|---|
| `CategoryRepositoryPort` | `application/…/catalog/domain/port/CategoryRepositoryPort.java` | `infrastructure/…/catalog/infrastructure/persistence/CategoryRepositoryAdapter.java` |
| `ProductRepositoryPort` | `application/…/catalog/domain/port/ProductRepositoryPort.java` | `infrastructure/…/catalog/infrastructure/persistence/ProductRepositoryAdapter.java` |
| `InvoicingProvider` | `application/…/invoicing/domain/port/InvoicingProvider.java` | `infrastructure/…/invoicing/infrastructure/nubefact/NubeFactInvoicingAdapter.java` |
| `VoucherRepositoryPort` | `application/…/invoicing/domain/port/VoucherRepositoryPort.java` | `infrastructure/…/invoicing/infrastructure/persistence/VoucherRepositoryAdapter.java` |
| `PurchaseRepositoryPort` | `application/…/purchasing/domain/port/PurchaseRepositoryPort.java` | `infrastructure/…/purchasing/infrastructure/persistence/PurchaseRepositoryAdapter.java` |
| `SupplierRepositoryPort` | `application/…/purchasing/domain/port/SupplierRepositoryPort.java` | `infrastructure/…/purchasing/infrastructure/persistence/SupplierRepositoryAdapter.java` |
| `SaleRepositoryPort` | `application/…/sales/domain/port/SaleRepositoryPort.java` | `infrastructure/…/sales/infrastructure/persistence/SaleRepositoryAdapter.java` |
| `TransactionRunner` | `application/…/shared/domain/port/TransactionRunner.java` | `infrastructure/…/shared/infrastructure/transaction/SpringTransactionRunner.java` |
<!-- END GENERATED: ports-adapters -->

### Wiring: who plugs what into which socket

Use cases are plain Java classes with constructor parameters, so Spring does not discover them on its own.
`infrastructure/.../catalog/infrastructure/config/UseCaseConfiguration.java` declares one `@Bean` per use
case and passes it the concrete adapters. It is the house's fuse box: when a use case gains a new port,
this is where it gets plugged in. (It lives under `catalog` for historical reasons but wires every domain.)

Ports that are not repositories are wired the same way. `TransactionRunner` (in `shared`) is how a use
case asks for "all of this or nothing" without importing Spring: `SpringTransactionRunner` implements it
with a `TransactionTemplate`, so everything written through repository ports inside it commits or rolls
back together.

`WebConfig.java`, next to `UseCaseConfiguration`, configures CORS so the Vite dev server (`http://localhost:5173`) may call
the API.

### REST API

<!-- BEGIN GENERATED: rest-endpoints -->
| Method | Path | Handler |
|---|---|---|
| POST | `/api/categories` | `CategoryController.create` |
| GET | `/api/categories` | `CategoryController.list` |
| POST | `/api/products` | `ProductController.create` |
| GET | `/api/products` | `ProductController.list` |
| PUT | `/api/products/{id}` | `ProductController.update` |
| PATCH | `/api/products/{id}/deactivate` | `ProductController.deactivate` |
| PATCH | `/api/products/{id}/activate` | `ProductController.activate` |
| POST | `/api/purchases` | `PurchaseController.record` |
| GET | `/api/purchases` | `PurchaseController.list` |
| GET | `/api/purchases/{id}` | `PurchaseController.get` |
| POST | `/api/suppliers` | `SupplierController.create` |
| GET | `/api/suppliers` | `SupplierController.list` |
| PATCH | `/api/suppliers/{id}/deactivate` | `SupplierController.deactivate` |
| PATCH | `/api/suppliers/{id}/activate` | `SupplierController.activate` |
| POST | `/api/sales` | `SaleController.record` |
| GET | `/api/sales` | `SaleController.list` |
| GET | `/api/sales/{id}` | `SaleController.get` |
| POST | `/api/sales/{id}/retry-voucher` | `SaleController.retryVoucher` |
| POST | `/api/sales/{id}/void` | `SaleController.voidSale` |
<!-- END GENERATED: rest-endpoints -->

### Database schema history (Flyway)

<!-- BEGIN GENERATED: migrations -->
| Version | Description |
|---|---|
| V10 | create purchases |
| V1 | init catalog |
| V2 | add cost price check |
| V3 | create sales |
| V4 | create vouchers |
| V5 | create voucher correlativo sequences |
| V6 | make buyer document nullable |
| V7 | widen voucher rejection reason |
| V8 | reseed voucher correlativo series |
| V9 | create suppliers |
<!-- END GENERATED: migrations -->

## 4. Frontend architecture

- **Atomic design**: small pieces compose into bigger ones. Atoms (`Button`, `Input`) → molecules
  (`FormField`) → organisms (`ProductForm`, `ProductList`) → pages.
- **Container / presentational**: pages fetch data and hold state (TanStack Query); components below
  them only receive props and render.
- **API clients** (`frontend/src/api/*.js`) are thin `fetch` wrappers with no React in them. The backend
  URL comes from `VITE_API_BASE_URL` (default `http://localhost:8080`).
- **No router**: `App.jsx` renders every page one below the other on a single screen.

<!-- BEGIN GENERATED: frontend-structure -->
| Layer | Folder | Files |
|---|---|---|
| Pages | `frontend/src/pages/` | `CategoriesPage.jsx`, `ProductsPage.jsx`, `PurchasesPage.jsx`, `RecordSalePage.jsx`, `SalesListPage.jsx`, `SuppliersPage.jsx` |
| Organisms | `frontend/src/components/organisms/` | `CategoryList.jsx`, `ProductForm.jsx`, `ProductList.jsx`, `PurchaseForm.jsx`, `PurchaseList.jsx`, `SupplierForm.jsx`, `SupplierList.jsx` |
| Molecules | `frontend/src/components/molecules/` | `FormField.jsx`, `PriceTag.jsx` |
| Atoms | `frontend/src/components/atoms/` | `Badge.jsx`, `Button.jsx`, `Input.jsx` |
| API clients | `frontend/src/api/` | `catalogApi.js`, `purchasingApi.js`, `salesApi.js` |
<!-- END GENERATED: frontend-structure -->

## 5. Main flow: recording a sale

1. **`RecordSalePage.jsx`** — the cashier submits. `useMutation` calls `recordSale()`.
2. **`frontend/src/api/salesApi.js`** — `POST /api/sales` with items, buyer document and voucher type.
3. **`SaleController.record`** (web adapter) — Spring parses and validates the JSON, then calls the use
   case.
4. **`RecordSaleUseCase`** (business) rejects a factura without a RUC, then works in two phases:
   - **Phase 1, one database transaction** (`TransactionRunner`):
     - checks every product exists, is active and has enough stock;
     - builds and saves the `Sale` (which computes its own total, so it can never disagree with its items);
     - decrements stock with a **conditional update** (`... WHERE stock_quantity >= :quantity`). If a
       concurrent sale took the stock in the meantime, the update changes nothing, the use case throws
       `InsufficientStockException` and the whole transaction rolls back. `stock_quantity` is mapped
       `updatable = false` in `ProductJpaEntity`, so saving a product loaded earlier (an edit, a
       deactivation) never writes back a stale stock over this decrement;
     - registers the `Voucher` as `PENDING`.
     All of it commits together or not at all.
   - **Phase 2, after the commit**: `IssueVoucherForSaleUseCase.issue` calls NubeFacT through
     `InvoicingProvider` and saves the voucher as `ACCEPTED`, or leaves it `PENDING` with the reason.
     It runs outside the transaction on purpose: a transaction must never stay open waiting on the
     network. Its failures never undo the sale.
5. **`SaleRepositoryAdapter`** — converts `Sale` to `SaleJpaEntity`; JPA writes it to PostgreSQL.
6. **`RetryPendingVouchersJob`** — every 5 minutes, retries every `PENDING` voucher not updated in the last
   minute. The grace period keeps it from racing phase 2 (which would issue a voucher twice) while still
   recovering a voucher whose phase 2 never ran (the server died right after the commit).

**Why not a saga?** A saga coordinates steps across services that each own a database, undoing earlier
steps with compensations when a later one fails. Here there is one database, so the local steps fit in one
transaction; and the only remote step, NubeFacT, must not trigger compensation: the customer already left
with the product, so a sale is never undone because SUNAT is down. The `PENDING` voucher row written in the
same transaction acts as a **transactional outbox**: a durable record of work still owed to the outside
world, drained by the retry job.

Other flows follow the same shape: page → API client → controller → use case → ports → adapters.
Voiding a sale (`POST /api/sales/{id}/void`) asks NubeFacT to void the voucher, marks it `VOIDED` and the
sale `CANCELLED`.

### Recording a purchase

`POST /api/purchases` → **`PurchaseController.record`** → **`RecordPurchaseUseCase`**, all inside one
`TransactionRunner` transaction (there is no remote call, so there is no second phase):

1. checks the supplier exists and is active, and every product exists and is active;
2. builds the `Purchase` (which validates the invoice number, the date, the lines and computes its total),
   rejects a supplier invoice number already registered, and saves it. The unique
   `(supplier_id, invoice_number)` constraint is the race-safe guard behind that check;
3. per line, calls `ProductRepositoryPort.incrementStockAndAverageCost`: **one conditional `UPDATE`**
   (`ProductJpaRepository`) that adds the quantity to the stock and sets the cost price to the weighted
   average, both computed from the row's current values (`... WHERE id = :id AND active = true`). A
   concurrent sale's decrement and this increment queue on the row lock and each applies on top of the
   other, so neither is lost. If the product was deactivated meanwhile, the update changes nothing, the use
   case throws and the whole purchase rolls back. `stock_quantity` and `cost_price` are both mapped
   `updatable = false` in `ProductJpaEntity` (a JPQL bulk update ignores that flag), so saving a product
   loaded earlier (an edit, a deactivation) never writes back a stale stock or cost over this update.
   The saved entity would still carry those stale values in memory, so `ProductRepositoryAdapter.save`
   re-reads stock and cost from the row for an existing product; the edit response shows the stored ones.

`PurchaseConcurrencyPostgresTest` proves the no-lost-update and rollback behavior, the half-up rounding of
the average, and the constraint mappings (only `uq_purchases_supplier_invoice` becomes a duplicate invoice,
only `uq_suppliers_ruc` a duplicate RUC) on a real PostgreSQL. `RecordPurchaseUseCase` takes "now" from an
injected `java.time.Clock` (system UTC in `UseCaseConfiguration`) and reads "today" in `America/Lima`.

On the frontend, **`PurchasesPage.jsx`** offers only active suppliers and products, submits through
`recordPurchase()` (`frontend/src/api/purchasingApi.js`), and on success invalidates both the `purchases`
and the `products` queries, so the purchase list and the products page (stock) refresh together. A backend
error (for example the `409` for a duplicate supplier invoice) is shown as-is and the form keeps its
values. `frontend/e2e/purchase-flow.spec.js` drives this whole path through a real browser: supplier
creation, a recorded purchase in the list, the raised stock and the weighted-average cost, and the
duplicate-invoice rejection.

## 6. Known risks and gaps

Keep this list honest: add a risk when you find it, remove it in the change that fixes it.

- **Manual retry can race the retry job.** `POST /api/sales/{id}/retry-voucher` and `RetryPendingVouchersJob`
  do not coordinate. If both hit the same settled `PENDING` voucher at the same moment, it could be issued
  twice. Unlikely with one cashier, but there is no lock.
- **No authentication.** Anyone who can reach the API can record and void sales.
- **Stock only moves through sales and purchases.** Purchases bring stock in (and recompute cost price), but
  there is no manual adjustment (shrinkage, physical counts) and no stock-movement ledger, so the current
  stock cannot be explained movement by movement. Cancelling a sale does not restore its stock
  (`CancelSaleUseCase`). Editing a product cannot change its stock or its cost price; a cost entered
  wrong at creation can only be corrected by later purchases.
- **Purchases cannot be voided or corrected.** A wrong purchase can only be offset by hand.
- **Cost price is invisible in the UI.** It is set at product creation and then only by purchases, but no
  page shows it (`ProductList.jsx` has no cost column), so the weighted average can only be checked through
  `GET /api/products`.
- **Backend error messages reach the UI in English.** The API clients show the backend `message` verbatim
  (for example "A purchase with invoice number ... already exists for supplier <id>"), inside a Spanish UI,
  and with internal ids instead of names.
- **Not deployed.** CI tests but does not deploy. CORS only allows `http://localhost:5173`.
- **Buyer name is a placeholder** on vouchers (`"Cliente <document>"`). See `odd/tasks/buyer-name-lookup.md`.
