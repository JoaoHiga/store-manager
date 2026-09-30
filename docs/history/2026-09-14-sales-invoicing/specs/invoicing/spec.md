# Invoicing Specification

## Purpose

Issue SUNAT-compliant electronic vouchers (boleta/factura) for completed sales through an OSE (NubeFacT is the current adapter, chosen to stay swappable behind the `InvoicingProvider` port), handling automatic and manual retries and voids so an external service outage never blocks a sale. Peru tax-compliance-specific; depends on `sales`.

## Requirements

### Requirement: Voucher Type Determination

The system MUST determine the voucher type from the sale's buyer document type: a buyer identified by RUC MUST produce a factura; any other supported document type MUST produce a boleta.

#### Scenario: A RUC buyer produces a factura
- WHEN a sale is recorded with buyer `documentType: RUC`
- THEN the resulting voucher's type is `FACTURA`

#### Scenario: A DNI buyer produces a boleta
- WHEN a sale is recorded with buyer `documentType: DNI`
- THEN the resulting voucher's type is `BOLETA`

### Requirement: Voucher Issuance via OSE

The system MUST issue a voucher for every completed sale through the configured `InvoicingProvider`. A sale MUST complete regardless of the outcome of voucher issuance: on acceptance the voucher is persisted `ACCEPTED` with its PDF/XML/CDR links and hash; on any provider failure, timeout, or non-acceptance, the voucher MUST be persisted `PENDING` and the sale MUST NOT be rolled back or blocked.

#### Scenario: Voucher accepted on the first attempt
- GIVEN a completed sale
- WHEN the invoicing provider accepts the voucher
- THEN the voucher is persisted with status `ACCEPTED` and its PDF/XML/CDR links and hash are stored

#### Scenario: Sale completes even when the invoicing provider fails
- GIVEN a completed sale
- WHEN the invoicing provider times out or is unreachable
- THEN the sale's status remains `COMPLETED` and the voucher is persisted with status `PENDING`

#### Scenario: Sale completes even when SUNAT rejects the voucher
- GIVEN a completed sale
- WHEN the invoicing provider responds but SUNAT does not accept the voucher
- THEN the sale's status remains `COMPLETED` and the voucher is persisted with status `PENDING`, retaining the rejection reason

### Requirement: Voucher Retry

The system MUST automatically retry a `PENDING` voucher on a recurring schedule, and MUST also allow a manual retry at any time. After a bounded number of automatic attempts without success, a `PENDING` voucher MUST transition to `NEEDS_ATTENTION` and stop retrying automatically until a manual retry is triggered.

#### Scenario: A pending voucher is retried automatically and succeeds
- GIVEN a `PENDING` voucher
- WHEN the scheduled retry runs and the invoicing provider now accepts it
- THEN the voucher transitions to `ACCEPTED`

#### Scenario: A pending voucher exhausts automatic retries
- GIVEN a `PENDING` voucher that has failed the maximum number of scheduled retry attempts
- WHEN the scheduled retry would run again
- THEN the voucher transitions to `NEEDS_ATTENTION` and no further automatic attempt is made

#### Scenario: A manual retry succeeds after exhaustion
- GIVEN a voucher with status `NEEDS_ATTENTION`
- WHEN a manual retry is triggered and the invoicing provider accepts it
- THEN the voucher transitions to `ACCEPTED`

### Requirement: Voucher Void

The system MUST allow voiding an `ACCEPTED` voucher by issuing a comunicación de baja through the `InvoicingProvider`. The system MUST NOT allow voiding a voucher in any other status.

#### Scenario: Void an accepted voucher
- GIVEN a voucher with status `ACCEPTED`
- WHEN it is voided and the invoicing provider confirms the comunicación de baja
- THEN the voucher transitions to `VOIDED`

#### Scenario: Reject voiding a non-accepted voucher
- GIVEN a voucher with status `PENDING` or `NEEDS_ATTENTION`
- WHEN voiding it is attempted
- THEN the system MUST reject the request with a validation error

### Requirement: Outbound Field Validation

Before sending a request to the configured OSE, the system MUST validate each field against that provider's documented limits (e.g. item description length) and MUST reject the request locally with a clear reason instead of sending a request known to be invalid.

#### Scenario: Reject an over-length item description before calling the provider
- WHEN a sale's line item description exceeds the invoicing provider's documented maximum length
- THEN the system MUST reject the sale with a validation error, and MUST NOT call the invoicing provider
