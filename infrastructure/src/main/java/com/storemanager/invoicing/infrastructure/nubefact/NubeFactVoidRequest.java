package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for a {@code voidVoucher} (comunicación de baja) call. NOT covered by research.md
 * (scoped to issuance only) — endpoint and shape are {@link NubeFactInvoicingAdapter}'s
 * best-informed judgment call, flagged as a deviation: {@code motivo} carries the void reason,
 * document type/series/correlativo identify the original document being voided.
 */
record NubeFactVoidRequest(
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("series") String series,
        @JsonProperty("correlativo") long correlativo,
        @JsonProperty("motivo") String motivo) {}
