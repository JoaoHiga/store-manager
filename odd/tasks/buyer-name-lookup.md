# buyer-name-lookup

**Status**: PAUSED (2026-09-16). Not cancelled; resume when a provider decision is made.
**Engram mirror**: `odd/buyer-name-lookup/tasks`
**Origin**: migrated from the paused SDD change `buyer-name-lookup` (exploration only, no code touched).

## Objective

Put the buyer's real name (DNI) or legal name (RUC) on issued vouchers instead of a placeholder.

## Problem

`NubeFactInvoicingAdapter` sends `cliente_denominacion` as the placeholder `"Cliente <docnumber>"`.
NubeFacT rejects a blank or under-3-char value, so the placeholder is required today, but it is not
the buyer's real name.

Current state:
- `Sale`, `SaleItem` and `RecordSaleRequest` only collect buyer document type and number, never a name.
- `RecordSalePage.jsx` has no name field.
- No outbound lookup port exists in the codebase.

## Why paused

The official free portals (e-consultaruc.sunat.gob.pe, cel.reniec.gob.pe/valreg) are CAPTCHA-gated
specifically to block automation. Scraping around that would violate their terms of service and likely
Peru's cybercrime law. **Ruled out, not attempted.** There is no official free SUNAT/RENIEC API for
general integrators.

## Remaining options

1. **Paid/freemium third-party provider.** Best-documented candidate: Decolecta (decolecta.com, docs at
   decolecta.gitbook.io/docs). RUC lookup "directly from SUNAT" (state, condition, address, economic
   activity, presumably razón social); DNI lookup returns names only, "from public sources". 1000 free
   requests/month, Bearer token auth, base URL `api.decolecta.com`. apis.net.pe appears to proxy the same
   backend. Unevaluated competitors: APIsPERU, PeruAPI, dniruc.com, ApiRestPeru.
   Caveat: reliability and reputation unconfirmed (no independent reviews or SLA found), and it scrapes
   itself rather than using an official channel.
2. **Manual only.** The cashier types the name.

## Constraints

- A lookup failure must fall back to today's placeholder and must never block a sale (same contract as
  `IssueVoucherForSaleUseCase`).
- Estimated volume ~500 lookups/month; check it against any provider quota.
- Hexagonal: a new outbound port (e.g. `BuyerNameLookupProvider`) in `application`, adapter in
  `infrastructure`.

## Open questions (block implementation)

1. Which provider, with **confirmed** response field names and cost. Needs a live trial call with a real
   API key; public docs do not confirm the JSON fields.
2. When the lookup happens (sale-time snapshot, like `unitPrice`, vs invoicing-time) and whether the
   cashier sees and can edit the result.

## Tasks

None yet. Define them after both open questions are answered.

## Progress

- 2026-09-16: exploration and provider research done; paused.
- 2026-09-29: migrated from SDD to ODD.

## Next step

Decide between a provider trial (get an API key, make one live call) and the manual-only path.
