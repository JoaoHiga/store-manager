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
| Technology | Version | Module | Scope |
|---|---|---|---|
| Java | 21 | root `pom.xml` | — |
| Spring Boot (BOM) | 3.3.4 | root `pom.xml` | — |
| junit-jupiter | managed | `application` | test |
| assertj-core | managed | `application` | test |
| spring-boot-starter-web | managed | `infrastructure` | compile |
| spring-boot-starter-data-jpa | managed | `infrastructure` | compile |
| spring-boot-starter-actuator | managed | `infrastructure` | compile |
| spring-boot-starter-validation | managed | `infrastructure` | compile |
| postgresql | managed | `infrastructure` | runtime |
| flyway-core | managed | `infrastructure` | compile |
| flyway-database-postgresql | managed | `infrastructure` | compile |
| spring-boot-starter-test | managed | `infrastructure` | test |
| h2 | managed | `infrastructure` | test |
| spring-boot-testcontainers | managed | `infrastructure` | test |
| postgresql | managed | `infrastructure` | test |
| junit-jupiter | managed | `infrastructure` | test |
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
| Package | Version | Kind |
|---|---|---|
| @tanstack/react-query | ^5.102.8 | runtime |
| react | ^19.2.8 | runtime |
| react-dom | ^19.2.8 | runtime |
| @playwright/test | ^1.63.0 | dev/test |
| @testing-library/jest-dom | ^7.0.1 | dev/test |
| @testing-library/react | ^16.3.3 | dev/test |
| @testing-library/user-event | ^14.6.7 | dev/test |
| @types/react | ^19.2.18 | dev/test |
| @types/react-dom | ^19.2.7 | dev/test |
| @vitejs/plugin-react | ^6.1.1 | dev/test |
| jsdom | ^30.0.1 | dev/test |
| oxlint | ^1.81.0 | dev/test |
| vite | ^8.3.0 | dev/test |
| vitest | ^5.0.0 | dev/test |
<!-- END GENERATED: tech-frontend -->

### Outside the code

- **NubeFacT** — electronic invoicing provider (OSE). Credentials come from environment variables
  (`NUBEFACT_BASE_URL`, `NUBEFACT_API_TOKEN`); locally from `.env.nubefact.local`, never committed.
- **GitHub Actions** — `.github/workflows/e2e.yml` runs backend tests, frontend tests, full-stack E2E and
  the architecture-doc check on every pull request and on every push to `main`. It does **not** deploy.

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

The backend is split into three business domains. Each one has the same internal layout:

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
- **shared** — not a business domain: cross-cutting ports every domain may use, such as
  `TransactionRunner` (run a unit of work atomically).

<!-- BEGIN GENERATED: backend-domains -->
| Domain | Domain model (`domain/`) | Ports (`domain/port/`) | Use cases (`usecase/`) | Infrastructure (files) |
|---|---|---|---|---|
| `catalog` | `Category`, `Product` | `CategoryRepositoryPort`, `ProductRepositoryPort` | `CreateCategoryUseCase`, `CreateProductUseCase`, `DeactivateProductUseCase`, `ListCategoriesUseCase`, `ListProductsUseCase`, `ReactivateProductUseCase`, `UpdateProductUseCase` | `config/ (2)`, `persistence/ (6)`, `web/ (8)` |
| `invoicing` | `Voucher`, `VoucherStatus`, `VoucherType` | `InvoicingProvider`, `VoucherRepositoryPort` | `IssueVoucherForSaleUseCase`, `RetryPendingVoucherUseCase`, `VoidSaleUseCase` | `config/ (1)`, `nubefact/ (9)`, `persistence/ (5)`, `scheduling/ (1)` |
| `sales` | `BuyerDocumentValidator`, `Sale`, `SaleItem`, `SaleStatus` | `SaleRepositoryPort` | `CancelSaleUseCase`, `GetSaleUseCase`, `ListSalesUseCase`, `RecordSaleUseCase`, `RetrySaleVoucherUseCase` | `persistence/ (4)`, `web/ (7)` |
| `shared` | — | `TransactionRunner` | — | `transaction/ (1)` |
<!-- END GENERATED: backend-domains -->

### Ports and the adapters that implement them

<!-- BEGIN GENERATED: ports-adapters -->
| Port (interface) | Declared in | Implemented by (adapter) |
|---|---|---|
| `CategoryRepositoryPort` | `application/…/catalog/domain/port/CategoryRepositoryPort.java` | `infrastructure/…/catalog/infrastructure/persistence/CategoryRepositoryAdapter.java` |
| `ProductRepositoryPort` | `application/…/catalog/domain/port/ProductRepositoryPort.java` | `infrastructure/…/catalog/infrastructure/persistence/ProductRepositoryAdapter.java` |
| `InvoicingProvider` | `application/…/invoicing/domain/port/InvoicingProvider.java` | `infrastructure/…/invoicing/infrastructure/nubefact/NubeFactInvoicingAdapter.java` |
| `VoucherRepositoryPort` | `application/…/invoicing/domain/port/VoucherRepositoryPort.java` | `infrastructure/…/invoicing/infrastructure/persistence/VoucherRepositoryAdapter.java` |
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
| V1 | init catalog |
| V2 | add cost price check |
| V3 | create sales |
| V4 | create vouchers |
| V5 | create voucher correlativo sequences |
| V6 | make buyer document nullable |
| V7 | widen voucher rejection reason |
| V8 | reseed voucher correlativo series |
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
| Pages | `frontend/src/pages/` | `CategoriesPage.jsx`, `ProductsPage.jsx`, `RecordSalePage.jsx`, `SalesListPage.jsx` |
| Organisms | `frontend/src/components/organisms/` | `CategoryList.jsx`, `ProductForm.jsx`, `ProductList.jsx` |
| Molecules | `frontend/src/components/molecules/` | `FormField.jsx`, `PriceTag.jsx` |
| Atoms | `frontend/src/components/atoms/` | `Badge.jsx`, `Button.jsx`, `Input.jsx` |
| API clients | `frontend/src/api/` | `catalogApi.js`, `salesApi.js` |
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
       `InsufficientStockException` and the whole transaction rolls back;
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

## 6. Known risks and gaps

Keep this list honest: add a risk when you find it, remove it in the change that fixes it.

- **Editing a product overwrites its stock.** `PUT /api/products/{id}` writes the stock value from the
  form as an absolute number. A sale recorded while the form is open is silently undone on save. Sales
  themselves are safe (conditional update).
- **Manual retry can race the retry job.** `POST /api/sales/{id}/retry-voucher` and `RetryPendingVouchersJob`
  do not coordinate. If both hit the same settled `PENDING` voucher at the same moment, it could be issued
  twice. Unlikely with one cashier, but there is no lock.
- **No authentication.** Anyone who can reach the API can record and void sales.
- **No stock entry.** Stock only goes down (sales); there is no purchase, restock or adjustment flow
  beyond editing the product.
- **Not deployed.** CI tests but does not deploy. CORS only allows `http://localhost:5173`.
- **Buyer name is a placeholder** on vouchers (`"Cliente <document>"`). See `odd/tasks/buyer-name-lookup.md`.
