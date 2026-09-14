package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The NubeFacT request body for issuing a boleta/factura/credit note, field names per research.md:
 * {@code tipo_de_comprobante} (1=Factura, 2=Boleta, 3=NotaCredito), {@code series} (4 chars,
 * F.../B...), {@code correlativo} (no leading zeros — kept as a numeric type so JSON never
 * serializes one), {@code cliente_tipo_de_documento} (SUNAT buyer-document-type code), cabecera
 * totals ({@code total_gravada}/{@code total_igv}/{@code total_inafecta}/{@code
 * total_exonerada}/{@code total}/{@code porcentaje_de_igv}), and {@code items[]}.
 */
record NubeFactDocumentRequest(
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("series") String series,
        @JsonProperty("correlativo") long correlativo,
        @JsonProperty("cliente_tipo_de_documento") String clienteTipoDeDocumento,
        @JsonProperty("cliente_numero_de_documento") String clienteNumeroDeDocumento,
        @JsonProperty("total_gravada") BigDecimal totalGravada,
        @JsonProperty("total_igv") BigDecimal totalIgv,
        @JsonProperty("total_inafecta") BigDecimal totalInafecta,
        @JsonProperty("total_exonerada") BigDecimal totalExonerada,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("porcentaje_de_igv") BigDecimal porcentajeDeIgv,
        @JsonProperty("items") List<NubeFactItemRequest> items) {}
