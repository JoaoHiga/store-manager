package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * One NubeFacT {@code items[]} entry, confirmed against the real sandbox: {@code
 * unidad_de_medida} ({@code NIU} for goods, {@code ZZ} for services), {@code codigo} (internal
 * product code — not yet sourced from the real catalog SKU, placeholder per line index until a
 * future change threads it through), {@code descripcion} (max 250 chars — enforced by {@link
 * NubeFactInvoicingAdapter} before this is ever built), {@code cantidad}, {@code valor_unitario}
 * (pre-tax unit price), {@code precio_unitario} (tax-inclusive unit price), {@code subtotal}
 * (pre-tax line total — {@code valor_unitario * cantidad}), {@code tipo_de_igv} (SUNAT
 * tax-classification code, 1-20), {@code igv} (line VAT), {@code total} (tax-inclusive line
 * total).
 */
record NubeFactItemRequest(
        @JsonProperty("unidad_de_medida") String unidadDeMedida,
        @JsonProperty("codigo") String codigo,
        @JsonProperty("descripcion") String descripcion,
        @JsonProperty("cantidad") BigDecimal cantidad,
        @JsonProperty("valor_unitario") BigDecimal valorUnitario,
        @JsonProperty("precio_unitario") BigDecimal precioUnitario,
        @JsonProperty("subtotal") BigDecimal subtotal,
        @JsonProperty("tipo_de_igv") int tipoDeIgv,
        @JsonProperty("igv") BigDecimal igv,
        @JsonProperty("total") BigDecimal total) {}
