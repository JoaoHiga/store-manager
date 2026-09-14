package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for a {@code checkStatus} consult call. NOT covered by research.md (scoped to
 * issuance only) — this endpoint/shape is {@link NubeFactInvoicingAdapter}'s best-informed
 * judgment call at NubeFacT's general "consultar comprobante" shape (document type + series +
 * correlativo identify the document), flagged as a deviation.
 */
record NubeFactStatusRequest(
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("series") String series,
        @JsonProperty("correlativo") long correlativo) {}
