## Exploration: explicit-voucher-type-selection

Found by the user while manually clicking through the app after `buyer-identification-hardening` landed on `main`: "veo boletas pero no facturas". Investigation surfaced two distinct, unrelated problems.

### Problem 1 (real bug, confirmed): `Sale.reconstitute` re-validates business rules that changed after the row was written

`Sale`'s private constructor (`application/.../sales/domain/Sale.java:25`) calls `BuyerDocumentValidator.validate(buyerDocumentType, buyerDocumentNumber, computedTotal)` unconditionally — both `createNew` and `reconstitute` share it. A sale row written **before** `buyer-identification-hardening` shipped (`sales.id = 1`, `buyer_document_type = 'RUC'`, `buyer_document_number = '00165490fddfgdsgdf'` — clearly not a valid RUC, but valid under the old "just non-blank" rule) now throws `IllegalArgumentException: RUC must be exactly 11 numeric digits` every time it's loaded, confirmed directly:

```
$ curl http://localhost:8080/api/sales
{"message":"RUC must be exactly 11 numeric digits"}
$ curl http://localhost:8080/api/sales/1
{"message":"RUC must be exactly 11 numeric digits"}
```

Both `GET /api/sales` and `GET /api/sales/{id}` are broken as long as this row exists. Confirmed via direct DB read (`psql`) that it's the only row in `sales` right now.

### Problem 2 (domain-rule correction, user-flagged): voucher type is wrongly auto-derived from buyer document type

Current code (`IssueVoucherForSaleUseCase.determineType`, unchanged since `sales-invoicing`):
```java
private VoucherType determineType(String buyerDocumentType) {
    return "RUC".equalsIgnoreCase(buyerDocumentType) ? VoucherType.FACTURA : VoucherType.BOLETA;
}
```
This assumes RUC always implies factura. Verified against real SUNAT rules (see research.md): **factura requires a RUC, but a RUC holder can still request a boleta** — the choice belongs to the buyer/seller at sale time, not a mechanical derivation from document type. The system has no way today to record that choice; `RecordSaleRequest`/`RecordSaleUseCase`/`SaleController` never pass anything but `buyerDocumentType`/`buyerDocumentNumber`.

### Call chain (confirmed via CodeGraph)

`SaleController.record` → `RecordSaleUseCase.execute(lines, buyerDocumentType, buyerDocumentNumber)` → `Sale.createNew(...)` → saved → `IssueVoucherForSaleUseCase.execute(sale)` → `determineType(sale.getBuyerDocumentType())` → `Voucher.createNew(saleId, type, ...)` → `InvoicingProvider.issueFactura`/`issueBoleta`.

`NubeFactInvoicingAdapter` already has separate `issueBoleta`/`issueFactura` methods (mapping to distinct `TIPO_FACTURA`/`TIPO_BOLETA`/series constants) driven purely by which one is called — no adapter change needed once the caller passes the right one explicitly.

`Voucher.type` is fixed at creation and never re-derived on retry (`RetryPendingVoucherUseCase` retries issuance of the same persisted type) — so the fix only needs to change how the type is decided at record-sale time, nothing about retry.
