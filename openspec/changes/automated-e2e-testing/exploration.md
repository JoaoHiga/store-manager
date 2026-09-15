## Exploration: Automated E2E Testing + Pre-Archive Gate

### Current State

**Backend testing**: JUnit 5 via `spring-boot-starter-test`, with `com.h2database:h2` as the only test-scope database dependency in `infrastructure/pom.xml`. There is no Testcontainers dependency anywhere in the repo. This means backend integration tests currently run against H2 in-memory, not the real PostgreSQL + Flyway (`flyway-database-postgresql`) stack that runs in production — a gap on its own, independent of E2E.

**Frontend testing**: Vitest 5 + `@testing-library/react` + `jsdom`. No MSW, no Playwright, no Cypress in `package.json`. Component tests (e.g. `SalesListPage.test.jsx`) render components in jsdom and presumably mock the API layer directly — which is exactly why the `salesApi.recordSale` bug (missing `voucherType` in the request body) slipped through: the mock already "knew" the right shape, so the test never exercised the real fetch call against a real backend contract.

**CI**: No `.github/workflows/` directory exists. All testing today is run manually/locally. This matters for both phases — any automated E2E suite will initially only run when someone remembers to run it locally, unless CI is added as part of this change or a fast follow.

**Docker**: `docker` CLI resolves (Docker Desktop for Windows, WSL-integrated), but `docker info` failed — Docker Desktop is not currently running in this environment. Testcontainers (Phase A) needs a running Docker daemon; this is a local dev-environment dependency, not a blocker, but must be documented as a prerequisite.

**The real flow to cover** (from `SaleController`): `POST /sales` (record sale, body includes `voucherType`), `GET /sales` (list), `GET /sales/{id}`, `POST /sales/{id}/retry-voucher`, `POST /sales/{id}/void`. Frontend: `RecordSalePage.jsx` → `SalesListPage.jsx`. The two real bugs found so far both sit exactly on this path (recording a sale, seeing it correctly voucher-typed in the list).

### Affected Areas

- `infrastructure/pom.xml` — needs Testcontainers (`postgresql` module) test-scope dependency for Phase A.
- `infrastructure/src/test/java/...` — new integration test package driving `SaleController` end-to-end against a real Postgres container + Flyway migrations (Phase A).
- `frontend/package.json` — needs Playwright (or equivalent) devDependency for Phase B.
- New top-level `e2e/` (or `frontend/e2e/`) directory for Playwright specs (Phase B) — needs both the Vite dev server and the Spring Boot app running together.
- `openspec/changes/{future-changes}/tasks.md` convention — needs a standard "E2E gate" task pattern so this isn't a one-off.
- Missing entirely today: `.github/workflows/` — out of scope to fully build CI here, but worth flagging since a local-only E2E suite only prevents the next miss if someone actually runs it.

### Approaches

1. **Phase A — Backend integration E2E via Testcontainers** — Spin up a real Postgres container per test class, run Flyway migrations against it, drive `SaleController` through `MockMvc` or a real embedded HTTP client (`TestRestTemplate`) covering: record sale with BOLETA, record sale with FACTURA (RUC), list reflects correct voucher type, void, retry-voucher.
   - Pros: Fast (seconds), no browser, catches real DB/Flyway/SQL issues H2 masks (e.g. Postgres-specific constraints), runs in any CI without a display.
   - Cons: Would NOT have caught either of the two real bugs — both were frontend↔backend contract bugs, invisible from the backend's own perspective (the backend received exactly what a correct frontend would send).
   - Effort: Low-Medium (mostly wiring Testcontainers + writing scenario tests against existing endpoints).

2. **Phase B — Full-stack E2E via Playwright** — Launch the real Vite dev server + real Spring Boot app (against Testcontainers Postgres or a dedicated test DB), drive the actual browser through: navigate to record-sale page, fill form, pick voucher type, submit, assert the sale appears correctly voucher-typed in the list.
   - Pros: The only level that actually exercises the real `fetch` call frontend makes and the real response the backend returns — this is what would have caught both real bugs.
   - Cons: Slower, needs both processes running simultaneously (more orchestration — a `docker-compose` or npm script that boots both), flakier by nature (network timing, real browser), no CI yet to run it automatically.
   - Effort: Medium (Playwright setup + a boot-both-servers script + writing the happy-path spec).

3. **Skip backend-only, go straight to Playwright only** — considered and rejected by the user; explicitly wants both, phased.

### Recommendation

Proceed with the user's own decision: Phase A (backend Testcontainers) first for fast regression coverage of everything already built, then Phase B (Playwright) specifically targeting the sale-record → voucher-type → list-display path where the two real bugs lived. Scope this SDD change to define both phases' design and tasks now, but the tasks.md delivery plan should let Phase A ship and merge independently of Phase B (they're not tightly coupled — Phase B doesn't depend on Phase A's test code, only on the app being runnable).

For the "gate before archive" requirement: express it as a **required task in every future change's `tasks.md`** ("Run automated E2E suite (Phase A + Phase B once it exists) and confirm pass before requesting archive") plus a check `sdd-verify`/`sdd-archive` inline validation performs — not a vague reminder. Concretely: add a project-wide rule to `openspec/config.yaml`'s `rules.archive` section (e.g. `- Automated E2E suite must pass before archive; do not archive with E2E marked pending`) so it's structurally enforced for every future change, not just remembered ad hoc.

### Risks

- Docker Desktop must be running for Phase A/B locally — if forgotten, tests silently can't run (not a test failure, an environment gap). Design should call this out explicitly in a README/CONTRIBUTING note.
- No CI exists — an automated suite that only runs "when you remember" is a weaker guarantee than the user may expect from the word "automated." Worth an explicit scope decision: is adding a minimal CI workflow (just running the new suites on push) in scope for this change, or a deliberately separate follow-up?
- Phase B (Playwright) against a real NubeFacT integration risks hitting the real SUNAT/NubeFacT sandbox API on every E2E run unless the invoicing adapter is stubbed/mocked for E2E specifically — needs a design decision (test double for NubeFacT vs. a sandbox account).
- Scope creep risk: "automated E2E" could balloon to cover every flow (inventory, categories, all voucher types, void, retry). Recommend scoping the first version tightly to the sale-record → voucher-type → list path that's already bitten twice, with room to expand later.

### Ready for Proposal

Yes — the two real bugs give a concrete, bounded scope (record sale + voucher type + list display), the phased approach is already decided, and the codebase investigation surfaced no blocking unknowns, only design decisions (NubeFacT stubbing for Phase B, whether CI is in scope) that belong in `sdd-propose`/`sdd-design`, not further exploration.
