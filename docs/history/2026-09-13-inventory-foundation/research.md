# SDD Research — inventory-foundation

`gentle-ai.sdd-research/v1`

**Revision**: 1
**Outcome**: done
**Admission**: capability grants used — `documentation` (WebFetch, primary-source pages) and `open-web` (WebSearch)
**Accessed**: 2026-09-13

## Questions

1. Is TanStack Query justified over plain `fetch` + `useEffect` for a beginner-built React CRUD app?
2. What is the current officially-documented way to scaffold a new React SPA?
3. For a Hexagonal-Architecture Spring Boot backend, should package structure be single-module (package-by-layer) or multi-module (Maven/Gradle)?
4. Should the frontend start in TypeScript or plain JavaScript, given the owner knows Java well but is new to frontend frameworks?

## Sources

| ID | Class | Title | Publisher | URL | Accessed |
|---|---|---|---|---|---|
| S1 | secondary/blog | Beginner's Guide to React Query (TanStack Query) | Zero To Mastery | https://zerotomastery.io/blog/react-query/ | 2026-09-13 |
| S2 | secondary/blog | React Query vs TanStack Query vs SWR: A 2025 Comparison | Refine | https://refine.dev/blog/react-query-vs-tanstack-query-vs-swr-2025/ | 2026-09-13 |
| S3 | official documentation (fetched directly) | Getting Started | Vite | https://vite.dev/guide/ | 2026-09-13 |
| S4 | expert technical article (fetched directly) | Hexagonal Architecture With Spring Boot | Arho Huttunen | https://www.arhohuttunen.com/hexagonal-architecture-spring-boot/ | 2026-09-13 |
| S5 | secondary/blog | TypeScript vs JavaScript: Which to Learn in 2026 | Futurense | https://futurense.com/blog/typescript-vs-javascript | 2026-09-13 |
| S6 | secondary/blog | TypeScript vs JavaScript: Which Should You Learn First in 2026? | DEV Community | https://dev.to/_d7eb1c1703182e3ce1782/typescript-vs-javascript-which-should-you-learn-first-in-2026-19jh | 2026-09-13 |

## Validated Claims

- **C1** — TanStack Query is recommended over plain `useEffect` + `fetch` for a beginner-built CRUD app: it removes hand-rolled loading/error/caching/retry boilerplate and is the more maintainable default for server state. — Sources: S1, S2
- **C2** — Vite is the current officially-documented tool to scaffold a new React project, via `npm create vite@latest my-react-app -- --template react`. Confirmed directly on the Vite docs page. — Source: S3
- **C3** — A recognized Hexagonal-Architecture-with-Spring-Boot reference recommends a **multi-module build** (application module with zero framework dependencies + infrastructure module with the Spring/adapter code), specifically because only a module boundary can mechanically *enforce* that framework code never leaks into business logic — a single module with package-by-layer relies on discipline/code review instead of the build to keep that boundary. — Source: S4
- **C4** — Consensus beginner guidance is to learn a new framework (React) in plain JavaScript first, and introduce TypeScript on a second project rather than learning both simultaneously — despite TypeScript's larger current job-market footprint. — Sources: S5, S6

## Contradictions / Uncertainty / Freshness

- **Contradicts the exploration's draft recommendation**: `exploration.md` proposed a single Maven module with package-by-layer "to keep build complexity low while learning." C3 (from a primary/expert source, not a low-effort blog) argues the opposite — that only a module boundary meaningfully enforces the Hexagonal boundary, and package-by-layer alone is easy to erode over time. This is a genuine tradeoff, not a settled fact: multi-module Maven is a real new concept, but the user's stated strength is specifically Java/Maven-adjacent tooling, so the added complexity may be well within reach. **Flagging for an explicit product decision in `sdd-propose`, not resolving here.**
- **Unconfirmed claim, excluded**: several secondary sources claimed create-react-app (CRA) was deprecated by the React team around February 2025. The primary source fetched directly (S3, vite.dev) makes no mention of CRA or its status at all, so this claim is **not included as validated** — it just wasn't material to the recommendation anyway, since Vite is presented as the standard from S3 regardless of CRA's status.
- C1, C2, and C4 are corroborated by more than one independent source and not contradicted by anything found.

## Product Choices (non-authoritative — orchestrator/user decide in `sdd-propose`)

- Frontend scaffolding: Vite + React confirmed as current standard (supports the stack already chosen).
- Server-state library: TanStack Query supported by evidence; no contradicting source found.
- Frontend language: start in plain JavaScript, add TypeScript later — supported by evidence, matches the user's stated wish to learn frontend gradually rather than two new things at once.
- Backend module structure: **CONFIRMED by user** — multi-module Maven (`application` module with zero Spring dependencies + `infrastructure` module), per C3. Build-enforced Hexagonal boundary, not just package convention.

## Pre-Proposal State (`gentle-ai.sdd-preproposal/v1`)

- Revision: 1
- Exploration outcome/reference: `openspec/changes/inventory-foundation/exploration.md` — Ready for Proposal: Yes
- Research request/classes: `documentation`, `open-web` — admitted, outcome `done`
- Evidence references: `openspec/changes/inventory-foundation/research.md` (this file), Engram `sdd/inventory-foundation/research`
- Product decisions: **confirmed**
  - Backend module structure: multi-module Maven (application + infrastructure)
  - Frontend data fetching: TanStack Query
  - Frontend scaffolding: Vite
  - Frontend language: plain JavaScript first, TypeScript deferred
- Store mode: hybrid — openspec (this file) and Engram (`sdd/inventory-foundation/research`) both written, equal content
- `proposal_ready`: true
