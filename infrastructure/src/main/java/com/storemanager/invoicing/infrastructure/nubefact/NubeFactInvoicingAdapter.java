package com.storemanager.invoicing.infrastructure.nubefact;

import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.CreditNoteRequest;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherLineItem;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
 * document kind ({@code FFF1} facturas, {@code BBB1} boletas — confirmed directly against the real
 * account's configured series in the NubeFacT panel), plus a durable, per-series {@link
 * CorrelativoAllocator} (database-backed — see {@link JpaCorrelativoAllocator} — fixed per RDD
 * review-reliability finding R3-nondurable-correlativo, which flagged an earlier in-memory counter
 * as risking duplicate series+correlativo pairs across a restart or a second app instance). {@code
 * IssuedVoucher.voucherReference()} is our own stable handle, built as {@code
 * "<series>-<correlativo>"}.
 *
 * <p><b>Credit/debit notes share their parent's series</b> (confirmed against the real account's
 * panel: NOTA DE CRÉDITO/DÉBITO ELECTRÓNICA for a factura are listed under the same {@code FFF1}
 * series as the factura itself, not a dedicated series like an earlier, unconfirmed assumption
 * here had it). {@code issueCreditNote} isn't wired to any use case yet, so this is dormant today,
 * but it means {@link #tipoFromSeries} can no longer disambiguate a factura/boleta from a credit
 * note issued against it by series text alone once that changes — {@code checkStatus}/{@code
 * voidVoucher} on a credit note's own reference will need the caller to pass the real {@code
 * tipo_de_comprobante} explicitly (e.g. from the issuing {@code Voucher}'s own state) instead of
 * inferring it here.
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

    // Confirmed against the real sandbox (curl, real account): NubeFacT has ONE endpoint per
    // client account (the configured base URL already includes that client-specific path), not
    // separate paths per operation. Every call POSTs to that same URL; the "operacion" field in
    // the body selects issue/consult/void.
    private static final String OPERACION_GENERAR_COMPROBANTE = "generar_comprobante";
    private static final String OPERACION_CONSULTAR_COMPROBANTE = "consultar_comprobante";
    private static final String OPERACION_GENERAR_ANULACION = "generar_anulacion";

    private static final int MAX_DESCRIPTION_LENGTH = 250;

    private static final int TIPO_FACTURA = 1;
    private static final int TIPO_BOLETA = 2;
    private static final int TIPO_NOTA_CREDITO = 3;

    private static final String SERIES_FACTURA = "FFF1";
    private static final String SERIES_BOLETA = "BBB1";

    private static final int SUNAT_TRANSACTION_NORMAL = 1;
    private static final int MONEDA_SOLES = 1;
    private static final DateTimeFormatter FECHA_DE_EMISION_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final BigDecimal IGV_PERCENTAGE = new BigDecimal("18.00");
    private static final BigDecimal IGV_MULTIPLIER = new BigDecimal("1.18");
    // NubeFacT's own tipo_de_igv catalog (1-9), NOT raw SUNAT catalog 07 (10/20/30...) — confirmed
    // against NubeFacT's official example code (www.nubefact.com/integracion), which sends "1" for
    // a normal taxed line. Sending the SUNAT code (10) instead triggered a confusing bundle of
    // "Total GRATUITA"/"Item - IGV" validation errors on the real sandbox.
    private static final int STANDARD_TAX_CODE = 1; // NubeFacT catalog: "Gravado - Operacion Onerosa"
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
                    NubeFactDocumentResponse response = post(body);
                    return toIssuedVoucher(response, series, correlativo);
                });
    }

    @Override
    public VoucherStatus checkStatus(String voucherReference) {
        VoucherReferenceParts parts = parseReference(voucherReference);
        NubeFactStatusRequest body =
                new NubeFactStatusRequest(
                        OPERACION_CONSULTAR_COMPROBANTE, tipoFromSeries(parts.series()), parts.series(),
                        parts.correlativo());
        NubeFactDocumentResponse response = post(body);
        return response.aceptadaPorSunat() ? VoucherStatus.ACCEPTED : VoucherStatus.PENDING;
    }

    @Override
    public IssuedVoucher voidVoucher(String voucherReference, String reason) {
        VoucherReferenceParts parts = parseReference(voucherReference);
        NubeFactVoidRequest body =
                new NubeFactVoidRequest(
                        OPERACION_GENERAR_ANULACION, tipoFromSeries(parts.series()), parts.series(),
                        parts.correlativo(), reason);
        NubeFactDocumentResponse response = post(body);
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
                    NubeFactDocumentResponse response = post(body);
                    return toIssuedVoucher(response, series, correlativo);
                });
    }

    /**
     * Builds the request body shared by every issuance path. Line items are mapped to {@link
     * NubeFactItemRequest} exactly once; the cabecera's {@code total_gravada}/{@code total_igv} are
     * derived from that same mapped list instead of re-deriving the 1.18 split a second time.
     *
     * <p>{@code cliente_denominacion} is a placeholder (confirmed against the real sandbox:
     * NubeFacT rejects a blank/under-3-char denominación) — this project doesn't collect a buyer's
     * legal name anywhere yet, only their document number. A follow-up (paused, see
     * {@code odd/tasks/buyer-name-lookup.md}) adds a real RUC/DNI lookup; until then this is a known, deliberate limitation, not a silent guess.
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
        String clienteDenominacion =
                "Cliente " + (clienteNumeroDeDocumento != null ? clienteNumeroDeDocumento : "Varios");
        // Confirmed against the real sandbox: NubeFacT rejects a blank cliente_numero_de_documento
        // (codigo 21) even when tipo is "-" (Varios/menor cuantia) — a placeholder is required.
        String resolvedClienteNumeroDeDocumento =
                clienteNumeroDeDocumento != null ? clienteNumeroDeDocumento : "00000000";
        String fechaDeEmision = FECHA_DE_EMISION_FORMAT.format(java.time.LocalDate.now(ZoneOffset.UTC));
        return new NubeFactDocumentRequest(
                OPERACION_GENERAR_COMPROBANTE,
                tipoDeComprobante,
                series,
                correlativo,
                SUNAT_TRANSACTION_NORMAL,
                clienteTipoDeDocumento,
                resolvedClienteNumeroDeDocumento,
                clienteDenominacion,
                "-",
                fechaDeEmision,
                MONEDA_SOLES,
                totalGravada,
                totalIgv,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                total,
                IGV_PERCENTAGE,
                true,
                items);
    }

    private NubeFactDocumentResponse post(Object body) {
        try {
            return restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .body(body)
                    .retrieve()
                    .body(NubeFactDocumentResponse.class);
        } catch (RuntimeException ex) {
            // Never log request/response payloads here: the Authorization header (the token)
            // must never reach the logs. Only the exception's own message is logged.
            log.warn("NubeFacT call failed: {}", ex.getMessage());
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
        int[] lineNumber = {0};
        return lineItems.stream()
                .map(
                        item -> {
                            // unitPrice/subtotal are our own tax-inclusive consumer prices (Peru
                            // retail convention); valor_unitario/total_gravada/igv are derived by
                            // dividing by 1.18 — confirmed against the real sandbox.
                            BigDecimal precioUnitario = item.unitPrice();
                            BigDecimal valorUnitario = divideByIgv(precioUnitario);
                            BigDecimal lineTotal = item.subtotal();
                            BigDecimal lineGravada = divideByIgv(lineTotal);
                            BigDecimal lineIgv = lineTotal.subtract(lineGravada);
                            lineNumber[0]++;
                            // `codigo`: not yet sourced from the real catalog SKU (VoucherLineItem
                            // doesn't carry one) — a positional placeholder until a future change
                            // threads the product SKU through.
                            String codigo = String.format("%03d", lineNumber[0]);
                            return new NubeFactItemRequest(
                                    UNIDAD_DE_MEDIDA_GOODS,
                                    codigo,
                                    item.description(),
                                    BigDecimal.valueOf(item.quantity()),
                                    valorUnitario,
                                    precioUnitario,
                                    lineGravada,
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

    /** A credit/debit note shares its series with the document it's issued against (see class javadoc). */
    private String creditNoteSeriesFor(String originalVoucherReference) {
        if (originalVoucherReference != null
                && originalVoucherReference.toUpperCase(Locale.ROOT).startsWith("F")) {
            return SERIES_FACTURA;
        }
        return SERIES_BOLETA;
    }

    /**
     * Known limitation (see class javadoc): since a credit note now shares its series with the
     * document it's issued against, this can only ever answer FACTURA/BOLETA, never
     * NOTA_CREDITO — fine for today's only real caller ({@code VoidSaleUseCase}, which never voids
     * a credit note), but wrong the day {@code checkStatus}/{@code voidVoucher} gets called with a
     * credit note's own reference.
     */
    private int tipoFromSeries(String series) {
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
