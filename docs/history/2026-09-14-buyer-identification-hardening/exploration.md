## Exploration: buyer-identification-hardening

### Current State (confirmed in code)
- `RecordSaleRequest.buyerDocumentType`/`buyerDocumentNumber`: only `@NotBlank` — zero format validation anywhere in the stack (DTO, domain, or frontend).
- `Sale`'s constructor only checks non-blank, same as above.
- `frontend/src/pages/RecordSalePage.jsx`: `BUYER_DOCUMENT_TYPES = ['DNI', 'RUC']` — hardcoded, no Carnet de Extranjería, Pasaporte, or "no document" option.
- `IssueVoucherForSaleUseCase.determineType`: `"RUC".equalsIgnoreCase(...) → FACTURA`, everything else → `BOLETA` — a buyer with NO document at all currently isn't representable (buyer reference is required, non-blank, on every sale per the existing `sales` spec).

Found by the user manually clicking through the running app (task 5.5 of `sales-invoicing`) — real gap, not a regression: the archived `sales-invoicing` change's spec never defined format validation or the "boleta simple" (no-buyer-ID) case at all.

### Researched facts (verified this session, sources below — not from memory)
- **Boleta simple threshold: S/ 700.00.** Below that amount, the buyer's document is optional (can be issued to "Clientes Varios" / no document at all, unless the buyer specifically asks to be identified). At or above S/ 700.00, SUNAT requires full identification: names + document (DNI, Carnet de Extranjería, or Pasaporte). [invyperu.com](https://www.invyperu.com/blog/como-emitir-boleta-electronica-peru), [facturasmype.com](https://facturasmype.com/errores-comunes-en-facturacion-sunat-2026/)
- **DNI**: exactly 8 numeric digits. [orientacion.sunat.gob.pe manual](https://orientacion.sunat.gob.pe/sites/default/files/inline-files/App_Manual_de_Usuario_FV3800_Parte2-15-38.pdf)
- **RUC**: 11 digits. First two digits MUST be one of `10`, `15`, `17`, `20`. Last digit is a check digit computed via a fixed modulo-11 algorithm: multiply the first 10 digits by weights `5,4,3,2,7,6,5,4,3,2` respectively, sum the products, check digit = `11 - (sum mod 11)`, except a result of `10` maps to `0` and `11` maps to `1`. [digitoverificador.com](https://www.digitoverificador.com/es/blog/digito-verificador-ruc-peru), corroborated by a worked example against SUNAT's own RUC (`20131312955`).
- **Carnet de Extranjería (CE)**: SUNAT catalog 06 type code `4`. No single confirmed fixed-length numeric format from an authoritative source in this pass (unlike DNI/RUC) — CE numbers vary in length/format across issuance eras. Treat as "non-empty, reasonable length" rather than a strict digit-count regex, to avoid rejecting real documents.
- **Pasaporte**: catalog type code `7`, alphanumeric, no fixed SUNAT-mandated format found.
- research.md (the archived `sales-invoicing` research) already confirmed NubeFacT's `cliente_tipo_de_documento` codes: `6`=RUC, `1`=DNI, `4`=Carnet de Extranjería, `7`=Pasaporte, `A`=Cédula Diplomática, `0`=No domiciliado, `G`=Salvoconducto, `-`=Varios/menor cuantía (this last one is likely NubeFacT's own code for the "boleta simple, no document" case — worth confirming against the actual manual during design, not assumed here).

### What needs to change
1. **Domain/DTO validation**: format-check DNI (8 digits) and RUC (11 digits + prefix + mod-11 check digit) before accepting a sale. CE/Pasaporte get a lighter "non-empty, bounded length" check given the format uncertainty above.
2. **"Boleta simple" case**: `Sale` currently REQUIRES a non-blank buyer reference on every sale (existing `sales` spec, `Record a Sale` requirement). This needs to change to allow an absent buyer reference when the sale's total is below S/ 700 — a real requirement-level change to an already-archived spec, not just a bugfix.
3. **`IssueVoucherForSaleUseCase.determineType`**: needs a third outcome (boleta simple / no document) alongside boleta-with-DNI and factura, and must pick the right NubeFacT `cliente_tipo_de_documento` code per case.
4. **Frontend**: expand `BUYER_DOCUMENT_TYPES`, add a "sin documento" option (only enabled/relevant under S/700), add client-side format hints matching the backend's real validation.

### Risks
- The S/ 700 threshold and the exact "who can omit ID" rule are the kind of number SUNAT could revise — same caution as the OSE-adapter-owned-knowledge principle from `sales-invoicing`'s design.md: this threshold should live in one clearly-named, easily-changed place (a domain constant or config value), not scattered.
- Modifying `Record a Sale`'s existing requirement (buyer reference currently unconditionally required) is a MODIFIED requirement on an already-archived spec, not an ADDED one — the delta spec for this change needs a `## MODIFIED Requirements` section, not just `## ADDED Requirements`.
- Not a regulatory/legal sign-off: per the earlier conversation in this session, a real Peruvian accountant should still validate these specific rules before this goes live for real invoicing — this research is enough to build correctly, not enough to substitute professional tax advice.

### Ready for Proposal
Yes — the two researched facts that actually block correct implementation (S/700 threshold, RUC check-digit algorithm) are now sourced. Remaining open questions (exact CE/Pasaporte format, NubeFacT's exact code for "sin documento") are proposal/design-level scope decisions, not blockers.
