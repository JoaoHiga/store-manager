# Research: Automated E2E Testing + Pre-Archive Gate

## Status: done

## Questions Investigated

1. What's the current recommended pattern for Testcontainers + PostgreSQL + Flyway in a Spring Boot 3.x app (Phase A)?
2. How does Playwright handle driving a real E2E test across two independently-run local servers (Vite dev server + Spring Boot backend)?
3. Does a stock `ubuntu-latest` GitHub Actions runner support Testcontainers out of the box, and are there known gotchas?

## Claims

### C1 — Testcontainers + Spring Boot + Flyway needs no extra migration wiring
Spring Boot 3.1+ auto-detects the `@ServiceConnection`-annotated `PostgreSQLContainer` bean and points the datasource at it; Flyway (already a dependency in `infrastructure/pom.xml`) then runs the project's real migrations against that real Postgres instance with zero extra configuration — the same schema, constraints, and DDL that production uses, which H2 does not faithfully reproduce (partial indexes, generated columns, some CHECK constraint semantics).
- Sources: [How to Use Spring Boot Testcontainers](https://oneuptime.com/blog/post/2026-01-26-spring-boot-testcontainers/view), [Spring Boot Testing — Testcontainers and Flyway](https://dev.to/kirekov/spring-boot-testing-testcontainers-and-flyway-2jpd), [Testing Spring Boot Applications Using Testcontainers (JetBrains)](https://blog.jetbrains.com/idea/2024/12/testing-spring-boot-applications-using-testcontainers/)
- Confidence: High — consistent across independent sources, and `@ServiceConnection` is a documented Spring Boot 3.1+ feature, not a third-party workaround.

### C2 — Playwright's `webServer` config natively supports launching multiple independent processes
Playwright's config accepts an array of `webServer` entries, each with its own `command`, `url` (health-check to wait on), `name` (log prefix), and `reuseExistingServer` (recommended as `!process.env.CI` so local runs reuse an already-running dev server, but CI always boots fresh). This directly fits store-manager's shape: one entry for `npm run dev` (Vite) and one for the Spring Boot app (`mvn -pl infrastructure spring-boot:run` or the packaged jar).
- Sources: [Web server | Playwright (official docs)](https://playwright.dev/docs/test-webserver), [Configuring web server in Playwright for E2E testing](https://umairqa.medium.com/configuring-web-server-in-playwright-for-end-to-end-testing-db3dda8415b4)
- Confidence: High — official Playwright documentation.

### C3 — GitHub Actions `ubuntu-latest` ships Docker pre-installed; Testcontainers needs no extra CI setup
Docker Engine is preinstalled on GitHub-hosted `ubuntu-latest` runners, so Testcontainers-based tests (Phase A) run in CI the same way they do locally, with no `docker:dind` service or extra install step needed.
- Sources: [GitHub-hosted runners reference (GitHub Docs)](https://docs.github.com/en/actions/reference/runners/github-hosted-runners), [Running Testcontainers Tests Using GitHub Actions and Testcontainers Cloud (Docker blog)](https://www.docker.com/blog/running-testcontainers-tests-using-github-actions/)
- Confidence: High.

## Gaps / Contradictions

- **Recent Docker-version regression risk**: `testcontainers/testcontainers-java` issue #11491 reports GitHub's Ubuntu runners upgrading to Docker Engine 29.1 broke builds using `testcontainers-java`, even on its latest version at the time. Status of the fix wasn't confirmed further in this search. **Action for design phase**: pin a known-good `testcontainers-java` version and note this as a watch-item, not a blocker.
  - Source: [testcontainers-java issue #11491](https://github.com/testcontainers/testcontainers-java/issues/11491)
- **NubeFacT sandbox-in-CI specifics** (credentials management, rate limits, availability SLA) is not researchable via general web search — it's specific to the NubeFacT account/contract this project already has. Not a blocker for design, but the design doc must treat NubeFacT sandbox credentials as a CI secret (`GENTLE_AI` style: `NUBEFACT_SANDBOX_TOKEN` or similar) and explicitly accept the flakiness/external-dependency tradeoff the user already chose over stubbing.

## Next Recommended

`sdd-propose`
