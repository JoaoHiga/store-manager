## Research: explicit-voucher-type-selection — who decides boleta vs. factura

### The rule

- **Factura**: may only be issued to a buyer who provides a RUC. A RUC is a necessary condition, not a sufficient one.
- **Boleta**: issued to a "consumidor final" — does not require RUC, and **a buyer who has a RUC can still receive a boleta** if they don't request/need a factura.
- The actual determining factor is what the buyer needs: a factura lets the buyer use the IGV as crédito fiscal and deduct the expense for Impuesto a la Renta; a boleta does neither. A RUC holder buying for personal, non-business use commonly just takes a boleta.

> "La decisión no es opcional: depende de quién es el cliente y qué necesita. Si el cliente tiene RUC y pide factura, emites factura; si no, boleta."

Sources: [Acepta Perú — ¿Diferencia entre boleta y factura: ¿cuál emitir según la SUNAT?](https://acepta.com/pe/blog/2026/07/14/cual-es-la-diferencia-entre-boleta-y-factura/), [NubeFacT — Boleta, factura o ticket POS: cuál emitir en tu negocio según SUNAT](https://www.nubefact.com/blog/facturacion-electronica/boleta-factura-o-ticket-pos-cual-emitir-en-tu-negocio-segun-sunat)

### Implication for this system

The current `"RUC".equalsIgnoreCase(buyerDocumentType) ? FACTURA : BOLETA` derivation is wrong: it forces a factura on every RUC buyer with no way to choose otherwise, and it's directionally one-way-safe only (a RUC buyer CAN get a boleta; a non-RUC buyer can NEVER get a factura — that direction was already correctly impossible, since factura requires RUC). The fix is an explicit choice at sale-recording time, constrained by one hard rule: **factura requires `buyerDocumentType == RUC`; boleta has no such constraint.**

### Status

Done. The determining rule (buyer's need, not document type alone) is sourced from two independent, mutually corroborating sources. No further legal research needed — this doesn't touch RUC/DNI format rules (already covered by `buyer-identification-hardening`'s research.md) or NubeFacT's API shape (already covered by `sales-invoicing`'s research.md — `issueBoleta`/`issueFactura` are already separate provider calls, unaffected by this change).
