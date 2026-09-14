package com.storemanager.invoicing.infrastructure.nubefact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NubeFacT's response to an issue/void/consult call. {@code aceptadaPorSunat}, the PDF/XML/CDR
 * links, {@code codigoHash} and {@code cadenaParaCodigoQr} are the fields research.md confirms.
 * {@code sunatDescription}/{@code sunatResponsecode} are NOT in research.md (which was scoped to
 * the accepted-response shape) — they are this adapter's best-informed guess at NubeFacT's
 * rejection-reason field names, matching the general NubeFacT REST shape; flagged as a deviation.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} because NubeFacT's real payload almost
 * certainly carries more fields than this adapter consumes (e.g. the QR string components).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record NubeFactDocumentResponse(
        @JsonProperty("aceptada_por_sunat") boolean aceptadaPorSunat,
        @JsonProperty("enlace_del_pdf") String enlaceDelPdf,
        @JsonProperty("enlace_del_xml") String enlaceDelXml,
        @JsonProperty("enlace_del_cdr") String enlaceDelCdr,
        @JsonProperty("codigo_hash") String codigoHash,
        @JsonProperty("cadena_para_codigo_qr") String cadenaParaCodigoQr,
        @JsonProperty("sunat_description") String sunatDescription,
        @JsonProperty("sunat_responsecode") String sunatResponsecode) {}
