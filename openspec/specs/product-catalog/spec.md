# Product Catalog Specification

## Purpose

Manage the shop's categories and products: create, list, edit, filter, and deactivate them, with basic stock quantity tracking. This is the foundational catalog capability the rest of the inventory app builds on.

## Requirements

### Requirement: Category Creation and Listing

The system MUST allow creating a category with a unique, non-empty `name` and an optional `description`. The system MUST allow listing all categories.

#### Scenario: Create a category with a valid unique name
- GIVEN no category named "Celulares" exists
- WHEN a category is created with `name: "Celulares"`
- THEN the category is persisted and returned with a generated `id`

#### Scenario: Reject a duplicate category name
- GIVEN a category named "Celulares" already exists
- WHEN a category is created with `name: "Celulares"`
- THEN the system MUST reject the request with a validation error

### Requirement: Product Creation

The system MUST allow creating a product with `name`, `categoryId`, `salePrice` (> 0), and `stockQuantity` (>= 0) required; `description`, `brand`, and `costPrice` are optional. The system MUST auto-generate a unique `sku` for every created product; `sku` MUST NOT be accepted as caller input in this change.

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

The system MUST allow editing an existing product's `name`, `description`, `brand`, `categoryId`, `costPrice`, `salePrice`, and `stockQuantity`. The system MUST NOT allow `sku` to be modified through this capability.

#### Scenario: Update a product's sale price and stock
- GIVEN an active product with `stockQuantity: 3`
- WHEN it is updated with `salePrice: 320`, `stockQuantity: 5`
- THEN the product reflects the new values

#### Scenario: Reject an update that sets a negative stock quantity
- WHEN an existing product is updated with `stockQuantity: -2`
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
