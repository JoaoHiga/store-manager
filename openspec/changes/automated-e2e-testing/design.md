# Design: Automated E2E Testing + Pre-Archive Gate

## Technical Approach

Two independent, additive test layers on top of what exists today. Existing `SaleControllerTest` is a `@WebMvcTest` slice test with Mockito-mocked use-cases — real, but it never exercises Flyway/Postgres or the real frontend `fetch` call, which is exactly where both prior bugs lived. Phase A adds a full-Spring-context integration layer against a real Postgres via Testcontainers. Phase B adds a full-stack layer driving the real browser against both real servers. Both wire into one new CI workflow. A new OpenSpec config rule turns "E2E must pass" into a structural precondition for `sdd-archive`, not a convention.

## Architecture Decisions

### Decision: Phase A test scope — new class, not extending `SaleControllerTest`

**Choice**: New `SaleFlowE2ETest` in a new `com.storemanager.e2e` test package (crosses `sales`+`invoicing`, doesn't belong to either module's own package).
**Alternatives considered**: Extending `SaleControllerTest` in place.
**Rationale**: `SaleControllerTest` is a `@WebMvcTest` slice (fast, mocked) and stays that way for what it's good at — controller-layer contract tests. Mixing a full `@SpringBootTest` + Testcontainers class into the same file changes its performance profile and purpose. Keeping them separate lets Phase A run as its own CI job/Maven profile if it ever needs to (Testcontainers tests are slower).

### Decision: Testcontainers wiring — `@ServiceConnection`, not manual `@DynamicPropertySource`

**Choice**: `@Testcontainers` + `@Container static PostgreSQLContainer<?> postgres` annotated `@ServiceConnection`, per `research.md` C1.
**Alternatives considered**: Manual `@DynamicPropertySource` registering `spring.datasource.url/username/password`.
**Rationale**: `@ServiceConnection` (Spring Boot 3.1+, confirmed available — project already targets Spring Boot 3.x) is zero-config and auto-wires Flyway against the container with no property boilerplate; manual wiring is the older, more error-prone pattern this project has no reason to reach for.

### Decision: Phase B server orchestration — Playwright's own multi-`webServer` array

**Choice**: `playwright.config.ts` `webServer: [{ command: 'npm run dev' ... }, { command: 'mvn -pl infrastructure spring-boot:run' ... }]`, per `research.md` C2.
**Alternatives considered**: A separate `docker-compose.yml` orchestrating both; a shell script wrapper.
**Rationale**: Native Playwright support needs no extra tooling and keeps `reuseExistingServer: !process.env.CI` so local dev can reuse an already-running `npm run dev` / already-running backend, while CI always boots fresh.

### Decision: NubeFacT in Phase B — real sandbox (per user's explicit choice), backend picks the profile

**Choice**: A `sandbox` Spring profile activates the existing `NubeFactInvoicingAdapter` pointed at NubeFacT's sandbox base URL with credentials from a `NUBEFACT_SANDBOX_TOKEN` GitHub Actions secret (and a local `.env`-style file, gitignored, for developers running Phase B locally).
**Alternatives considered**: Stub/test-double adapter (recommended in exploration, rejected by user in favor of realism).
**Rationale**: User explicitly chose real-sandbox realism over stub determinism. Design accepts this tradeoff and isolates the blast radius: only Phase B (not Phase A, not unit tests) touches the network, and only through a profile that's never active outside CI/local E2E runs.

## Data Flow

    Playwright browser ──→ Vite dev server (React) ──→ Spring Boot (sandbox profile)
                                                              │
                                                    ┌─────────┴─────────┐
                                              Testcontainers Postgres   NubeFacT sandbox (real HTTP)
                                                    (Phase A only)         (Phase B only)

## File Changes

| File | Action | Description |
|------|--------|--------------|
| `infrastructure/pom.xml` | Modify | Add `org.testcontainers:postgresql` + `org.testcontainers:junit-jupiter`, test scope |
| `infrastructure/src/test/java/com/storemanager/e2e/SaleFlowE2ETest.java` | Create | Phase A: record BOLETA/FACTURA, list, void, retry-voucher against real Postgres |
| `infrastructure/src/main/java/com/storemanager/sales/infrastructure/web/SaleResponse.java` | Modify | Discovered gap (user-confirmed fix, RED-first): add `voucherType` field, populated from `voucher.getType().name()` — `GET /sales` currently exposes `voucherStatus` but never the actual BOLETA/FACTURA type |
| `infrastructure/src/main/resources/application-sandbox.yaml` | Create | NubeFacT sandbox profile config (base URL; token from env) |
| `frontend/package.json` | Modify | Add `@playwright/test` devDependency + `test:e2e` script |
| `frontend/playwright.config.ts` | Create | Multi-`webServer` config (Vite + Spring Boot) |
| `frontend/e2e/sale-voucher-flow.spec.ts` | Create | Phase B: record sale → pick voucher type → confirm list |
| `.github/workflows/e2e.yml` | Create | Runs Phase A (`mvn -pl infrastructure test -Dtest=SaleFlowE2ETest`) and Phase B (`npm run test:e2e`) on push/PR |
| `openspec/config.yaml` | Modify | Add `rules.archive` entry requiring E2E pass |

## Interfaces / Contracts

No new production interfaces — both phases test existing `SaleController` endpoints (`POST /sales`, `GET /sales`, `POST /sales/{id}/void`, `POST /sales/{id}/retry-voucher`) and the existing `RecordSalePage`/`SalesListPage` UI. No contract changes.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|---------------|----------|
| Phase A (integration) | Sale record + voucher-type persistence + list correctness against real schema | `@SpringBootTest` + Testcontainers Postgres, `TestRestTemplate` |
| Phase B (E2E) | Same path through the real UI, real fetch calls, real backend response | Playwright, real browser, real NubeFacT sandbox |
| CI | Both suites gate every push/PR | GitHub Actions, `ubuntu-latest` (Docker preinstalled) |

## Threat Matrix

N/A — no routing, shell-command execution over untrusted input, subprocess spawning from external/PR data, VCS/PR automation, executable-file classification, or process-integration boundary. `webServer` commands and the CI workflow are fixed, developer-authored strings, not built from user/PR-controlled input.

## Migration / Rollout

No migration required — purely additive test/CI infrastructure. `openspec/config.yaml`'s new archive-gate rule applies going forward, starting with this change's own archive.

## Open Questions

- [ ] Exact `testcontainers-java` version to pin against the Docker 29.1 CI regression (`research.md` gap) — resolve at `sdd-tasks`/`sdd-apply` time by checking the latest release notes then, since this is a fast-moving compatibility issue.
