package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for a {@code checkStatus} consult call ({@code operacion: consultar_comprobante}),
 * confirmed against the real sandbox: document type + serie + numero identify the document.
 */
record NubeFactStatusRequest(
        @JsonProperty("operacion") String operacion,
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("serie") String serie,
        @JsonProperty("numero") long numero) {}
