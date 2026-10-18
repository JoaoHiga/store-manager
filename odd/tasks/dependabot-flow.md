# Feature: Dependabot PRs that can merge on their own

Status: **in progress** on branch `ci/dependabot-flow` (2026-10-18).

## Objective

Dependabot update PRs pass CI without manual edits, and arrive grouped instead of one PR per package.

## Problem

After PR #36 enabled Dependabot, it opened 13 PRs (#37-#49). Every npm and Maven bump fails the required
`Architecture doc` check: the generated sections of `docs/architecture.md` print each dependency's
version, so any bump makes the doc stale (`architecture-doc.mjs --check` exits 1). Observed on #47
(run 39161722706). A workflow that regenerates and commits is not viable: it needs write permission,
and pushes made with `GITHUB_TOKEN` do not trigger CI, so required checks would never run.

## Scope

- The generator stops printing versions of dependencies that Dependabot manages. Versions live in
  `package.json` and the POMs. The Java version is not Dependabot-managed and may stay.
- `.github/dependabot.yml` groups minor and patch updates per ecosystem; majors stay separate.
- Out of scope: Spring Boot 3 -> 4 (#37), a major migration that breaks backend and E2E. Own feature.

## Tasks

- [x] D1 Generator: drop dependency version columns; tests first; regenerate the doc.
- [x] D2 Dependabot: `groups` for minor + patch per ecosystem.

## Checks

- `node --test scripts/architecture-doc.test.mjs` and `node scripts/architecture-doc.mjs --check` pass.
- After merge: rebased Dependabot PRs pass the architecture-doc check.

## Progress

- D1 (route: delegated writer, 3 files incl. tests): generator renders dependency tables without versions
  (`renderBackendTechnologies`, `renderFrontendPackages`); Java version kept in the row name, Spring Boot
  version dropped. RED: test file failed to load (functions not exported); GREEN: 7/7. `--check` passes.
- D2 (route: inline, 1 file): one `*-minor-patch` group per ecosystem; majors keep their own PR. Syntax checked
  against docs.github.com Dependabot options reference (`groups.update-types`: patch/minor/major; `patterns`
  supports `*`). YAML parses. Effect only visible after merge, on the next Dependabot run.

## Next step

Push + PR (user). After merge: ask Dependabot to recreate/rebase open PRs and confirm the architecture-doc
check passes on them; handle #37 (Spring Boot 4) as its own feature.
