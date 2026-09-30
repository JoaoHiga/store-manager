# Apply Progress: Automated E2E Testing + Pre-Archive Gate

## PR1 — Phase A (Backend Integration E2E via Testcontainers): DONE

**Mode**: Strict TDD

### Completed Tasks
- [x] 1.1 Testcontainers deps added to `infrastructure/pom.xml`; `SaleFlowE2ETest` skeleton (RED confirmed: class didn't exist, `mvn test -Dtest=SaleFlowE2ETest` failed with "No tests matching pattern").
- [x] 1.2 `@Testcontainers`/`@Container`/`@ServiceConnection` `PostgreSQLContainer` wiring — GREEN, Flyway runs all 6 real migrations against real Postgres.
- [x] 1.3 FACTURA+RUC succeeds / FACTURA without RUC → 400 — GREEN, characterizes existing `RecordSaleUseCase` behavior at the new integration layer.
- [x] 1.4a `voucherType` added to `SaleResponse` — RED confirmed (`expected: "BOLETA" but was: null`), then GREEN.
- [x] 1.4b List reflects each sale's own voucher type — GREEN. Also **refutes** `SalesListPage.jsx`'s comment claiming `voucherStatus` is always null in the list — confirmed stale, not a live bug (see Deviations).
- [x] 1.5 Void (ACCEPTED → CANCELLED) and retry-voucher (PENDING → ACCEPTED) scenarios — GREEN, characterizes existing `VoidSaleUseCase`/`RetryPendingVoucherUseCase` behavior at the new layer.
- [x] 1.6 Refactor: extracted `recordSale(...)` helper, removed duplicated response-parsing across 4 test methods — still GREEN after refactor.

### Files Changed
| File | Action | What Was Done |
|------|--------|----------------|
| `infrastructure/pom.xml` | Modified | Added `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` (test scope) |
| `infrastructure/src/test/java/com/storemanager/e2e/SaleFlowE2ETest.java` | Created | 6 tests: record BOLETA/FACTURA, reject FACTURA without RUC, list reflects voucher type, void, retry-voucher |
| `infrastructure/src/main/java/com/storemanager/sales/infrastructure/web/SaleResponse.java` | Modified | Added `voucherType` field, populated from `voucher.getType().name()` |

### TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 1.1-1.2 | `SaleFlowE2ETest.java` | Integration (Testcontainers) | N/A (new file) | ✅ Written (class-not-found) | ✅ Passed | ➖ Single scenario | ✅ Clean |
| 1.3 | `SaleFlowE2ETest.java` | Integration | ✅ existing suite unaffected | ✅ Written | ✅ Passed | ✅ 2 cases (RUC accepted, non-RUC rejected) | ✅ Clean |
| 1.4a-1.4b | `SaleFlowE2ETest.java` + `SaleResponse.java` | Integration | ✅ `SaleControllerTest` 24/24 still passing after the field addition | ✅ Written (`expected: "BOLETA" but was: null`) | ✅ Passed | ✅ 2 cases (BOLETA, FACTURA) | ✅ Clean |
| 1.5 | `SaleFlowE2ETest.java` | Integration | ✅ existing suite unaffected | ✅ Written | ✅ Passed | ✅ 2 cases (void, retry) | ✅ Clean |

### Test Summary
- **Total tests written**: 6 (`SaleFlowE2ETest`)
- **Total tests passing**: 6/6, plus full `infrastructure` module regression: 96/96 (0 failures, 0 errors)
- **Layers used**: Integration (6, Testcontainers + real Postgres + Flyway)
- **Approval tests** (refactoring): None — no refactoring tasks in PR1
- **Pure functions created**: 0 (test infrastructure + one DTO field addition)

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused test command and exact result | `mvn -pl infrastructure -am test -Dtest=SaleFlowE2ETest -Dsurefire.failIfNoSpecifiedTests=false` → Tests run: 6, Failures: 0, Errors: 0 |
| Runtime harness command/scenario and exact result | Real Testcontainers PostgreSQL 16.15 container, real Flyway migration of all 6 project migrations, real Spring context boot — confirmed via surefire report and Flyway migration log |
| Rollback boundary | `infrastructure/pom.xml`'s 3 new test-scope deps, the new `SaleFlowE2ETest.java` file, and the additive `voucherType` field on `SaleResponse` — all independently revertable without touching PR2/PR3 |

### Deviations from Design
1. **Environment gap discovered and fixed, not in original design**: local Docker Engine 29.7.2 + Testcontainers 1.19.8 (Spring Boot 3.3.4's managed version) hit the known `testcontainers-java` Docker-29.x incompatibility (confirmed via `testcontainers/testcontainers-java#11212`, GitHub's fix: upgrade to 2.0.x, no 1.x backport). Worked around locally with `System.setProperty("api.version", "1.44")` in a static initializer rather than upgrading to Testcontainers 2.0.x (which renames every artifact and may not be compatible with Spring Boot 3.3.4's `spring-boot-testcontainers` module — a bigger, riskier change than this PR's scope). **Flagged as a risk for PR3 (CI)**: GitHub Actions' `ubuntu-latest` may run a different Docker Engine version than local; if CI hits the same incompatibility, the same `api.version=1.44` workaround applies, or PR3 may need to pin an older Docker Engine on the runner. This must be verified when PR3 is implemented, not assumed fixed by this workaround alone.
2. **`InvoicingProvider` mocked via `@MockBean`, not tested against real NubeFacT** — this was already the explicit design decision (Phase A/B split), not a deviation, restated here for clarity.
3. **Discovered and refuted a suspected pre-existing bug**: `SalesListPage.jsx`'s comment claims `GET /api/sales` "currently always returns `voucherStatus: null`". `listReflectsEachSalesOwnVoucherType` proves this is false today — the real list response correctly returns both `voucherStatus` and the newly-added `voucherType`. This comment is stale (likely predates PR4 of `sales-invoicing` wiring invoicing synchronously into `RecordSaleUseCase`) and should be corrected as a small follow-up — not fixed in this PR since it's outside Phase A's scope (backend-only).

### Issues Found
None blocking. See Deviations #1 (CI risk to verify in PR3) and #3 (stale frontend comment, follow-up).

### Remaining Tasks
- [ ] Phase 2 (PR2): Playwright full-stack E2E
- [ ] Phase 3 (PR3): CI workflow + archive gate rule (must also verify Deviation #1's Docker/Testcontainers compatibility on `ubuntu-latest`)

### Workload / PR Boundary
- Mode: chained PR slice (stacked-to-main, user-confirmed)
- Current work unit: PR1 — Phase A
- Boundary: starts from `main` (post-`explicit-voucher-type-selection`/PR#22 fix), ends with a self-contained, independently mergeable Testcontainers integration suite + the `voucherType` field
- Estimated review budget impact: ~230 lines authored (pom.xml +11, SaleResponse.java +2/-0 net, SaleFlowE2ETest.java ~215) — well within the 400-line budget for this slice

### Status
6/6 PR1 tasks complete. Ready for PR2 (Phase B — Playwright).

---

## PR2 — Phase B (Full-Stack Playwright E2E): DONE

**Mode**: Strict TDD

### Completed Tasks
- [x] 2.1 `@playwright/test` devDependency + `test:e2e` script added.
- [x] 2.2 `playwright.config.js` with two-`webServer` array (Vite + `start-backend.sh`).
- [x] 2.3 Revised — no separate Spring profile (see Deviations #1).
- [x] 2.4-2.5 `sale-voucher-flow.spec.js`: 2 scenarios (BOLETA, FACTURA), both asserting the real outgoing POST body, the real POST response, and the real subsequent GET list response.
- [x] 2.6 `frontend/e2e/README.md` written, including the port-collision incident.

### Files Changed
| File | Action | What Was Done |
|------|--------|----------------|
| `frontend/package.json` | Modified | Added `@playwright/test` devDependency, `test:e2e` script |
| `frontend/playwright.config.js` | Created | Two-`webServer` config, `reuseExistingServer: false` |
| `frontend/e2e/start-backend.sh` | Created | Throwaway Postgres (port 5433) + real Spring Boot app against it |
| `frontend/e2e/sale-voucher-flow.spec.js` | Created | 2 scenarios: BOLETA, FACTURA — real UI, real network assertions |
| `frontend/e2e/README.md` | Created | Prerequisites, env vars, known limitation |
| `frontend/vite.config.js` | Modified | Excluded `e2e/**` from Vitest's own test discovery |

### TDD Cycle Evidence
| Task | Test File | Layer | Safety Net | RED | GREEN | TRIANGULATE | REFACTOR |
|------|-----------|-------|------------|-----|-------|-------------|----------|
| 2.1-2.6 | `sale-voucher-flow.spec.js` | E2E (Playwright, real browser + real servers) | ✅ `npm test` 50/50 still passing after `vite.config.js` change | ✅ Written — real failures at each infra layer before the stack ran correctly (see Deviations) | ✅ Passed (both scenarios) | ✅ 2 cases (BOLETA, FACTURA) via shared `recordSaleThroughTheRealUi` helper | ✅ Extracted the shared helper once both scenarios existed |

### Test Summary
- **Total tests written**: 2 (`sale-voucher-flow.spec.js`)
- **Total tests passing**: 2/2 E2E, plus `npm test` (Vitest) 50/50 unaffected
- **Layers used**: E2E (2, full browser + full stack)
- **Approval tests**: None
- **Pure functions created**: 0 (test infra + config)

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused test command and exact result | `npm run test:e2e` → 2 passed (17.4s) |
| Runtime harness command/scenario and exact result | Real Vite dev server + real Spring Boot app (via `start-backend.sh`) + real throwaway Postgres 16-alpine container on port 5433 — confirmed via Playwright's `pw:webserver` debug log and direct `curl` sanity checks during debugging |
| Rollback boundary | All PR2 files are new except `vite.config.js`'s one-line `exclude` addition and `package.json`'s devDependency/script — independently revertable without touching PR1 or PR3 |

### Deviations from Design

1. **No separate `application-sandbox.yaml` profile** (design.md's original plan): found no evidence NubeFacT has a distinct sandbox base URL (only sandbox/demo *credentials* on the same API, per available NubeFacT documentation) — inventing a profile around an unconfirmed assumption would have been worse than not having one. The existing default profile already reads `NUBEFACT_API_TOKEN` from the environment and degrades to `PENDING` gracefully when absent, which is sufficient for what this suite actually asserts (voucher *type* round-trip, not NubeFacT acceptance).

2. **Real incident during implementation — reused a stale unrelated process and wrote to the real dev database.** First run of the Playwright suite used `reuseExistingServer: !process.env.CI` (the design's original plan, "standard" Playwright usage). Port 8080 already had an unrelated, long-running `mvn spring-boot:run` process left over from an earlier, unrelated session — pointed at the *real* local dev Postgres (port 5432). Playwright's health check (`GET /actuator/health` → 200) couldn't distinguish "the right server" from "a server," so it silently reused the wrong one. The test's `seedProduct`/`recordSale` calls wrote real rows into the developer's real dev database (one test sale mixed in with 5 of the user's own manual-testing sales). **Fix**: changed both `webServer` entries to `reuseExistingServer: false` — a run now fails loudly ("port already in use") instead of silently running against the wrong process. **User-directed cleanup**: gave the user exact scoped `DELETE` SQL (by the specific row ids involved) to run themselves against their own database with their own access — did not execute any mutating SQL against real dev data directly.

3. **`mvn spring-boot:run` plugin-goal resolution needed two fix attempts.** `mvn -pl infrastructure -am spring-boot:run` (the shorthand prefix) failed with "No plugin found for prefix 'spring-boot'". The fully-qualified `org.springframework.boot:spring-boot-maven-plugin:run` (no version) resolved the *latest* plugin release (a `4.2.0-M1` milestone) against the *reactor root* (`store-manager`, a `pom`-packaged aggregator with no main class) instead of `infrastructure`, and failed with "Unable to find a suitable main class". **Fix**: `start-backend.sh` runs `mvn -f infrastructure/pom.xml spring-boot:run` directly against `infrastructure`'s own `pom.xml`, which declares the plugin with the project's pinned `${spring-boot.version}` (3.3.4) — resolves the right version, against the right project, every time.

4. **Docker container cleanup is best-effort, not guaranteed.** `start-backend.sh`'s `trap cleanup EXIT INT TERM` doesn't always fire when Playwright tears down the process (observed: container left running after a normal successful test run). The script defensively `docker rm -f`s any same-named container before starting, so this self-heals on the next run and never causes a failure — documented as a known limitation in `frontend/e2e/README.md` rather than over-engineered away.

### Issues Found
None blocking, beyond Deviations #2 (real incident, fixed and disclosed) and #4 (cosmetic, documented).

### Remaining Tasks
- [ ] Phase 3 (PR3): CI workflow + archive gate rule. Must verify PR1's Deviation #1 (local Testcontainers/Docker-29.x `api.version=1.44` workaround) and PR2's `reuseExistingServer: false`/port assumptions both hold on `ubuntu-latest`.

### Workload / PR Boundary
- Mode: chained PR slice (stacked-to-main), branched from PR1's branch
- Current work unit: PR2 — Phase B
- Boundary: starts from PR1's tip, ends with a self-contained, independently mergeable Playwright E2E suite
- Estimated review budget impact: ~260 lines authored (package.json +2, playwright.config.js ~30, start-backend.sh ~25, sale-voucher-flow.spec.js ~110, README.md ~35, vite.config.js +4) — within the 400-line budget for this slice

### Status
2/2 PR2 tasks complete. Ready for PR3 (CI + archive gate).

---

## PR3 — CI + Pre-Archive Gate: DONE

**Mode**: Strict TDD (infra/config — no application code; verification is the CI run itself)

### Completed Tasks
- [x] 3.1 `.github/workflows/e2e.yml`: 3 jobs (`backend`, `frontend-unit`, `e2e-full-stack`).
- [x] 3.2 Revised — no explicit `testcontainers-java` version pin (see Deviations).
- [x] 3.3 `rules.archive` entry added to `openspec/config.yaml`; `e2e_test_command` added to the frontend project entry.
- [x] 3.4 Verified: [PR3's own first CI run](https://github.com/JoaoHiga/store-manager/actions/runs/35019328227) — all 3 jobs green, after one real fix (see Deviations).

### Files Changed
| File | Action | What Was Done |
|------|--------|----------------|
| `.github/workflows/e2e.yml` | Created | 3-job CI workflow |
| `openspec/config.yaml` | Modified | New `rules.archive` entry, `e2e_test_command` added |
| `frontend/playwright.config.js` | Modified | Added `reporter: [['list'], ['html', ...]]` for CI failure artifacts |
| `frontend/e2e/start-backend.sh` | Modified | Install `application` module before `spring-boot:run` (see Deviations) |

### Work Unit Evidence
| Evidence | Value |
|---|---|
| Focused test command and exact result | The workflow itself, run twice on PR3: first attempt failed (`e2e-full-stack` job), second attempt (after the fix below) — all 3 jobs green |
| Runtime harness command/scenario and exact result | Real GitHub Actions `ubuntu-latest` runner, both runs observed live via `gh run watch` |
| Rollback boundary | All 4 files independently revertable; the workflow and gate rule have zero effect on PR1/PR2's own code |

### Deviations from Design

1. **No `testcontainers-java` version pin** (task 3.2, revised in the tasks artifact before implementation) — the Docker-29.x workaround already lives inside `SaleFlowE2ETest` itself (PR1), independent of which version resolves.

2. **Real CI failure, found and fixed on this PR's own first run**: `start-backend.sh` ran `mvn -f infrastructure/pom.xml spring-boot:run` directly, which had only ever been tested on a local machine that already had the `application` module jar pre-installed in `~/.m2` from an unrelated earlier session. On CI's fresh checkout, that dependency isn't there — the webServer process died immediately with no diagnosable output (Playwright reported only "Process from config.webServer was not able to start. Exit code: 1", no Maven error text at all). **Fix**: added `mvn -f pom.xml -pl application -am install -DskipTests -q` before `spring-boot:run`, plus explicit progress `echo`s so a future failure at this stage is diagnosable from the CI log instead of silent. Verified: this PR's second CI run passed all 3 jobs.

3. **The local Docker-29.x incompatibility (PR1) did not reproduce on `ubuntu-latest`** — the `backend` job passed cleanly with the `api.version=1.44` workaround present but apparently unneeded there (GitHub's runner evidently doesn't run the specific Docker Engine version that triggered it locally). Left the workaround in place since it's harmless either way and protects any contributor whose local Docker happens to be on a newer version.

### Issues Found
None blocking — both real issues hit during this PR (GitHub OAuth token missing the `workflow` scope to push `.github/workflows/*`, and the missing-dependency CI failure) were found and resolved before this PR's own CI went green.

### Remaining Tasks
None — all 3 PRs of `automated-e2e-testing` are implemented and each has a green CI/test run. Ready for `sdd-verify`.

### Workload / PR Boundary
- Mode: chained PR slice (stacked-to-main), branched from PR2's branch
- Current work unit: PR3 — CI + archive gate
- Boundary: starts from PR2's tip, ends with a working CI workflow and the archive-gate rule
- Estimated review budget impact: ~110 lines authored (e2e.yml ~65, config.yaml +3, playwright.config.js +1, start-backend.sh +17 net) — well within the 400-line budget

### Status
4/4 PR3 tasks complete. All of `automated-e2e-testing`'s planned work (PR1, PR2, PR3) is done. Ready for `sdd-verify`.
