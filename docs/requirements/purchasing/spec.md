# Purchasing Specification

## Purpose

Register the suppliers the store buys merchandise from and the purchases made from them. A purchase is how stock enters the store: recording one increments each product's stock and recomputes its cost price, and every purchase is traceable to a supplier and the supplier's invoice for accounting. This domain sits next to `product-catalog`, whose stock and cost price it updates.

## Requirements

### Requirement: Create a Supplier

The system MUST allow creating a supplier with a `name` (required, non-blank) and a `ruc` (required), plus optional contact data: `phone`, `email`, and `address`. The `ruc` MUST be a valid Peruvian RUC: 11 numeric digits, a prefix in `{10, 15, 17, 20}`, and a valid modulo-11 check digit (the same rule used for buyer RUCs in `sales`). When present, `email` MUST be a well-formed email address. A new supplier MUST start active.

The `ruc` MUST be unique across all suppliers, active or inactive. Creating a supplier with a `ruc` that already exists MUST be rejected as a conflict, including when two creations with the same `ruc` happen at the same moment.

#### Scenario: Create a valid supplier
- WHEN a supplier is created with `name: "Distribuidora Lima"`, `ruc: "20131312955"`, and `phone: "987654321"`
- THEN the supplier is persisted with an id, `active: true`, and the given data; `email` and `address` are `null`

#### Scenario: Reject a blank name
- WHEN a supplier is created with a blank `name`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a malformed RUC
- WHEN a supplier is created with a `ruc` that isn't 11 digits, has an unsupported prefix, or fails the modulo-11 check digit (e.g. `"20131312956"`)
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a duplicate RUC
- GIVEN a supplier with `ruc: "20131312955"` exists, active or inactive
- WHEN another supplier is created with `ruc: "20131312955"`
- THEN the system MUST reject the request with a conflict error, and no second supplier is persisted

### Requirement: List Suppliers

The system MUST allow listing suppliers ordered by `name`. Without a filter it MUST return every supplier, active and inactive; with an `active` filter it MUST return only the suppliers in that state.

#### Scenario: List every supplier
- GIVEN an active supplier "Zeta Importaciones" and an inactive supplier "Alfa Accesorios"
- WHEN suppliers are listed without a filter
- THEN both are returned, "Alfa Accesorios" first, each with its `active` state

#### Scenario: List only active suppliers
- GIVEN an active supplier "Zeta Importaciones" and an inactive supplier "Alfa Accesorios"
- WHEN suppliers are listed with `active: true`
- THEN only "Zeta Importaciones" is returned

### Requirement: Deactivate and Reactivate a Supplier

The system MUST allow deactivating a supplier and reactivating an inactive one. Suppliers are never deleted, so past purchases always keep their supplier. An inactive supplier MUST NOT be used in a new purchase. Both operations MUST be idempotent. Deactivating or reactivating a nonexistent supplier MUST be rejected as not found.

#### Scenario: Deactivate a supplier
- GIVEN an active supplier
- WHEN the supplier is deactivated
- THEN the supplier is persisted with `active: false` and still appears when listing every supplier

#### Scenario: Reactivate a supplier
- GIVEN an inactive supplier
- WHEN the supplier is reactivated
- THEN the supplier is persisted with `active: true`

#### Scenario: Reject an unknown supplier
- WHEN a supplier id that does not exist is deactivated or reactivated
- THEN the system MUST reject the request with a not-found error

#### Scenario: An inactive supplier cannot be used in a purchase
- GIVEN an inactive supplier
- WHEN a purchase is recorded for that supplier
- THEN the system MUST reject the request with a validation error, and no stock or cost price changes

### Requirement: Record a Purchase

The system MUST allow recording a purchase with a `supplierId`, the supplier's `invoiceNumber`, a `purchaseDate`, and one or more lines, each with a `productId`, a `quantity`, and a `unitCost`.

- The supplier MUST exist (otherwise not found) and MUST be active (otherwise a validation error).
- `invoiceNumber` is required; surrounding whitespace is removed first, and what remains MUST be non-blank and at most 30 characters (whitespace alone does not make it too long). The pair (supplier, invoice number) MUST be unique: registering the same supplier invoice twice MUST be rejected as a conflict, including when both are recorded at the same moment. Different suppliers may use the same invoice number.
- `purchaseDate` is required and MUST NOT be after the current date in the store's time zone (`America/Lima`).
- Every line MUST be present (a `null` line is a validation error). Every line's `quantity` MUST be a whole number greater than zero (a fractional quantity MUST be rejected, never truncated) and its `unitCost` MUST be zero or greater, with at most 10 integer digits and 2 decimals. The same product MUST NOT appear on two lines of one purchase.
- Every referenced product MUST exist (otherwise not found) and MUST be active (otherwise a validation error), the same rule as for sales.
- A line's `subtotal` is `quantity * unitCost` and the purchase `total` is the sum of the subtotals, both with 2 decimals. The supplier's name and each product's name are snapshotted onto the purchase when it is recorded.

Recording a purchase MUST, for every line, add `quantity` to the product's `stockQuantity` and recompute its `costPrice` as the weighted average `(stock * costPrice + quantity * unitCost) / (stock + quantity)`, rounded half-up to 2 decimals, where `stock` and `costPrice` are the product's values right before this purchase. When the product has no `costPrice` yet or its stock is 0, the new `costPrice` is the line's `unitCost`.

The purchase and every stock and cost update MUST be applied together or not at all: if any line is rejected, nothing is recorded and no product changes. A sale or another purchase of the same product recorded at the same moment MUST NOT be lost: each change applies on top of the other.

#### Scenario: Record a valid purchase
- GIVEN an active supplier "Distribuidora Lima" and an active product "iPhone 12" with `stockQuantity: 3` and `costPrice: 300.00`, and an active product "Funda" with `stockQuantity: 0` and no `costPrice`
- WHEN a purchase is recorded with `invoiceNumber: "F001-123"`, today's date, 2 x "iPhone 12" at `unitCost: 330.00` and 10 x "Funda" at `unitCost: 4.50`
- THEN the purchase is persisted with `total: 705.00` and both lines with their product names
- AND "iPhone 12" has `stockQuantity: 5` and `costPrice: 312.00`, and "Funda" has `stockQuantity: 10` and `costPrice: 4.50`

#### Scenario: Round the weighted average half-up
- GIVEN an active product with `stockQuantity: 1` and `costPrice: 1.00`
- WHEN a purchase of 1 unit at `unitCost: 1.01` is recorded
- THEN the product's `costPrice` is `1.01` (the exact average is 1.005)

#### Scenario: Reject a duplicate supplier invoice
- GIVEN a purchase from supplier "Distribuidora Lima" with `invoiceNumber: "F001-123"`
- WHEN another purchase from the same supplier is recorded with `invoiceNumber: "F001-123"`
- THEN the system MUST reject the request with a conflict error, and no stock or cost price changes

#### Scenario: Reject an invalid purchase
- WHEN a purchase is recorded with a future date, a blank invoice number, an invoice number longer than 30 characters after removing surrounding whitespace, no lines, a `null` line, a line with `quantity: 0`, a fractional `quantity` (`1.5`), a negative `unitCost` or one with 3 decimals, or the same product on two lines
- THEN the system MUST reject the request with a validation error, and no stock or cost price changes

#### Scenario: Reject an inactive or unknown product
- WHEN a purchase is recorded with a line referencing an inactive product
- THEN the system MUST reject the request with a validation error, and no part of the purchase is recorded
- AND a line referencing a product that does not exist MUST be rejected with a not-found error

#### Scenario: A concurrent sale is never lost
- GIVEN an active product with `stockQuantity: 10` and `costPrice: 100.00`
- WHEN a sale of 1 unit and a purchase of 2 units at `unitCost: 130.00` are recorded at the same moment
- THEN the product ends with `stockQuantity: 11`
- AND when the sale is applied first, the purchase averages over the remaining 9 units: `costPrice: 105.45`

### Requirement: List and View Purchases

The system MUST allow listing purchases newest first (by purchase date, then most recently recorded), each with its id, supplier id and name, invoice number, purchase date, total, and number of lines. The system MUST allow viewing one purchase with its lines (product id and name, quantity, unit cost, subtotal). Viewing a purchase that does not exist MUST be rejected as not found.

#### Scenario: List purchases newest first
- GIVEN purchases dated two days ago, today, and yesterday
- WHEN purchases are listed
- THEN they are returned in the order today, yesterday, two days ago, each with its line count
