package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * The NubeFacT request body for issuing a boleta/factura/credit note. Field names and shape
 * confirmed directly against the real NubeFacT sandbox (curl, real account) — {@code operacion}
 * ({@code "generar_comprobante"}), {@code tipo_de_comprobante} (1=Factura, 2=Boleta,
 * 3=NotaCredito), {@code serie} (singular; 4 chars, F.../B...), {@code numero} (no leading zeros —
 * kept as a numeric type so JSON never serializes one), {@code sunat_transaction} (1 = normal
 * transaction), {@code cliente_tipo_de_documento}/{@code cliente_numero_de_documento} (SUNAT
 * buyer-document fields), {@code cliente_denominacion}/{@code cliente_direccion} (buyer name/
 * address — NubeFacT rejects a blank/short {@code cliente_denominacion}, min 3 chars; this
 * adapter fills a placeholder until a real buyer-name lookup exists, see
 * {@code odd/tasks/buyer-name-lookup.md}), {@code fecha_de_emision} (DD-MM-YYYY), {@code moneda} (1=Soles), cabecera totals
 * ({@code total_gravada}/{@code total_igv}/{@code total_inafecta}/{@code total_exonerada}/{@code
 * total}/{@code porcentaje_de_igv}), {@code enviar_automaticamente_a_la_sunat}, and {@code
 * items[]}.
 */
record NubeFactDocumentRequest(
        @JsonProperty("operacion") String operacion,
        @JsonProperty("tipo_de_comprobante") int tipoDeComprobante,
        @JsonProperty("serie") String serie,
        @JsonProperty("numero") long numero,
        @JsonProperty("sunat_transaction") int sunatTransaction,
        @JsonProperty("cliente_tipo_de_documento") String clienteTipoDeDocumento,
        @JsonProperty("cliente_numero_de_documento") String clienteNumeroDeDocumento,
        @JsonProperty("cliente_denominacion") String clienteDenominacion,
        @JsonProperty("cliente_direccion") String clienteDireccion,
        @JsonProperty("fecha_de_emision") String fechaDeEmision,
        @JsonProperty("moneda") int moneda,
        @JsonProperty("total_gravada") BigDecimal totalGravada,
        @JsonProperty("total_igv") BigDecimal totalIgv,
        @JsonProperty("total_inafecta") BigDecimal totalInafecta,
        @JsonProperty("total_exonerada") BigDecimal totalExonerada,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("porcentaje_de_igv") BigDecimal porcentajeDeIgv,
        @JsonProperty("enviar_automaticamente_a_la_sunat") boolean enviarAutomaticamenteALaSunat,
        @JsonProperty("items") List<NubeFactItemRequest> items) {}
