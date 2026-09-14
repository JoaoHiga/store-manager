# Design: Buyer Identification Hardening

## Technical Approach

All format validation (DNI, RUC + check digit, CE/Pasaporte) and the S/ 700 "boleta simple" threshold are pure functions of data the `Sale` aggregate already owns (its own items → total, and the buyer fields passed to its constructor) — no repository access is needed for either. This means, unlike the proposal's original sketch, the whole rule set fits `Sale`'s existing "enforces only its own structural invariants" pattern (verified: `Sale`'s constructor already self-computes `total` from `items` before the current buyer-reference checks) rather than needing to live in `RecordSaleUseCase`. `RecordSaleUseCase` stays focused on the checks that genuinely need `ProductRepositoryPort` (existence, active, stock) — consistent with the separation already established in `sales-invoicing`.

**Second real gap found while designing this (not by the user, by tracing the actual call chain):** `Voucher`'s own constructor independently re-requires non-blank `buyerDocumentType`/`buyerDocumentNumber` — even after `Sale` accepts a null buyer reference, `IssueVoucherForSaleUseCase` would still throw when building the `Voucher` for a boleta-simple sale. Both `sales.buyer_document_type`/`buyer_document_number` AND `vouchers.buyer_document_type`/`buyer_document_number` are `NOT NULL` in the database (`V3`, `V4`). This change touches `Voucher` and one more migration, not just `Sale`.

## Architecture Decisions

| Decision | Alternative(s) | Choice | Rationale |
|---|---|---|---|
| Where the S/700 + format rules live | `RecordSaleUseCase` (per the proposal's original sketch) | Inside `Sale`'s (and `Voucher`'s) own constructor, via a stateless `BuyerDocumentValidator` helper | Both total and the buyer fields are already owned by the aggregate at construction time — no I/O needed. Matches the existing pattern (`Product`/`Sale` validate their own structural invariants; only cross-entity/repository-dependent checks live in use cases) more closely than the proposal did |
| RUC check-digit algorithm | Trust a validation library | Implement directly (11 lines, no new dependency) per research.md's sourced modulo-11 algorithm | Small, pure, fully specified; a dependency would be overkill and adds a supply-chain surface for something this contained |
| "No document" representation | A separate `VoucherType.BOLETA_SIMPLE` enum value | Keep `VoucherType.BOLETA`; `buyerDocumentType`/`Number` are `null` | `VoucherType` should reflect the actual comprobante SUNAT issues (boleta vs factura); "simple" is just a boleta with no buyer identified, not a different document type — confirmed `IssueVoucherForSaleUseCase.determineType`'s existing `"RUC".equalsIgnoreCase(buyerDocumentType)` is already null-safe (a String literal's `.equalsIgnoreCase(null)` returns `false`, never NPEs), so no code change needed there beyond `Voucher`/`Sale` accepting the null |
| CE/Pasaporte validation strictness | A specific fixed-length regex per type | Light: non-blank, length 5-20 | research.md found no authoritative fixed format for either — a wrong strict regex would reject real documents, worse than under-validating |

## Data Flow

    POST /api/sales  (total will be < S/700, no buyerDocumentType/Number in the request)
        │
        ▼
    RecordSaleUseCase — validates products/stock (unchanged), builds Sale
        │
        ▼
    Sale constructor — computes total from items FIRST, then:
        buyerDocumentType/Number both blank?
          ├─ total < 700  → allowed, stored as null/null
          └─ total >= 700 → IllegalArgumentException (BuyerDocumentValidator)
        buyerDocumentType present?
          └─ BuyerDocumentValidator.validate(type, number) — DNI/RUC/CE/Pasaporte format
        │
        ▼
    Sale persisted (buyer_document_type/number nullable, V6 migration)
        │
        ▼
    IssueVoucherForSaleUseCase — determineType(null) → BOLETA (unchanged logic, already null-safe)
        │
        ▼
    Voucher.createNew(..., null, null, ...) — constructor relaxed to accept null (matches Sale)
        │
        ▼
    NubeFactInvoicingAdapter.mapDocumentType(null) → "-" (NubeFacT's varios/menor-cuantía code,
    per research.md) — verified already null-safe (the method's existing null check predates this
    change, added in sales-invoicing's PR3, commit 4d5a0d8); no production fix needed here, kept
    as a regression test plus a doc comment tying the behavior to boleta-simple

## File Changes

| File | Action | Description |
|---|---|---|
| `application/.../sales/domain/BuyerDocumentValidator.java` | Create | Stateless: `validate(type, number, total)` — DNI/RUC/CE/Pasaporte rules + S/700 threshold; RUC modulo-11 check digit |
| `application/.../sales/domain/Sale.java` | Modify | Computes `total` before the buyer-reference check; delegates to `BuyerDocumentValidator` instead of the current unconditional non-blank check |
| `application/.../invoicing/domain/Voucher.java` | Modify | Constructor accepts `null` `buyerDocumentType`/`buyerDocumentNumber` (drops the two non-blank checks) |
| `infrastructure/.../invoicing/infrastructure/nubefact/NubeFactInvoicingAdapter.java` | Modify (doc-only) | `mapDocumentType` was already null-safe (predates this change); adds a doc comment tying the existing `null` → `"-"` branch to boleta-simple, plus a regression test |
| `infrastructure/.../sales/infrastructure/web/RecordSaleRequest.java` | Modify | Buyer fields lose `@NotBlank` (become optional; format is validated domain-side, not at the DTO layer, so the real error message comes from `BuyerDocumentValidator`) |
| `infrastructure/.../sales/infrastructure/persistence/SaleJpaEntity.java` | Modify | `nullable = true` on both buyer columns |
| `infrastructure/.../invoicing/infrastructure/persistence/VoucherJpaEntity.java` | Modify | `nullable = true` on both buyer columns |
| `infrastructure/src/main/resources/db/migration/V6__make_buyer_document_nullable.sql` | Create | `ALTER TABLE sales ALTER COLUMN buyer_document_type DROP NOT NULL`, same for `buyer_document_number`, and the same two `ALTER`s on `vouchers` |
| `frontend/src/pages/RecordSalePage.jsx` | Modify | `BUYER_DOCUMENT_TYPES` → `['DNI', 'RUC', 'CE', 'PASAPORTE']`; a "Sin documento" choice, contextually suggested under S/700; inline format hints |

## Interfaces / Contracts

No new endpoints. Behavior changes on the existing one:
- `POST /api/sales`: `buyerDocumentType`/`buyerDocumentNumber` are now optional in the request body; when present, they're format-validated (400 on a malformed DNI/RUC, or a CE/Pasaporte that's blank/too short/too long); when absent, a `400` is still returned if the computed total is S/700 or more.

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit | `BuyerDocumentValidator` | Real RUC (`20131312955` from research.md) accepted; a RUC with a flipped check digit rejected; RUC with an invalid prefix (e.g. `99...`) rejected; DNI of 7/9 digits or containing letters rejected; DNI of exactly 8 digits accepted; CE/Pasaporte blank rejected, reasonable value accepted |
| Unit | `Sale` | Total exactly S/700 with no buyer ref → rejected; S/699.99 with no buyer ref → accepted; total >= S/700 with a valid DNI → accepted |
| Unit | `Voucher` | `createNew`/`reconstitute` accept `null` buyer fields without throwing |
| Unit | `IssueVoucherForSaleUseCase` | A sale with no buyer document still resolves to `BOLETA` (regression proof the existing null-safe `equalsIgnoreCase` logic wasn't accidentally broken) |
| Unit | `NubeFactInvoicingAdapter` | `mapDocumentType(null)` returns `"-"`, doesn't throw |
| Integration | `SaleRepositoryAdapter`/`VoucherRepositoryAdapter` (`@DataJpaTest`) | A sale/voucher with `null` buyer fields round-trips through the real (H2) database after `V6` |
| Integration | `SaleController` (MockMvc) | `POST /api/sales` without buyer fields succeeds for a low-total sale, fails for a high-total one; a malformed RUC/DNI returns 400 |

## Threat Matrix

N/A — no new routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary; this only adds validation logic and relaxes two `NOT NULL` constraints.

## Migration / Rollout

`V6__make_buyer_document_nullable.sql` relaxes `NOT NULL` on 4 columns across `sales` and `vouchers` — no existing row has `NULL` in them today (every sale so far had a required buyer reference), so this is a safe, reversible schema change with no data migration needed.

## Open Questions

- NubeFacT's exact field/behavior for a boleta with no buyer identified isn't confirmed against their real manual (research.md flagged `-` as the likely `cliente_tipo_de_documento` code, inferred from the general SUNAT catalog, not directly verified against NubeFacT's own docs) — same caveat as `sales-invoicing`'s open items: verify against real sandbox access when available, not blocking for now since `FakeInvoicingProvider` is what tests actually exercise.
- The exact CE/Pasaporte bounds (5-20 chars) are a reasonable default, not a sourced rule — flagged in case real-world documents fall outside it.
