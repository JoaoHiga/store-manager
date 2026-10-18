# store-manager

Inventory management web app for a family cellphone store, with Peru electronic invoicing
(SUNAT) through NubeFacT. How the system is built: [`docs/architecture.md`](docs/architecture.md).

## Running locally

Prerequisites: Java 21, Maven, Node.js, and Docker.

1. Start the development database (PostgreSQL 16 on port 5432):

   ```bash
   docker compose up -d --wait
   ```

   Data persists in the `store-manager-dev-data` volume. `docker compose down` stops it and keeps
   the data; `docker compose down -v` also deletes the data.

2. Start the backend (Spring Boot on port 8080). Flyway applies the migrations on startup.

   ```bash
   mvn -pl application -am install -DskipTests -q
   mvn -f infrastructure/pom.xml spring-boot:run
   ```

   The first command installs the `application` module that `infrastructure` depends on. Run it
   again after changing code in `application`.

3. Start the frontend (Vite on port 5173):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

   Open http://localhost:5173. The frontend calls the backend at `http://localhost:8080`; set
   `VITE_API_BASE_URL` to point it elsewhere.

### Configuration

- Database settings come from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`
  (see `infrastructure/src/main/resources/application.yml`). The defaults match `docker-compose.yml`.
- NubeFacT credentials are optional locally. Without them, vouchers stay `PENDING`. To issue against
  the sandbox, put `NUBEFACT_BASE_URL` and `NUBEFACT_API_TOKEN` in `.env.nubefact.local` (gitignored,
  never committed) and export it before starting the backend:

  ```bash
  set -a; source .env.nubefact.local; set +a
  ```

## Tests

| Scope | Command |
|---|---|
| Backend tests | `mvn clean test` (repo root; Docker needed for Testcontainers) |
| Frontend unit tests | `npm test` (in `frontend/`) |
| Frontend build | `npm run build` (in `frontend/`) |
| Full-stack E2E | `npm run test:e2e` (in `frontend/`; see [`frontend/e2e/README.md`](frontend/e2e/README.md)) |

The E2E suite starts its own throwaway database on port 5433, so it never touches the development
data.
