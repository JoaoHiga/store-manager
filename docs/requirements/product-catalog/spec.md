# Product Catalog Specification

## Purpose

Manage the shop's categories and products: create, list, edit, filter, and deactivate them, with basic stock quantity and cost price tracking. After creation, stock goes down through sales (`sales`) and up through purchases (`purchasing`), and each purchase also recomputes the cost price as a weighted average. This is the foundational catalog capability the rest of the inventory app builds on.

## Requirements

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

### Requirement: Product Listing and Filtering

The system MUST list products, MUST support filtering by `categoryId`, and MUST exclude inactive (`active: false`) products from the default listing.

#### Scenario: List active products only by default
- GIVEN one active product and one deactivated product exist
- WHEN products are listed without filters
- THEN only the active product is returned

#### Scenario: Filter products by category
- GIVEN products exist in categories "Celulares" and "Accesorios"
- WHEN products are listed filtered by the "Celulares" category
- THEN only products in "Celulares" are returned

### Requirement: Product Update

The system MUST allow editing an existing product's `name` (at most 160 characters), `description` (at most 1000 characters), `brand` (at most 80 characters), `categoryId`, `costPrice` (>= 0 when present), and `salePrice`. The system MUST NOT allow `sku` or `stockQuantity` to be modified through this capability: stock is set only as the initial stock at creation and afterwards changes only through stock operations (sales, purchases). A `stockQuantity` sent on update MUST be ignored, and saving an edit MUST NOT undo a stock change made concurrently (for example, a sale recorded while the edit form was open). `costPrice` is also recomputed by every recorded purchase as a weighted average of the existing stock and the purchased units (see `purchasing`); an edit sets it to the given value.
(Previously: `costPrice` had no lower bound, and `name`/`brand`/`description` had no upper bound, on update. `stockQuantity` was editable as an absolute value, which silently undid concurrent sales.)

#### Scenario: Update a product's sale price
- GIVEN an active product with `salePrice: 350`
- WHEN it is updated with `salePrice: 320`
- THEN the product reflects the new sale price

#### Scenario: Updating a product does not change its stock
- GIVEN an active product with `stockQuantity: 3`
- AND a sale of 1 unit is recorded after the edit form was loaded
- WHEN the product is updated with `salePrice: 320` (with or without a `stockQuantity` in the request)
- THEN the product's `stockQuantity` is 2

#### Scenario: Reject an update that sets a negative cost price
- WHEN an existing product is updated with `costPrice: -1`
- THEN the system MUST reject the request with a validation error

#### Scenario: Reject an update with an over-length name, brand, or description
- WHEN an existing product is updated with a `name` longer than 160 characters, a `brand` longer than 80 characters, or a `description` longer than 1000 characters
- THEN the system MUST reject the request with a validation error
### Requirement: Product Reactivation

The system MUST allow reactivating a deactivated product (set `active: true`). The system MUST allow listing inactive products so one can be found to reactivate.

#### Scenario: Reactivate an inactive product
- GIVEN a product with `active: false`
- WHEN it is reactivated
- THEN `active` becomes `true` and the product reappears in the default listing

#### Scenario: Reactivating an already-active product is idempotent
- GIVEN a product with `active: true`
- WHEN it is reactivated again
- THEN the system MUST return success without error and `active` remains `true`

### Requirement: Product Soft Delete

The system MUST deactivate a product (set `active: false`) instead of physically deleting its row, so future capabilities that reference historical products remain valid.

#### Scenario: Deactivate an active product
- GIVEN an active product
- WHEN it is deactivated
- THEN `active` becomes `false` and the product no longer appears in the default listing

#### Scenario: Deactivating an already-inactive product is idempotent
- GIVEN a product with `active: false`
- WHEN it is deactivated again
- THEN the system MUST return success without error and `active` remains `false`
