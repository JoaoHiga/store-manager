# Delta for product-catalog

## MODIFIED Requirements

### Requirement: Category Creation and Listing

The system MUST allow creating a category with a unique, non-empty `name` (at most 120 characters) and an optional `description` (at most 500 characters). The system MUST allow listing all categories. The system MUST reject a duplicate `name` with a `409`-equivalent validation error even under concurrent creation, never an unhandled server error.
(Previously: no explicit length bound on `name` or `description`, and concurrent duplicate creation could reach the database and raise an unhandled error instead of the validation-error path.)

#### Scenario: Create a category with a valid unique name
- GIVEN no category named "Celulares" exists
- WHEN a category is created with `name: "Celulares"`
- THEN the category is persisted and returned with a generated `id`

#### Scenario: Reject a duplicate category name
- GIVEN a category named "Celulares" already exists
- WHEN a category is created with `name: "Celulares"`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a concurrent duplicate category name without a server error
- GIVEN two requests are creating a category named "Celulares" at the same time and neither has committed yet
- WHEN both requests reach persistence
- THEN exactly one succeeds and the other is rejected with the same validation error as a sequential duplicate — never an unhandled server error

#### Scenario: Reject an over-length category name
- WHEN a category is created with a `name` longer than 120 characters
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject an over-length category description
- WHEN a category is created with a `description` longer than 500 characters
- THEN the system MUST reject the request with a validation error

### Requirement: Product Creation

The system MUST allow creating a product with `name` (at most 160 characters), `categoryId`, `salePrice` (> 0), and `stockQuantity` (>= 0) required; `description` (at most 1000 characters), `brand` (at most 80 characters), and `costPrice` (>= 0 when present) are optional. The system MUST auto-generate a unique `sku` for every created product; `sku` MUST NOT be accepted as caller input in this change.
(Previously: `costPrice` had no lower bound, and `name`/`brand`/`description` had no upper bound — all could reach the database unvalidated.)

#### Scenario: Create a product with valid data
- GIVEN category "Celulares" exists
- WHEN a product is created with `name: "iPhone 12"`, `categoryId` of "Celulares", `salePrice: 350`, `stockQuantity: 3`
- THEN the product is persisted with a system-generated unique `sku` and `active: true`

#### Scenario: Reject a product referencing a nonexistent category
- GIVEN no category with id 999 exists
- WHEN a product is created with `categoryId: 999`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a negative stock quantity
- WHEN a product is created with `stockQuantity: -1`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject a negative cost price
- WHEN a product is created with `costPrice: -1`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject an over-length name, brand, or description
- WHEN a product is created with a `name` longer than 160 characters, a `brand` longer than 80 characters, or a `description` longer than 1000 characters
- THEN the system MUST reject the request with a validation error

### Requirement: Product Update

The system MUST allow editing an existing product's `name` (at most 160 characters), `description` (at most 1000 characters), `brand` (at most 80 characters), `categoryId`, `costPrice` (>= 0 when present), `salePrice`, and `stockQuantity`. The system MUST NOT allow `sku` to be modified through this capability.
(Previously: `costPrice` had no lower bound, and `name`/`brand`/`description` had no upper bound, on update.)

#### Scenario: Update a product's sale price and stock
- GIVEN an active product with `stockQuantity: 3`
- WHEN it is updated with `salePrice: 320`, `stockQuantity: 5`
- THEN the product reflects the new values

#### Scenario: Reject an update that sets a negative stock quantity
- WHEN an existing product is updated with `stockQuantity: -2`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject an update that sets a negative cost price
- WHEN an existing product is updated with `costPrice: -1`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject an update with an over-length name, brand, or description
- WHEN an existing product is updated with a `name` longer than 160 characters, a `brand` longer than 80 characters, or a `description` longer than 1000 characters
- THEN the system MUST reject the request with a validation error
