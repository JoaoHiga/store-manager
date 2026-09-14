## Research: buyer-identification-hardening — Peru buyer-document rules

### Boleta simple threshold
**S/ 700.00.** Below this amount, a boleta may be issued without buyer identification ("Clientes Varios"). At or above S/ 700.00, SUNAT requires the buyer's full name plus a document (DNI, Carnet de Extranjería, or Pasaporte).
Sources: [invyperu.com — Boleta Electrónica SUNAT: Cómo Emitirla en Perú (2026)](https://www.invyperu.com/blog/como-emitir-boleta-electronica-peru), [facturasmype.com — Errores comunes en facturación SUNAT 2026](https://facturasmype.com/errores-comunes-en-facturacion-sunat-2026/)

### DNI format
Exactly 8 numeric digits.
Source: [SUNAT — Manual de Usuario FV3800](https://orientacion.sunat.gob.pe/sites/default/files/inline-files/App_Manual_de_Usuario_FV3800_Parte2-15-38.pdf)

### RUC format and check digit
11 digits total. The first two digits MUST be one of `10`, `15`, `17`, `20` (taxpayer-type prefix). The 11th (last) digit is a check digit: multiply digits 1-10 by weights `5, 4, 3, 2, 7, 6, 5, 4, 3, 2` respectively (in order), sum the ten products, then `check_digit = 11 - (sum mod 11)`, with two exceptions: a result of `10` becomes `0`, and a result of `11` becomes `1`.
Worked example (from the source, verified against SUNAT's own RUC `20131312955`): sum of weighted digits = 94; `94 mod 11 = 6`; `11 - 6 = 5`, matching the real last digit.
Source: [digitoverificador.com — Cómo averiguar el dígito verificador RUC en Perú](https://www.digitoverificador.com/es/blog/digito-verificador-ruc-peru)

### Carnet de Extranjería / Pasaporte
SUNAT catalog 06 (tipo de documento de identidad) assigns type code `4` to Carnet de Extranjería and `7` to Pasaporte. No single authoritative fixed-length/format rule was found for either in this pass (unlike DNI/RUC) — formats have varied across issuance eras. **Recommendation**: validate these as "non-empty, reasonable bounded length" only; do not invent a strict digit-count regex without further confirmation, since rejecting a real, valid document is worse than under-validating here.
Source: [SUNAT catalog references, Anexo N.° 1 — Tabla 1: Tipo de Documento de Identidad](https://www.sunat.gob.pe/legislacion/superin/2021/anexo-112-2021.pdf)

### Cross-reference with `sales-invoicing`'s existing research
The archived `sales-invoicing` research.md already confirmed NubeFacT's `cliente_tipo_de_documento` codes: `6`=RUC, `1`=DNI, `4`=Carnet de Extranjería, `7`=Pasaporte, `A`=Cédula Diplomática, `0`=No domiciliado sin RUC, `G`=Salvoconducto, `-`=Varios/menor cuantía. The `-` code is the strongest candidate for NubeFacT's own "boleta simple, no document" representation, but this is inferred, not directly confirmed against NubeFacT's manual in this pass — flagged for design-time confirmation if/when real sandbox access is available.

### Status
Done. The two facts that block correct implementation (S/700 threshold, RUC check-digit algorithm) are sourced with a worked example. CE/Pasaporte intentionally left as light validation given inconclusive sourcing — documented as a deliberate choice, not an oversight.
