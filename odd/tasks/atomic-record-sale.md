# atomic-record-sale

**Status**: IN PROGRESS
**Engram mirror**: `odd/atomic-record-sale/tasks`
**Branch**: `fix/atomic-record-sale` (stacked on `docs/architecture-doc`, PR #29)

## Objective

Recording a sale is all-or-nothing, never oversells stock, and never holds a database transaction open
while waiting for NubeFacT.

## Problem

`RecordSaleUseCase` saved the sale, called NubeFacT synchronously, then decremented stock: three separate
writes with no transaction. A failure in between left a sale without its stock decremented. Stock was read,
decremented in memory and written back as an absolute value, so two concurrent sales of the last unit could
both succeed (lost update).

## Why not SAGA

SAGA coordinates steps across services that each own a database, undoing earlier steps with compensations.
Here there is one database, so the local steps fit in one transaction. The only remote step (NubeFacT) must
not be compensated: the business rule is that a sale is never blocked or undone because SUNAT is down; the
voucher stays `PENDING` and is retried. Decision taken with the user on 2026-09-29.

## Design: local transaction + transactional outbox

1. One transaction (`TransactionRunner` port, Spring `TransactionTemplate` adapter): validate products,
   save the sale, decrement stock with a conditional `UPDATE ... WHERE stock_quantity >= :quantity`, and
   save the voucher as `PENDING` (attempt 0).
2. After commit: attempt issuance once (`IssueVoucherForSaleUseCase.issue`). Any failure leaves the voucher
   `PENDING`; the sale response still succeeds.
3. `RetryPendingVouchersJob` skips `PENDING` vouchers updated less than a minute ago, so it never races the
   immediate attempt (double issuance), and still recovers vouchers whose immediate attempt never happened
   (crash after commit).

## Constraints

- `application` stays free of Spring: transactions enter through a port.
- Existing `IssueVoucherForSaleUseCase.execute` behavior and tests stay valid (it becomes
  `registerPending` + `issue`).
- TDD practice: RED before GREEN. Testcontainers E2E runs only in CI (no Docker in the local WSL).

## Tasks

- [x] T1 Application: `TransactionRunner` port, `ProductRepositoryPort.decrementStockIfAvailable`,
  `IssueVoucherForSaleUseCase.registerPending/issue`, `RecordSaleUseCase` rewritten; fakes updated.
  Acceptance: new tests (issuance after commit; pending voucher and stock written at commit; concurrent
  stock loss rejected without issuance) RED then GREEN; full `application` suite green.
  Evidence: 3 new tests RED for the expected reasons (no commit; no rejection), 18 existing green; then
  145/145 green.
- [x] T2 Retry job grace period. Acceptance: test for a freshly updated voucher being skipped, RED then GREEN.
  Evidence: RED (voucher retried, status ACCEPTED); GREEN 4/4. Existing seeds moved to 10 minutes ago.
- [x] T3 Infrastructure: JPA conditional update, `SpringTransactionRunner`, wiring.
  Acceptance: adapter tests (decrement success/insufficient, rollback) RED then GREEN; `mvn clean test` green
  locally except Docker-only tests; new E2E test green in CI.
  Evidence: stub adapters RED (no rollback; decrement never applied); GREEN 8/8. `mvn clean test`:
  application 145/145; infrastructure 96 run, only `SaleFlowE2ETest` errors ("Could not find a valid Docker
  environment", local WSL). E2E pending CI.
- [x] T4 `docs/architecture.md`: regenerate; update flow and known risks. Acceptance: `--check` passes.
  Evidence: `--check` failed before regenerating (new port detected), passes after. Two fixed risks removed;
  two found while working added (product edit overwrites stock; manual retry vs job race).
- [x] T5 `docs/requirements/`: sales (atomic recording, concurrent last unit) and invoicing (register PENDING
  with the sale, attempt after; retry grace period). Added before merge, per the `AGENTS.md` rule.

## Delivery

Strategy: `single-pr`, stacked on PR #29. Forecast ~400 authored lines including tests.

## Progress

- 2026-09-29: created; T1-T4 done locally.

## Next step

Push, open PR stacked on #29, confirm CI (Testcontainers + Playwright E2E) green. Follow-ups for the two new
risks are not in scope.
