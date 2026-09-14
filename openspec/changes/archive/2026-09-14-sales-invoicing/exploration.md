## Exploration: sales-invoicing

### Current State

The codebase has one bounded context so far: `catalog` (`com.storemanager.catalog`), split across the two Maven modules `application` (domain + ports + use cases, zero Spring dependency) and `infrastructure` (adapters: persistence, web, Spring config). Domain entities (`Category`, `Product`) are immutable, built via `createNew`/`reconstitute` factories, and enforce only their own structural invariants — cross-entity rules (name uniqueness, category existence) live in use cases via repository ports (`CategoryRepositoryPort`, `ProductRepositoryPort`). Use cases are plain Java classes wired as Spring beans by `UseCaseConfiguration` in `infrastructure`. No `Sales` or `Invoicing` bounded context exists yet.

Decision already made with the user (not yet formalized in a proposal): invoicing integrates through an OSE/PSE rather than a direct SUNAT SEE build — this exempts the taxpayer from SUNAT's mandatory homologación process entirely (verified against `cpe.sunat.gob.pe/sistema_emision/see_contribuyente`). NubeFacT is the chosen **reference** for this exploration (not a locked-in final choice) at their paid tier — S/70/month, up to 500 documents/month, REST API included — matching the user's ~500/month volume estimate.

### Two new bounded contexts under consideration

1. **Sales** (`com.storemanager.sales`) — records a completed sale: line items referencing existing `Product`s, quantities, unit price *snapshotted at sale time* (must not follow later catalog price changes), computed subtotal/tax/total, and a customer reference. A completed sale is the trigger event for invoicing.
2. **Invoicing** (`com.storemanager.invoicing`) — issues the legal voucher for a completed sale via an OSE. Owns voucher numbering (series/correlativo), the boleta-vs-factura determination rule (by buyer document type: DNI → boleta, RUC → factura — a SUNAT-wide rule, not OSE-specific), the persisted voucher record (status, hash, PDF/XML/CDR references), and the `InvoicingProvider` port.

Both fit as new packages inside the existing `application`/`infrastructure` modules, mirroring `catalog`'s layout — a package-level boundary, not a new Maven module; this project's size doesn't yet justify a third module.

### Port design sketch — `InvoicingProvider`

```java
public interface InvoicingProvider {
    IssuedVoucher issueBoleta(VoucherRequest request);
    IssuedVoucher issueFactura(VoucherRequest request);
    IssuedVoucher issueCreditNote(CreditNoteRequest request);
    VoucherStatus checkStatus(String voucherReference);
}
```

`VoucherRequest`/`IssuedVoucher` are our own domain shapes (series, correlativo, buyer document type/number, line items, totals; on the response side: acceptance flag, SUNAT description/code, hash, PDF/XML/CDR references) — never NubeFacT's literal JSON field names. `NubeFactInvoicingAdapter` (in `infrastructure`) is the only place that translates to/from NubeFacT's actual API contract, mirroring the existing `CategoryRepositoryPort` / `CategoryRepositoryAdapter` pattern already established in `catalog`. Swapping OSE later means a new adapter class, zero domain change.

### NubeFacT — reference evidence

Verified at a high level; exact JSON schema is gated behind their integration manual, not published in public search results:

- REST API, JSON or TXT payload — one call emits a document; NubeFacT generates the PDF/XML, signs it, submits to SUNAT, and returns the CDR. ([nubefact.com/integracion](https://www.nubefact.com/integracion), [ayuda.nubefact.com/preguntas/nubefact-api](https://ayuda.nubefact.com/preguntas/nubefact-api))
- Supports invoices (facturas), boletas, credit/debit notes, retention/perception vouchers, and shipment guides. ([nubefact.com/precios](https://www.nubefact.com/precios))
- Paid tier: S/70.00/month, up to 500 documents/month, includes API-REST access with example code in popular languages. ([nubefact.com/precios](https://www.nubefact.com/precios))
- Response is known to include an acceptance flag, a SUNAT description/code, a hash, and links to the generated PDF/XML/CDR — exact field names unconfirmed (see Open Questions).

### Open Questions (candidates for research/proposal)

1. **Exact NubeFacT request/response JSON schema** — not available from public search; needs their integration manual (likely requires a NubeFacT account or the PDF manual directly) to pin down field names for the adapter's mapping. Candidate for `sdd-research`.
2. **Boleta daily summary (resumen diario)** — SUNAT allows batching low-value boletas into a daily summary instead of real-time emission. Does day one need this, or is per-sale real-time emission acceptable at ~500/month? Affects whether `Invoicing` needs a batching job.
3. **Void/anulación flow** — SUNAT requires a formal "comunicación de baja" for a voided invoice/boleta after issuance, not a silent deletion. Needs a proposal-level scope decision.
4. **OSE outage / rejection handling** — if `InvoicingProvider` times out or NubeFacT rejects a request (e.g. malformed buyer data), does the sale still complete with a "pending invoice" state, or is invoicing synchronous-blocking on sale completion? A real architecture decision, not just error handling.
5. **Multi-tenant/certificate concerns** — none: NubeFacT holds the certificate on their side; single shop, single NubeFacT account. Confirmed non-issue at this project's scale.

### Risks

- SUNAT's electronic-invoicing validation rules are actively evolving (RS N.° 000031-2026-SUNAT postponed some validations to January 2027) — the domain model should treat "which fields SUNAT currently requires" as OSE/adapter-owned knowledge, never hardcoded into `Sales`/`Invoicing` use cases, so a regulatory change doesn't ripple into the domain.
- The ~500/month volume is the user's own rough estimate, not measured — worth confirming before committing to NubeFacT's paid tier over the free tier in the proposal.

### Ready for Proposal

Not yet. Question 1 (exact NubeFacT schema) is worth a short `sdd-research` pass first, since the adapter's contract depends on it; questions 2-4 are product-scope decisions for the user to confirm during proposal, not assumed here.
