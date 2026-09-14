package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One NubeFacT {@code items[]} entry, field names/limits per research.md: {@code
 * unidad_de_medida} ({@code NIU} for goods, {@code ZZ} for services), {@code descripcion} (max 250
 * chars — enforced by {@link NubeFactInvoicingAdapter} before this is ever built), {@code
 * cantidad}, {@code valor_unitario} (pre-tax unit price), {@code precio_unitario} (tax-inclusive
 * unit price), {@code tipo_de_igv} (SUNAT tax-classification code, 1-20), {@code igv} (line VAT),
 * {@code total} (line total).
 */
record NubeFactItemRequest(
        @JsonProperty("unidad_de_medida") String unidadDeMedida,
        @JsonProperty("descripcion") String descripcion,
        @JsonProperty("cantidad") BigDecimal cantidad,
        @JsonProperty("valor_unitario") BigDecimal valorUnitario,
        @JsonProperty("precio_unitario") BigDecimal precioUnitario,
        @JsonProperty("tipo_de_igv") int tipoDeIgv,
        @JsonProperty("igv") BigDecimal igv,
        @JsonProperty("total") BigDecimal total) {}
