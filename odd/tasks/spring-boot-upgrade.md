# Feature: Spring Boot upgrade path

Status: **in progress** on branch `chore/spring-boot-3.5` (2026-10-18).

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
- [ ] S3 (separate feature, later) Migrate to Spring Boot 4.

## Checks

- `mvn clean test` and `npm run test:e2e` (in `frontend/`) pass; `architecture-doc.mjs --check` passes.

## Progress
- S1 (route: inline, 1 file): `ignore` for `org.springframework.boot:*` semver-major in the Maven block. YAML parses.
  Effect visible on the next Dependabot run.
- S2 (route: inline, 1 file): `spring-boot.version` 3.3.4 -> 3.5.16, no code changes needed. With Docker:
  `mvn clean test` exit 0; `npm run test:e2e` 3 passed; `architecture-doc.mjs --check` up to date.
  (A first run without Docker failed only the 2 Testcontainers tests: environment, not the upgrade.)

## Next step

Push + PR (user). Close #37 once merged; S3 becomes its own feature.
