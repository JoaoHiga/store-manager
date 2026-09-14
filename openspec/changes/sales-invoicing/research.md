## Research: sales-invoicing — NubeFacT API JSON schema

### Source
User-provided NubeFacT integration manual ("NUBEFACT DOC API JSON V1"), confirmed against the doc's key-fields reference section. Resolves Open Question 1 from `exploration.md`.

### Request fields

**`tipo_de_comprobante`** (document type):
| Value | Meaning |
|---|---|
| 1 | Invoice (Factura) |
| 2 | Sales receipt (Boleta) |
| 3 | Credit note |
| 4 | Debit note |

**Series & correlativo**: series is exactly 4 characters, starting with `F` for facturas or `B` for boletas. The correlativo has no leading zeros, 1-8 digits.

**`cliente_tipo_de_documento`** (buyer document type): `6`=RUC, `1`=DNI, `-`=varios/menor cuantía, `4`=carnet de extranjería, `7`=pasaporte, `A`=cédula diplomática, `0`=no domiciliado sin RUC, `G`=salvoconducto.

**Cabecera (header monetary fields)**: `total_gravada` (taxable base), `total_igv` (VAT at standard rate), `total_inafecta` (non-taxable), `total_exonerada` (exempt), `total` (grand total), `porcentaje_de_igv` (typically 18.00).

**`items[]` (line items)**: `unidad_de_medida` (`NIU` for goods, `ZZ` for services), `descripcion` (max 250 chars), `cantidad` (up to 10 decimals), `valor_unitario` (pre-tax unit price), `precio_unitario` (tax-inclusive unit price), `tipo_de_igv` (tax classification code, 1-20), `igv` (line VAT), `total` (line total).

### Response fields
- `enlace_del_pdf`, `enlace_del_xml`, `enlace_del_cdr` — document/CDR URLs.
- `codigo_hash` — hash for custom printing.
- `cadena_para_codigo_qr` — QR string, composed of RUC | type | series | number | IGV | total | date | buyer-doc-type | buyer-number | hash.
- `aceptada_por_sunat` — boolean acceptance flag.

### Impact on the `InvoicingProvider` port design (from exploration.md)

Confirms the port's domain shapes can stay OSE-agnostic:
- `VoucherRequest` needs: document type (mapped from our own `Boleta`/`Factura` enum → NubeFacT's `1`/`2` in the adapter), series/correlativo (owned by our `Invoicing` domain, not NubeFacT — NubeFacT accepts whatever series/correlativo we send, it does not assign it), buyer document type/number (our own enum → NubeFacT's code table, mapped in the adapter only), line items (product ref, description, quantity, unit price, tax classification), and computed totals.
- `IssuedVoucher` needs: acceptance flag, SUNAT description/code (if rejected), hash, QR string, and PDF/XML/CDR references — enough to persist and to print/display a compliant voucher without re-deriving anything from NubeFacT's response shape elsewhere in the domain.
- `tipo_de_igv` (tax classification, 1-20) is NubeFacT/SUNAT catalog knowledge — confirms this belongs in the adapter's mapping table, not in `Sales`/`Invoicing` domain logic (per the exploration's risk note: OSE-owned knowledge must not leak into the domain).

### Remaining open questions (unchanged from exploration.md)
Resumen diario (batching), void/anulación flow, and OSE outage/rejection handling are still product-scope decisions for the proposal — this research pass only closed the schema question.

### Status
Done — Open Question 1 resolved. No further research needed before proposal.
