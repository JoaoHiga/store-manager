# Feature: CI pipeline improvements

Status: **in progress** on branch `ci/pipeline-hardening` (resumed 2026-10-18). Not pushed yet.

## Objective

Make the CI workflow (`.github/workflows/e2e.yml`) catch more defects, cost less, and enforce merges.

## Context (observed 2026-10-17)

- One workflow, `E2E`, on every `pull_request` and on `push` to `main`. Four parallel jobs: backend
  (`mvn -B clean test`), frontend unit (`npm test`), architecture doc (generator tests + `--check`),
  full-stack E2E (Playwright). A full run takes about 1.6 minutes.
- SonarCloud runs as automatic analysis (GitHub app), outside the workflow, without coverage.
  Its duplication gate (3% on new code) failed on PRs #32 (5.7%) and #33 (3.2%), probably because
  tests are counted (three `InMemoryProductRepository` copies; unverified, the dashboard was private).
- Branch protection was unavailable (private repo on GitHub Free). The user is making the repo
  public, which enables it.

## Tasks

- [ ] C1 Branch protection / ruleset on `main` once the repo is public: require the four CI jobs
      (and SonarCloud, if wanted) before merge.
- [x] C2 Add `npm run build` to the frontend job (Vite build errors are not caught today).
- [x] C3 Add `npm run lint` (oxlint is installed but never run).
- [ ] C4 SonarCloud: exclude tests from duplication (`sonar.cpd.exclusions`); optionally move Sonar
      into the workflow with JaCoCo and Vitest coverage.
- [x] C5 `concurrency` with `cancel-in-progress: true`: grouped per ref for pull requests, per run for
      pushes to `main` (so main runs never supersede each other).
- [x] C6 `timeout-minutes` on every job.
- [x] C7 `permissions: contents: read` at workflow level.
- [x] C8 Dependabot for `github-actions`, `npm` (frontend/) and `maven`.
- [x] C9 Upload surefire reports as an artifact when the backend job fails or is cancelled (timeout).
- [ ] C10 (optional) Cache Playwright browsers (`~/.cache/ms-playwright`, keyed by version).
- [ ] C11 (optional) Rename the workflow from `E2E` to `CI`.

Keep the four parallel jobs; the structure is fine.

## Checks

- The workflow passes on a PR from the `ci/` branch; a deliberately broken build or lint fails it.
- `node scripts/architecture-doc.mjs --check` still passes.

## Progress

- 2026-10-18, commit `aff1d9c` (route: inline, 3 files, 48 lines): C2, C3, C5, C6, C7, C8, C9.
  - Checks: `npm run lint`, `npm run build` (in `frontend/`) and `node scripts/architecture-doc.mjs --check`
    pass locally. The workflow has not run yet because the branch is not pushed.
  - Native review: risk was high. The user granted consent, all 4 lenses approved, and the review was
    acknowledged (lineage `review-fc9f4edff8d95b07`). The reviewed boundary is now `aff1d9c`.

## Advisory follow-ups from the review (non-blocking)

- [x] (a) The `concurrency` comment says pushes to `main` always run to completion. That is not quite true:
  the group is per ref, so a newer push to `main` can still supersede a *pending* (queued) run.
  Fix the comment, or use `github.run_id` in the group for `main`.
- [x] (b) `failure()` does not fire when a job hits `timeout-minutes` (that is a cancellation), so the
  surefire artifact is skipped exactly when a timeout happens. Consider `if: failure() || cancelled()`.
- [x] (c) The job is still named "Frontend (Vitest unit tests)" but now runs lint and build too. Rename it.
- [ ] (d) The timeouts (15/10/20 min) are not tied to measured runtimes. Check them after the first CI runs.

Follow-ups (a)-(c) were fixed on 2026-10-18 (inline, 1 file): main runs use a per-run concurrency
group, artifact uploads also run on `cancelled()` (backend and Playwright), and the frontend job is
renamed. YAML parses, `architecture-doc --check` passes. Not yet run on CI.
Commit `7de3351`, native review high risk, granted, 4 lenses approved, acknowledged (lineage
`review-25857af385eb83d0`). Accepted trade-off: `cancelled()` also uploads partial reports from PR runs
superseded by a newer push (harmless, 7-day retention).

## Next step

Push the branch and open a PR so the workflow proves itself on CI.
Remaining tasks: C1 (GitHub settings, after the PR), C4, C10, C11.
