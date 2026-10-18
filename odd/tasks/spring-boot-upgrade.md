# Feature: Spring Boot upgrade path

Status: **in progress**. S1+S2 delivered in PR #54. S3 done on branch `chore/spring-boot-4`, not pushed.

## Objective

Stay current on Spring Boot without Dependabot opening a broken major-upgrade PR every week, and prepare
the 3 -> 4 migration as its own piece of work.

## Problem

Dependabot PR #37 (Spring Boot 3.3.4 -> 4.1.1) fails before compiling: the Spring Boot 4 BOM no longer
manages `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`
(`'dependencies.dependency.version' ... is missing`, run 39161672912). Later failures are unknown.
The Spring Boot 4.0 migration guide also lists `@MockBean` removal (use `@MockitoBean`), modular starters
(e.g. Flyway needs `spring-boot-starter-flyway`), Jackson 3 and `@SpringBootTest` no longer providing MockMvc.

## Tasks

- [x] S1 Dependabot: ignore Spring Boot major updates; keep minor/patch.
- [x] S2 Upgrade Spring Boot 3.3.4 -> 3.5.16 (latest 3.5.x on Maven Central); backend tests + E2E green.
- [x] S3 Migrate to Spring Boot 4.1.1:
  - [x] S3a Build + code compile and `mvn clean test` green, in one commit (a BOM bump alone cannot compile):
        modular starters, Testcontainers 2 artifacts, `@MockBean` -> `@MockitoBean`, test-slice imports,
        `TestRestTemplate`, Jackson 3 (`WholeNumberDeserializer`, `PurchaseItemRequest`, test `ObjectMapper`).
  - [x] S3b Runtime proof: full E2E, Flyway startup, actuator health, Hibernate 7 bulk updates on Postgres.
  - [x] S3c Docs: regenerate `architecture.md`, hand-written stack lines, stale comments.

## Checks

- `mvn clean test` and `npm run test:e2e` (in `frontend/`) pass; `architecture-doc.mjs --check` passes.

## S3 impact map (explorer, 2026-10-18; Maven Central checked)

- Boot 4.1.1 BOM manages: Testcontainers 2.0.5 (artifacts renamed `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`), JUnit Jupiter 6.0.3, Jackson 3.1.5, Hibernate 7.4.5.Final, Flyway 12.4.0.
- Starters: `spring-boot-starter-web` -> `-webmvc`; Flyway needs `spring-boot-starter-flyway`; test companions
  `-webmvc-test`, `-data-jpa-test`; `spring-boot-resttestclient` exists (TestRestTemplate, 2 tests).
- Code: `@MockBean` 22 uses in 7 test files; `@WebMvcTest` 5, `@DataJpaTest` 8, `@SpringBootTest` 3 (no
  MockMvc there, so `@AutoConfigureMockMvc` not needed). Jackson databind in main: `WholeNumberDeserializer`
  (`throws IOException` override), `PurchaseItemRequest`; 4 tests autowire `ObjectMapper`.
- Risk: `spring.jackson.deserialization.accept-float-as-int` (application.yml) under Jackson 3; Hibernate 7
  on JPQL bulk updates with `CASE`/`round`; `api.version=1.44` docker-java workaround may become removable.
- Forecast: ~250 authored lines, one PR.

## Progress
- S1 (route: inline, 1 file): `ignore` for `org.springframework.boot:*` semver-major in the Maven block. YAML parses.
  Effect visible on the next Dependabot run.
- S2 (route: inline, 1 file): `spring-boot.version` 3.3.4 -> 3.5.16, no code changes needed. With Docker:
  `mvn clean test` exit 0; `npm run test:e2e` 3 passed; `architecture-doc.mjs --check` up to date.
  (A first run without Docker failed only the 2 Testcontainers tests: environment, not the upgrade.)

- S3a (route: delegated writer, 23 files +140/-127): Boot 4.1.1; starters webmvc, flyway, webmvc-test,
  data-jpa-test, resttestclient + restclient (test; `TestRestTemplate` needs `RestTemplateBuilder`);
  Testcontainers 2 artifacts and non-generic `org.testcontainers.postgresql.PostgreSQLContainer`; 22
  `@MockBean` -> `@MockitoBean`; Boot 4 test-slice packages; `@AutoConfigureTestRestTemplate` on the 2
  RANDOM_PORT tests; Jackson 3 in `WholeNumberDeserializer` (`throws JacksonException`) and
  `PurchaseItemRequest`. Docker-java `api.version=1.44` workaround removed (not needed with TC 2.0.5).
  RED: POM unresolvable, then compile errors, then 9 TestRestTemplate errors. GREEN: `mvn clean test`
  application 197/0/0, infrastructure 151/0/0 (incl. Testcontainers tests); no assertions changed.
  Parent spot check: no `@MockBean`/Jackson 2 databind/`api.version` left; `-DskipTests package` exit 0.
  Open for S3b: NubeFacT RestClient JSON under Jackson 3, Hibernate 7 bulk updates and Flyway 12 on Postgres.
- S3b (route: inline, checks only): `npm run test:e2e` at `ea38b2a`: 3 passed. Verified it ran against a fresh
  Boot 4.1.1 backend: `start-backend.sh` uses `mvn spring-boot:run` from source with
  `reuseExistingServer: false`, and nothing was listening on 8080 beforehand; Flyway 12 migrated a clean
  Postgres; Hibernate 7 and Jackson 3 exercised end to end.
- S3c (route: inline, mechanical): `architecture.md` regenerated + hand-written stack lines (webmvc, flyway
  starter, JUnit Jupiter); removed the stale `api.version` CI comment; `start-backend.sh` and pom/test
  comments no longer name old versions. `bash -n`, YAML parse, `architecture-doc.mjs --check` pass.
- Review of `origin/main..f2c7191` (high, granted, 4 lenses approved, acknowledged). Two lenses warned that
  NubeFacT request JSON under Jackson 3 was unproved: the adapter test never asserted request bodies, and the
  backend E2E mocks `InvoicingProvider`. The earlier note that S3b proved Jackson 3 end to end was overstated
  for this outbound path.
- S3d (route: delegated writer, failed mid-run; parent verified): 6 goldens in
  `infrastructure/src/test/resources/nubefact/` (boleta, boleta without buyer document, factura, credit note,
  status, void), asserted with `JsonCompareMode.STRICT` in `NubeFactInvoicingAdapterTest`. Parent proof: the
  same test file and goldens copied onto an `origin/main` worktree (Jackson 2) exit 0, and on this branch
  (Jackson 3) 14/14 pass per the surefire report; mutating `moneda` in `factura.json` fails the test (then
  restored). Gap: the test count of the main run was not captured (quiet Maven, worktree removed). Also adds
  the pom comment for the test-scoped `spring-boot-restclient` (review R2-001).
- Delivery strategy: `single-pr` (user, 2026-10-18). The branch is 457 authored lines (~150 of them golden
  JSON fixtures). The migration commit cannot be split (a BOM bump alone does not compile), and the goldens are
  its proof, so splitting them out would merge the migration before its evidence.
- Sandbox E2E (2026-10-18, user-provided `.env.nubefact.local`): 3 passed. Playwright drops backend stdout by
  default, so a second run used `DEBUG=pw:webserver`: credentials loaded, and the backend reached the NubeFacT
  sandbox, which answered two 400s. Neither is caused by the upgrade (production changes on this branch are only
  the Jackson imports in `purchasing`; goldens prove identical request JSON):
  - code 23 `Este documento ya existe`: fresh E2E database restarts correlatives, sandbox kept earlier numbers.
  - code 21 `descripcion no puede estar en blanco`: pre-existing bug, a product without `description` yields a
    blank voucher line. Recorded in `docs/architecture.md` known risks; fix as its own feature.
  - New non-blocking warning: Hibernate Validator HV000271 (`@Valid` on a `List` container is deprecated).
- Accepted voucher on this branch (2026-10-18): a controlled run (throwaway Postgres on 5434,
  `start-backend.sh` with the sandbox credentials, product WITH a `description`, `voucher_correlativo_sequences`
  bumped by SQL) issued FACTURA **FFF1-900000**: `voucherStatus=ACCEPTED`, PDF, XML, CDR and hash present.
  BOLETAs BBB1-206 and BBB1-207 were registered by NubeFacT (2xx, no error) but returned
  `aceptada_por_sunat=false` with an empty SUNAT description, so they stay `PENDING`; not confirmed whether
  that is the expected boleta flow (daily summary) or something else. BBB1 enforces a 200-correlative window
  above the last registered number; FFF1 accepted 900000. Side effect: the sandbox FFF1 series now ends at
  900000.

## Next step

Sandbox E2E run, then push + PR (user); CI must pass the 5 required checks. Then mark the feature done.
