# Sales Specification

## Purpose

Record sales against the product catalog and trigger invoicing, list and view sales with their voucher status, and void a completed sale when needed. This is the transactional core the shop's day-to-day operation runs on, sitting on top of `product-catalog` and triggering `invoicing`.

## Requirements

### Requirement: Record a Sale

The system MUST allow recording a sale with one or more line items, each referencing an existing, active `Product` by id, with `quantity` (> 0) and `unitPrice`/`description` snapshotted from the product at the moment of sale. The system MUST reject a sale referencing a nonexistent or inactive product, and MUST reject a sale whose requested quantity exceeds the product's current stock.

A buyer reference (`documentType`, `documentNumber`) is required only when the sale's computed total is S/700 or more ("boleta simple" threshold); below that, the sale may be recorded with no buyer reference at all. When present, `documentType` MUST be one of DNI (exactly 8 numeric digits), RUC (11 digits, prefix in `{10, 15, 17, 20}`, valid modulo-11 check digit), Carnet de Extranjería, or Pasaporte (non-blank, 5-20 characters); a malformed value is rejected regardless of the sale's total.

#### Scenario: Record a valid sale
- GIVEN an active product "iPhone 12" with `stockQuantity: 3`
- WHEN a sale is recorded with one line item for that product, `quantity: 1`, and a buyer with `documentType: DNI`, `documentNumber: "12345678"`
- THEN the sale is persisted with status `COMPLETED`, a computed `total`, and the line item's `unitPrice` matching the product's `salePrice` at that moment

#### Scenario: Reject a sale referencing an inactive product
- GIVEN a product that has been deactivated
- WHEN a sale is recorded with a line item for that product
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a sale exceeding available stock
- GIVEN an active product with `stockQuantity: 2`
- WHEN a sale is recorded with a line item for that product, `quantity: 3`
- THEN the system MUST reject the request with a validation error

#### Scenario: Record a low-total sale with no buyer reference
- GIVEN a sale whose computed total is below S/700
- WHEN the sale is recorded without a `documentType`/`documentNumber`
- THEN the sale is persisted successfully with both buyer fields `null`

#### Scenario: Reject a high-total sale with no buyer reference
- GIVEN a sale whose computed total is S/700 or more
- WHEN the sale is recorded without a `documentType`/`documentNumber`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a malformed RUC or DNI
- WHEN a sale is recorded with `documentType: RUC` and a `documentNumber` that fails the modulo-11 check digit, or `documentType: DNI` with a `documentNumber` that isn't exactly 8 digits
- THEN the system MUST reject the request with a validation error, regardless of the sale's total

### Requirement: Sale Line Item Price Snapshot

Once a sale is recorded, each line item's `unitPrice` and `description` MUST remain fixed regardless of later changes to the referenced product's `salePrice`, `name`, or `description`.

#### Scenario: A later catalog price change does not affect a past sale
- GIVEN a completed sale with a line item at `unitPrice: 350`
- WHEN the referenced product's `salePrice` is later updated to `400`
- THEN the sale's line item still shows `unitPrice: 350`

### Requirement: List and View Sales

The system MUST allow listing sales, optionally filtered by status, and MUST allow retrieving a single sale's detail including its associated voucher's current status and, once `ACCEPTED`, its PDF/XML/CDR links.

#### Scenario: View a sale's detail with an accepted voucher
- GIVEN a completed sale whose voucher has been accepted by SUNAT
- WHEN the sale's detail is requested
- THEN the response includes the voucher's status `ACCEPTED` and its PDF/XML/CDR links

### Requirement: Void a Sale

The system MUST allow voiding a sale only when its voucher's status is `ACCEPTED`. Voiding MUST NOT delete the sale or voucher records; both transition to a cancelled/voided state, preserving the audit trail.

#### Scenario: Void a completed sale with an accepted voucher
- GIVEN a completed sale whose voucher status is `ACCEPTED`
- WHEN the sale is voided
- THEN the sale's status becomes `CANCELLED` and the voucher's status becomes `VOIDED`, with both rows still present

#### Scenario: Reject voiding a sale with a pending voucher
- GIVEN a completed sale whose voucher status is `PENDING`
- WHEN voiding that sale is attempted
- THEN the system MUST reject the request with a validation error
