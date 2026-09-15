package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for a {@code voidVoucher} (comunicación de baja) call ({@code operacion:
 * generar_anulacion}), confirmed against the real sandbox: {@code motivo} carries the void
 * reason, document type/serie/numero identify the original document being voided.
 */
record NubeFactVoidRequest(
        @JsonProperty("operacion") String operacion,
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("serie") String serie,
        @JsonProperty("numero") long numero,
        @JsonProperty("motivo") String motivo) {}
