# Purchasing Specification

## Purpose

Register the suppliers the store buys merchandise from, so every purchase (stock entry) can be traced to a supplier for accounting. This domain sits next to `product-catalog`; recording purchases against suppliers is specified separately as it is built.

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

The system MUST allow deactivating a supplier and reactivating an inactive one. Suppliers are never deleted, so past purchases always keep their supplier. Both operations MUST be idempotent. Deactivating or reactivating a nonexistent supplier MUST be rejected as not found.

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
