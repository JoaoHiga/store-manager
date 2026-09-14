package com.storemanager.invoicing.infrastructure.nubefact;

import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.CreditNoteRequest;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherLineItem;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * {@link InvoicingProvider} implementation talking to NubeFacT over HTTP via Spring's {@link
 * RestClient} (design.md's Architecture Decisions). Owns every NubeFacT-specific field name/HTTP
 * shape (per research.md) — nothing about it leaks past this class; callers only ever see the
 * OSE-agnostic {@code invoicing.domain.port} shapes.
 *
 * <p><b>Series/correlativo (deviation, deferred to this PR by design.md):</b> NubeFacT does not
 * assign series/correlativo — the client owns them. This adapter uses one fixed 4-char series per
 * document kind ({@code F001} facturas, {@code B001} boletas, {@code FC01}/{@code BC01} credit
 * notes issued against a factura/boleta respectively) plus a durable, per-series {@link
 * CorrelativoAllocator} (database-backed — see {@link JpaCorrelativoAllocator} — fixed per RDD
 * review-reliability finding R3-nondurable-correlativo, which flagged an earlier in-memory counter
 * as risking duplicate series+correlativo pairs across a restart or a second app instance). {@code
 * IssuedVoucher.voucherReference()} is our own stable handle, built as {@code
 * "<series>-<correlativo>"}.
 *
 * <p><b>Endpoint paths, auth header, and the {@code checkStatus}/{@code voidVoucher} request
 * shapes are NOT confirmed by research.md</b> (which was scoped to the issuance JSON schema only).
 * They are this adapter's best-informed judgment call at NubeFacT's general REST shape, called out
 * per-method below and in the PR report as deviations for a future integration engineer to verify
 * against real NubeFacT sandbox credentials.
 */
@Component
public class NubeFactInvoicingAdapter implements InvoicingProvider {

    private static final Logger log = LoggerFactory.getLogger(NubeFactInvoicingAdapter.class);

    private static final String ISSUE_PATH = "/api/v1/comprobantes";
    private static final String CONSULT_PATH = "/api/v1/comprobantes/consultar";
    private static final String VOID_PATH = "/api/v1/comprobantes/anular";

    private static final int MAX_DESCRIPTION_LENGTH = 250;

    private static final int TIPO_FACTURA = 1;
    private static final int TIPO_BOLETA = 2;
    private static final int TIPO_NOTA_CREDITO = 3;

    private static final String SERIES_FACTURA = "F001";
    private static final String SERIES_BOLETA = "B001";
    private static final String SERIES_CREDIT_NOTE_FACTURA = "FC01";
    private static final String SERIES_CREDIT_NOTE_BOLETA = "BC01";

    private static final BigDecimal IGV_PERCENTAGE = new BigDecimal("18.00");
    private static final BigDecimal IGV_MULTIPLIER = new BigDecimal("1.18");
    private static final int STANDARD_TAX_CODE = 10; // SUNAT catalog 07: "Gravado - Operacion Onerosa"
    private static final String UNIDAD_DE_MEDIDA_GOODS = "NIU";

    private static final Pattern VOUCHER_REFERENCE_PATTERN = Pattern.compile("^([A-Za-z0-9]{4})-(\\d{1,8})$");

    private final RestClient restClient;
    private final String apiToken;
    private final CorrelativoAllocator correlativoAllocator;

    public NubeFactInvoicingAdapter(
            RestClient restClient,
            @Value("${nubefact.token}") String apiToken,
            CorrelativoAllocator correlativoAllocator) {
        this.restClient = restClient;
        this.apiToken = apiToken;
        this.correlativoAllocator = correlativoAllocator;
    }

    @Override
    public IssuedVoucher issueBoleta(VoucherRequest request) {
        return issueDocument(request, TIPO_BOLETA, SERIES_BOLETA);
    }

    @Override
    public IssuedVoucher issueFactura(VoucherRequest request) {
        return issueDocument(request, TIPO_FACTURA, SERIES_FACTURA);
    }

    @Override
    public IssuedVoucher issueCreditNote(CreditNoteRequest request) {
        validateLineItems(request.lineItems());
        String series = creditNoteSeriesFor(request.originalVoucherReference());
        return correlativoAllocator.withCorrelativo(
                series,
                correlativo -> {
                    NubeFactDocumentRequest body =
                            buildDocumentBody(
                                    TIPO_NOTA_CREDITO, series, correlativo, null, null, request.total(),
                                    request.lineItems());
                    NubeFactDocumentResponse response = post(ISSUE_PATH, body);
                    return toIssuedVoucher(response, series, correlativo);
                });
    }

    @Override
    public VoucherStatus checkStatus(String voucherReference) {
        VoucherReferenceParts parts = parseReference(voucherReference);
        NubeFactStatusRequest body =
                new NubeFactStatusRequest(tipoFromSeries(parts.series()), parts.series(), parts.correlativo());
        NubeFactDocumentResponse response = post(CONSULT_PATH, body);
        return response.aceptadaPorSunat() ? VoucherStatus.ACCEPTED : VoucherStatus.PENDING;
    }

    @Override
    public IssuedVoucher voidVoucher(String voucherReference, String reason) {
        VoucherReferenceParts parts = parseReference(voucherReference);
        NubeFactVoidRequest body =
                new NubeFactVoidRequest(tipoFromSeries(parts.series()), parts.series(), parts.correlativo(), reason);
        NubeFactDocumentResponse response = post(VOID_PATH, body);
        return toIssuedVoucher(response, parts.series(), parts.correlativo());
    }

    /** See {@link CorrelativoAllocator}: the whole HTTP call runs inside {@code withCorrelativo}, so a concurrent request or a thrown exception can never duplicate or burn a correlativo. */
    private IssuedVoucher issueDocument(VoucherRequest request, int tipoDeComprobante, String series) {
        validateLineItems(request.lineItems());
        return correlativoAllocator.withCorrelativo(
                series,
                correlativo -> {
                    NubeFactDocumentRequest body =
                            buildDocumentBody(
                                    tipoDeComprobante,
                                    series,
                                    correlativo,
                                    mapDocumentType(request.buyerDocumentType()),
                                    request.buyerDocumentNumber(),
                                    request.total(),
                                    request.lineItems());
                    NubeFactDocumentResponse response = post(ISSUE_PATH, body);
                    return toIssuedVoucher(response, series, correlativo);
                });
    }

    /**
     * Builds the request body shared by every issuance path. Line items are mapped to {@link
     * NubeFactItemRequest} exactly once; the cabecera's {@code total_gravada}/{@code total_igv} are
     * derived from that same mapped list instead of re-deriving the 1.18 split a second time.
     */
    private NubeFactDocumentRequest buildDocumentBody(
            int tipoDeComprobante,
            String series,
            long correlativo,
            String clienteTipoDeDocumento,
            String clienteNumeroDeDocumento,
            BigDecimal total,
            List<VoucherLineItem> lineItems) {
        List<NubeFactItemRequest> items = toItemRequests(lineItems);
        BigDecimal totalIgv = items.stream().map(NubeFactItemRequest::igv).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGravada =
                items.stream()
                        .map(item -> item.total().subtract(item.igv()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new NubeFactDocumentRequest(
                tipoDeComprobante,
                series,
                correlativo,
                clienteTipoDeDocumento,
                clienteNumeroDeDocumento,
                totalGravada,
                totalIgv,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                total,
                IGV_PERCENTAGE,
                items);
    }

    private NubeFactDocumentResponse post(String path, Object body) {
        try {
            return restClient
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .body(body)
                    .retrieve()
                    .body(NubeFactDocumentResponse.class);
        } catch (RuntimeException ex) {
            // Never log request/response payloads here: the Authorization header (the token)
            // must never reach the logs. Only the exception's own message is logged.
            log.warn("NubeFacT call to {} failed: {}", path, ex.getMessage());
            throw ex;
        }
    }

    private void validateLineItems(List<VoucherLineItem> lineItems) {
        for (VoucherLineItem item : lineItems) {
            String description = item.description();
            if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
                throw new InvoiceFieldTooLongException("descripcion", MAX_DESCRIPTION_LENGTH, description.length());
            }
        }
    }

    private List<NubeFactItemRequest> toItemRequests(List<VoucherLineItem> lineItems) {
        return lineItems.stream()
                .map(
                        item -> {
                            // unitPrice/subtotal are our own tax-inclusive consumer prices (Peru
                            // retail convention); valor_unitario/total_gravada/igv are derived by
                            // dividing by 1.18 — this modeling assumption isn't confirmed by
                            // research.md and is called out as a deviation in the PR report.
                            BigDecimal precioUnitario = item.unitPrice();
                            BigDecimal valorUnitario = divideByIgv(precioUnitario);
                            BigDecimal lineTotal = item.subtotal();
                            BigDecimal lineGravada = divideByIgv(lineTotal);
                            BigDecimal lineIgv = lineTotal.subtract(lineGravada);
                            return new NubeFactItemRequest(
                                    UNIDAD_DE_MEDIDA_GOODS,
                                    item.description(),
                                    BigDecimal.valueOf(item.quantity()),
                                    valorUnitario,
                                    precioUnitario,
                                    STANDARD_TAX_CODE,
                                    lineIgv,
                                    lineTotal);
                        })
                .toList();
    }

    private BigDecimal divideByIgv(BigDecimal taxInclusiveAmount) {
        return taxInclusiveAmount.divide(IGV_MULTIPLIER, 2, RoundingMode.HALF_UP);
    }

    /**
     * Maps a buyer's document type to NubeFacT's {@code cliente_tipo_de_documento} code.
     *
     * <p>A {@code null} input (a "boleta simple" issued under the S/700 no-document threshold —
     * see buyer-identification-hardening's design.md) maps to NubeFacT's "-" (varios/menor
     * cuantía) code instead of throwing; covered by {@code
     * NubeFactInvoicingAdapterTest#issueBoletaWithNullBuyerDocumentMapsToTheDashCodeAndDoesNotThrow}.
     */
    private String mapDocumentType(String buyerDocumentType) {
        if (buyerDocumentType == null) {
            return "-";
        }
        return switch (buyerDocumentType.toUpperCase(Locale.ROOT)) {
            case "RUC" -> "6";
            case "DNI" -> "1";
            case "CE" -> "4";
            case "PASAPORTE", "PASSPORT" -> "7";
            default -> "-";
        };
    }

    private String creditNoteSeriesFor(String originalVoucherReference) {
        if (originalVoucherReference != null
                && originalVoucherReference.toUpperCase(Locale.ROOT).startsWith("F")) {
            return SERIES_CREDIT_NOTE_FACTURA;
        }
        return SERIES_CREDIT_NOTE_BOLETA;
    }

    private int tipoFromSeries(String series) {
        if (SERIES_CREDIT_NOTE_FACTURA.equals(series) || SERIES_CREDIT_NOTE_BOLETA.equals(series)) {
            return TIPO_NOTA_CREDITO;
        }
        return series.toUpperCase(Locale.ROOT).startsWith("F") ? TIPO_FACTURA : TIPO_BOLETA;
    }

    private VoucherReferenceParts parseReference(String voucherReference) {
        if (voucherReference == null) {
            throw new IllegalArgumentException("voucherReference must not be null");
        }
        Matcher matcher = VOUCHER_REFERENCE_PATTERN.matcher(voucherReference);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid voucherReference format: " + voucherReference);
        }
        return new VoucherReferenceParts(matcher.group(1), Long.parseLong(matcher.group(2)));
    }

    private IssuedVoucher toIssuedVoucher(NubeFactDocumentResponse response, String series, long correlativo) {
        String voucherReference = response.aceptadaPorSunat() ? series + "-" + correlativo : null;
        return new IssuedVoucher(
                response.aceptadaPorSunat(),
                voucherReference,
                response.sunatDescription(),
                response.sunatResponsecode(),
                response.codigoHash(),
                response.enlaceDelPdf(),
                response.enlaceDelXml(),
                response.enlaceDelCdr());
    }

    private record VoucherReferenceParts(String series, long correlativo) {}
}
