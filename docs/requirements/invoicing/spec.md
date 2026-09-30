# Invoicing Specification

## Purpose

Issue SUNAT-compliant electronic vouchers (boleta/factura) for completed sales through an OSE (NubeFacT is the current adapter, chosen to stay swappable behind the `InvoicingProvider` port), handling automatic and manual retries and voids so an external service outage never blocks a sale. Peru tax-compliance-specific; depends on `sales`.

## Requirements

### Requirement: Voucher Type Determination

The system MUST determine the voucher type from an explicit `voucherType` choice made at sale-recording time, not derive it from the buyer's document type. A `FACTURA` MUST be rejected unless the buyer's document type is RUC. A `BOLETA` MUST be accepted regardless of the buyer's document type — including RUC, or no document at all (a "boleta simple" sale under S/700). For a boleta simple, the outbound request to the OSE MUST carry no buyer document type/number rather than a placeholder value.

#### Scenario: A RUC buyer explicitly requesting a factura succeeds
- WHEN a sale is recorded with buyer `documentType: RUC` and `voucherType: FACTURA`
- THEN the resulting voucher's type is `FACTURA`

#### Scenario: A RUC buyer explicitly requesting a boleta succeeds
- WHEN a sale is recorded with buyer `documentType: RUC` and `voucherType: BOLETA`
- THEN the resulting voucher's type is `BOLETA`

#### Scenario: A non-RUC buyer requesting a factura is rejected
- WHEN a sale is recorded with buyer `documentType: DNI` (or CE, Pasaporte, or no document) and `voucherType: FACTURA`
- THEN the system MUST reject the request with a validation error, and no voucher is issued

#### Scenario: A boleta simple (no buyer document) produces a boleta
- WHEN a sale under S/700 is recorded with no buyer document and `voucherType: BOLETA`
- THEN the resulting voucher's type is `BOLETA`, with `null` buyer document type/number, and the OSE request maps this to its "varios/menor cuantía" document-type code

### Requirement: Voucher Issuance via OSE

The system MUST issue a voucher for every completed sale through the configured `InvoicingProvider`. A sale MUST complete regardless of the outcome of voucher issuance: on acceptance the voucher is persisted `ACCEPTED` with its PDF/XML/CDR links and hash; on any provider failure, timeout, or non-acceptance, the voucher MUST be persisted `PENDING` and the sale MUST NOT be rolled back or blocked.

The voucher MUST be registered as `PENDING` in the same atomic step that records the sale, and issuance MUST be attempted immediately after that step completes, outside it: a sale's data is never held locked while waiting on the provider.

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

The automatic retry MUST skip a `PENDING` voucher updated within the last minute, so it never overlaps the immediate issuance attempt made right after a sale is recorded (which would issue the same voucher twice). A voucher whose immediate attempt never happened (e.g. the server stopped right after the sale was persisted) MUST still be picked up once that period has passed.

#### Scenario: A pending voucher is retried automatically and succeeds
- GIVEN a `PENDING` voucher
- WHEN the scheduled retry runs and the invoicing provider now accepts it
- THEN the voucher transitions to `ACCEPTED`

#### Scenario: A just-registered voucher is not retried yet
- GIVEN a `PENDING` voucher updated less than a minute ago
- WHEN the scheduled retry runs
- THEN the voucher is left untouched, with no issuance attempt

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
