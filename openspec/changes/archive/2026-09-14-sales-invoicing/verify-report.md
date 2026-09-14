```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:94f23dca6245557256ccee2451c2ec6ba45365ff
verdict: pass
blockers: 0
critical_findings: 0
requirements: 10/10
scenarios: 24/24
test_command: mvn clean test && (cd frontend && npm test)
test_exit_code: 0
test_output_hash: sha256:be12584603fe3ffa18ca83caf5a422966737be8f6c4c1b5f0d87828bb098c805
build_command: mvn -q -DskipTests package && (cd frontend && npm run build)
build_exit_code: 0
build_output_hash: sha256:689bedb0b76fef978405757ebf90ffe2d90defe1ba76fa75bae15762b7456ef8
```

## Verification Report

**Change**: sales-invoicing
**Version**: adds `sales` and `invoicing` capabilities (new, no prior spec)
**Mode**: Strict TDD (per every PR's RED/GREEN/TRIANGULATE evidence in apply-progress)

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 21 |
| Tasks complete | 20 |
| Tasks incomplete | 1 — **5.5, manual E2E click-through against the real running app**: requires a human at the keyboard, cannot be run by an agent; explicitly deferred by the user's own decision to proceed to archive without it |

### Build & Tests Execution

**Build**: ✅ Passed
```text
mvn -q -DskipTests package && (cd frontend && npm run build)
Backend: BUILD SUCCESS. Frontend: vite build, 76 modules transformed.
```

**Tests**: ✅ 122 passed / ❌ 0 failed / ⚠️ 0 skipped
```text
mvn clean test && (cd frontend && npm test)
Backend: Tests run: 80 (application + infrastructure combined), Failures: 0 — BUILD SUCCESS
Frontend: Test Files 9 passed (9), Tests 42 passed (42)
```
Run directly against `main` at `94f23dc` (post-merge of all 5 PRs + the follow-up PR #17 that brought PR4/PR5's content into `main`), not just against a feature branch.

**Coverage**: N/A — no coverage tool configured (unchanged from prior changes)

### Spec Compliance Matrix — `sales`
| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Record a Sale | Record a valid sale | `RecordSaleUseCaseTest > recordsAValidSaleSnapshottingPriceAndDescriptionAndComputingTheTotal`, `SaleControllerTest > recordsAValidSaleAndReturns201` | ✅ COMPLIANT |
| Record a Sale | Reject a sale referencing an inactive product | `RecordSaleUseCaseTest > rejectsASaleReferencingAnInactiveProduct`, `SaleControllerTest > rejectsASaleReferencingAnInactiveProductWith400` | ✅ COMPLIANT |
| Record a Sale | Reject a sale exceeding available stock | `RecordSaleUseCaseTest > rejectsASaleExceedingAvailableStock` (+ `rejectsASaleWhoseCombinedQuantityForTheSameProductAcrossTwoLinesExceedsStock`, `rejectsASecondSaleThatWouldExceedStockRemainingAfterAPriorSale`), `SaleControllerTest > rejectsASaleExceedingAvailableStockWith400` | ✅ COMPLIANT |
| Record a Sale | Reject a sale with no buyer reference | `RecordSaleUseCaseTest > rejectsASaleWithNoBuyerReference`, `SaleControllerTest > rejectsASaleWithNoBuyerReferenceWith400` | ✅ COMPLIANT |
| Sale Line Item Price Snapshot | A later catalog price change does not affect a past sale | `RecordSaleUseCaseTest > aLaterProductPriceChangeDoesNotAffectAnAlreadyRecordedSalesLineItem`, `SaleControllerTest > aPastSalesLineItemPriceIsUnaffectedByALaterCatalogPriceChange` | ✅ COMPLIANT |
| List and View Sales | View a sale's detail with an accepted voucher | `SaleControllerTest > viewsASalesDetailWithAnAcceptedVoucherIncludesStatusAndDocumentLinks` | ✅ COMPLIANT |
| Void a Sale | Void a completed sale with an accepted voucher | `CancelSaleUseCaseTest > voidingASaleWithAnAcceptedVoucherVoidsTheVoucherAndCancelsTheSale`, `SaleControllerTest > voidingASaleReturnsItCancelled` | ✅ COMPLIANT |
| Void a Sale | Reject voiding a sale with a pending voucher | `CancelSaleUseCaseTest > voidingASaleWithAPendingVoucherIsRejectedAndTheSaleStaysCompleted`, `SaleControllerTest > voidingASaleWhoseVoucherIsNotAcceptedReturns409` | ✅ COMPLIANT |

### Spec Compliance Matrix — `invoicing`
| Requirement | Scenario | Test | Result |
|---|---|---|---|
| Voucher Type Determination | A RUC buyer produces a factura | `IssueVoucherForSaleUseCaseTest > aRucBuyerProducesAFactura` | ✅ COMPLIANT |
| Voucher Type Determination | A DNI buyer produces a boleta | `IssueVoucherForSaleUseCaseTest > aDniBuyerProducesABoleta` | ✅ COMPLIANT |
| Voucher Issuance via OSE | Voucher accepted on the first attempt | `IssueVoucherForSaleUseCaseTest > voucherAcceptedOnFirstAttemptIsPersistedAcceptedWithLinksAndHash` | ✅ COMPLIANT |
| Voucher Issuance via OSE | Sale completes even when the invoicing provider fails | `IssueVoucherForSaleUseCaseTest > saleCompletesAsPendingWhenTheProviderTimesOutOrIsUnreachable` | ✅ COMPLIANT |
| Voucher Issuance via OSE | Sale completes even when SUNAT rejects the voucher | `IssueVoucherForSaleUseCaseTest > saleCompletesAsPendingWithReasonRetainedWhenSunatRejects` | ✅ COMPLIANT |
| Voucher Retry | A pending voucher is retried automatically and succeeds | `RetryPendingVoucherUseCaseTest > aPendingVoucherRetriedAutomaticallyAndAcceptedTransitionsToAccepted`, `RetryPendingVouchersJobTest > retriesEveryPendingVoucherAndLeavesAcceptedOnesUntouched` | ✅ COMPLIANT |
| Voucher Retry | A pending voucher exhausts automatic retries | `RetryPendingVoucherUseCaseTest > aPendingVoucherExhaustsAutomaticRetriesAndEscalatesToNeedsAttention` | ✅ COMPLIANT |
| Voucher Retry | A manual retry succeeds after exhaustion | `RetryPendingVoucherUseCaseTest > aManualRetrySucceedsAfterExhaustion` | ✅ COMPLIANT |
| Voucher Void | Void an accepted voucher | `VoidSaleUseCaseTest > voidingAnAcceptedVoucherTransitionsToVoided` | ✅ COMPLIANT |
| Voucher Void | Reject voiding a non-accepted voucher | `VoidSaleUseCaseTest > voidingAPendingVoucherIsRejected`, `> voidingANeedsAttentionVoucherIsRejected` | ✅ COMPLIANT |
| Outbound Field Validation | Reject an over-length item description before calling the provider | `NubeFactInvoicingAdapterTest > issueBoletaRejectsOverLongDescriptionBeforeCallingProvider` | ✅ COMPLIANT |

**Compliance summary**: 24/24 scenarios compliant (8 `sales` + 16 `invoicing` — some scenarios covered by more than one test at different layers, counted once each)

### Correctness (Static Evidence)
| Requirement area | Status | Notes |
|---|---|---|
| Sales | ✅ Implemented | Stock validated cumulatively per product across a request, not per-line (RDD-caught gap in PR1, fixed) |
| Invoicing domain | ✅ Implemented | `Voucher` status machine enforces valid transitions only; void only transitions on OSE confirmation (RDD-caught gap in PR2, fixed) |
| NubeFacT adapter | ✅ Implemented | Correlativo durable (DB-backed) and concurrency-safe (single-transaction lock spanning the whole HTTP call, proven by a real multi-threaded test) — both fixed after RDD found the original in-memory counter, then a race in the first fix |
| Wiring | ✅ Implemented | `CancelSaleUseCase` resolves the sale before the irreversible OSE void call, and distinguishes a post-void save failure via `SaleCancellationInconsistentException` (RDD-caught gap, fixed) |
| API contract | ✅ Implemented | `GET /api/sales`/`{id}` now returns real `voucherStatus` + PDF/XML/CDR links — this was found missing (hardcoded `null`) while building the frontend and fixed as a follow-up commit on PR4 |

### Coherence (Design)
| Decision | Followed? | Notes |
|---|---|---|
| Package-level bounded contexts (`sales`, `invoicing`), no new Maven module | ✅ Yes | Mirrors `catalog`'s existing layout throughout |
| `InvoicingProvider` port, OSE-agnostic domain shapes | ✅ Yes | `NubeFactInvoicingAdapter` is the only class that knows NubeFacT's field names |
| Spring `RestClient` with connect/read timeouts | ✅ Yes | `NubeFactConfiguration` |
| `@Scheduled` retry job, capped attempts → `NEEDS_ATTENTION` | ✅ Yes | `RetryPendingVouchersJob`, cap enforced in `RetryPendingVoucherUseCase` |
| Sale line-item price snapshot | ✅ Yes | Verified by a dedicated scenario/test |
| Threat Matrix mitigations (credential env-only, redacted logging, timeouts, retry cap, field-length validation) | ✅ Yes | All present in `NubeFactInvoicingAdapter`/`NubeFactConfiguration` |

### Issues Found
**CRITICAL/BLOCKER**: None outstanding — 5 were found across PR1-PR4 by RDD review during `apply` (stock not decremented; void ignored OSE confirmation; non-durable correlativo; a concurrency race in that fix; cancel-not-atomic), every one fixed and either RDD-reviewed-and-approved or (PR3's concurrency fix only) independently verified after exceeding the review's correction-line budget — see each PR's apply-progress for the individual finding IDs and fixes.
**WARNING/SUGGESTION**: ~15 non-blocking advisory findings left across PR2-PR5 (missing edge-case test coverage, a broad exception handler, an unused method parameter, a few frontend test-coverage suggestions) — none block this change; candidates for a future hardening pass, same pattern as `product-catalog-hardening` followed `inventory-foundation`.

### Verdict
**PASS**

20/21 tasks complete (5.5 — manual E2E click-through — is the sole exception: it requires a human, not something an agent can execute, and the user explicitly chose to proceed to archive without it). All 24 spec scenarios across both new capabilities (`sales`, `invoicing`) have a passing covering test. Fresh `mvn clean test` + `npm test` both green (122 tests total) run directly against `main` post-merge, not just a feature branch. Fresh `mvn package` + `npm run build` both succeed. Every design decision in `design.md` was followed, including the Threat Matrix mitigations for this change's first external-service boundary. Five real defects were found by RDD review during implementation and all were fixed before merge.
