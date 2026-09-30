package com.storemanager.invoicing.infrastructure.nubefact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.CreditNoteRequest;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherLineItem;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Adapter unit tests against a mocked HTTP layer ({@link MockRestServiceServer} bound to a {@link
 * RestClient.Builder}) — no live NubeFacT call is ever made. Field names, the single-endpoint
 * routing, the plain (non-"Bearer") auth header, and the {@code operacion} field were all
 * confirmed against the real NubeFacT sandbox directly (curl, real account) before this rewrite —
 * see automated-e2e-testing's follow-up fix: the original shape here was research.md's
 * best-informed guess, and it was wrong (real calls 404'd on the old per-operation URL paths).
 */
class NubeFactInvoicingAdapterTest {

    // The real endpoint is base-url-only, per-client (a URL like
    // https://api.nubefact.com/api/v1/{client-guid}) — every operation POSTs to this exact URL,
    // distinguished only by the "operacion" field in the body.
    private static final String BASE_URL = "https://sandbox.nubefact.test/api/v1/test-client-guid";
    private static final String TOKEN = "test-token-123";

    private static final String ACCEPTED_RESPONSE_JSON =
            """
            {
              "aceptada_por_sunat": true,
              "enlace_del_pdf": "https://nubefact.test/pdf/1",
              "enlace_del_xml": "https://nubefact.test/xml/1",
              "enlace_del_cdr": "https://nubefact.test/cdr/1",
              "codigo_hash": "HASH123",
              "cadena_para_codigo_qr": "20123456789|2|BBB1|1|18.00|118.00|1|12345678|20260101|HASH123",
              "sunat_description": "La boleta ha sido aceptada",
              "sunat_responsecode": "0"
            }
            """;

    private static final String REJECTED_RESPONSE_JSON =
            """
            {
              "aceptada_por_sunat": false,
              "sunat_description": "El numero de documento del cliente no es valido",
              "sunat_responsecode": "2035"
            }
            """;

    private MockRestServiceServer server;
    private NubeFactInvoicingAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        adapter = new NubeFactInvoicingAdapter(restClient, TOKEN, new InMemoryCorrelativoAllocator());
    }

    @Test
    void issueBoletaMapsAcceptedResponse() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        IssuedVoucher issued = adapter.issueBoleta(boletaRequest());

        assertThat(issued.accepted()).isTrue();
        assertThat(issued.voucherReference()).isEqualTo("BBB1-1");
        assertThat(issued.hash()).isEqualTo("HASH123");
        assertThat(issued.pdfLink()).isEqualTo("https://nubefact.test/pdf/1");
        assertThat(issued.xmlLink()).isEqualTo("https://nubefact.test/xml/1");
        assertThat(issued.cdrLink()).isEqualTo("https://nubefact.test/cdr/1");
        assertThat(issued.sunatCode()).isEqualTo("0");
    }

    @Test
    void issueBoletaSendsRequestBodyMatchingTheRealNubeFactContract() {
        // boletaRequest(): unitPrice 29.50 x qty 2 = subtotal 59.00 (tax-inclusive). Divided by the
        // 1.18 IGV multiplier: valor_unitario 29.50/1.18 = 25.00 exactly; line total_gravada
        // 59.00/1.18 = 50.00 exactly; line igv 59.00 - 50.00 = 9.00.
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, TOKEN))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "operacion": "generar_comprobante",
                                          "tipo_de_comprobante": 2,
                                          "serie": "BBB1",
                                          "numero": 1,
                                          "sunat_transaction": 1,
                                          "cliente_tipo_de_documento": "1",
                                          "cliente_numero_de_documento": "45678912",
                                          "moneda": 1,
                                          "total_gravada": 50.00,
                                          "total_igv": 9.00,
                                          "total_inafecta": 0.00,
                                          "total_exonerada": 0.00,
                                          "total": 59.00,
                                          "porcentaje_de_igv": 18.00,
                                          "enviar_automaticamente_a_la_sunat": true,
                                          "items": [
                                            {
                                              "unidad_de_medida": "NIU",
                                              "descripcion": "Case protector",
                                              "cantidad": 2,
                                              "valor_unitario": 25.00,
                                              "precio_unitario": 29.50,
                                              "subtotal": 50.00,
                                              "tipo_de_igv": 1,
                                              "igv": 9.00,
                                              "total": 59.00
                                            }
                                          ]
                                        }
                                        """,
                                        false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        adapter.issueBoleta(boletaRequest());

        server.verify();
    }

    @Test
    void issueBoletaWithNullBuyerDocumentMapsToTheDashCodeAndDoesNotThrow() {
        // buyer-identification-hardening: a "boleta simple" (sale under S/700, no buyer document)
        // must map to NubeFacT's "-" (varios/menor cuantia) cliente_tipo_de_documento code instead
        // of NPE-ing on a null buyerDocumentType — see research.md and design.md's Data Flow.
        VoucherRequest request =
                new VoucherRequest(
                        null, null,
                        List.of(new VoucherLineItem("Case protector", new BigDecimal("59.00"), 1, new BigDecimal("59.00"))),
                        new BigDecimal("59.00"));

        server.expect(requestTo(BASE_URL))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "cliente_tipo_de_documento": "-"
                                        }
                                        """,
                                        false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        IssuedVoucher issued = adapter.issueBoleta(request);

        assertThat(issued.accepted()).isTrue();
        server.verify();
    }

    @Test
    void issueFacturaSendsRucDocumentTypeAndFacturaSeries() {
        VoucherRequest request =
                new VoucherRequest(
                        "RUC", "20123456789", List.of(new VoucherLineItem("Smartphone case", new BigDecimal("59.00"), 1, new BigDecimal("59.00"))),
                        new BigDecimal("59.00"));

        server.expect(requestTo(BASE_URL))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "operacion": "generar_comprobante",
                                          "tipo_de_comprobante": 1,
                                          "serie": "FFF1",
                                          "numero": 1,
                                          "cliente_tipo_de_documento": "6",
                                          "total_gravada": 50.00,
                                          "total_igv": 9.00,
                                          "total": 59.00,
                                          "items": [
                                            {
                                              "valor_unitario": 50.00,
                                              "precio_unitario": 59.00,
                                              "subtotal": 50.00,
                                              "igv": 9.00,
                                              "total": 59.00
                                            }
                                          ]
                                        }
                                        """,
                                        false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        adapter.issueFactura(request);

        server.verify();
    }

    @Test
    void issueBoletaMapsRejectedResponse() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess(REJECTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        IssuedVoucher issued = adapter.issueBoleta(boletaRequest());

        assertThat(issued.accepted()).isFalse();
        assertThat(issued.voucherReference()).isNull();
        assertThat(issued.sunatDescription()).isEqualTo("El numero de documento del cliente no es valido");
        assertThat(issued.sunatCode()).isEqualTo("2035");
        assertThat(issued.pdfLink()).isNull();
    }

    @Test
    void issueBoletaThrowsOnServerError() {
        server.expect(requestTo(BASE_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.issueBoleta(boletaRequest()))
                .isInstanceOf(org.springframework.web.client.HttpServerErrorException.class);
    }

    @Test
    void issueBoletaThrowsOnConnectionFailure() {
        server.expect(requestTo(BASE_URL))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });

        assertThatThrownBy(() -> adapter.issueBoleta(boletaRequest())).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void aConnectionFailureDoesNotBurnTheCorrelativoSoARetryReusesTheSameNumber() {
        // Both expectations registered upfront: MockRestServiceServer forbids adding one mid-flight.
        server.expect(requestTo(BASE_URL))
                .andRespond(request -> {
                    throw new IOException("Connection refused");
                });
        server.expect(requestTo(BASE_URL))
                .andExpect(content().json("{\"numero\": 1}", false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.issueBoleta(boletaRequest())).isInstanceOf(ResourceAccessException.class);
        IssuedVoucher issued = adapter.issueBoleta(boletaRequest());

        assertThat(issued.voucherReference()).isEqualTo("BBB1-1");
    }

    @Test
    void issueBoletaRejectsOverLongDescriptionBeforeCallingProvider() {
        VoucherLineItem tooLong =
                new VoucherLineItem("A".repeat(251), BigDecimal.TEN, 1, BigDecimal.TEN);
        VoucherRequest request = new VoucherRequest("DNI", "45678912", List.of(tooLong), BigDecimal.TEN);

        // No expectation registered on `server`: if the adapter called NubeFacT before validating,
        // MockRestServiceServer would raise its own "no further requests expected" AssertionError
        // instead of InvoiceFieldTooLongException, failing this assertion.
        assertThatThrownBy(() -> adapter.issueBoleta(request))
                .isInstanceOf(InvoiceFieldTooLongException.class)
                .hasMessageContaining("250");
    }

    @Test
    void issueCreditNoteMapsAcceptedResponseAndPicksSeriesFromOriginalReference() {
        CreditNoteRequest request =
                new CreditNoteRequest(
                        "FFF1-7", "Devolucion de mercaderia",
                        List.of(new VoucherLineItem("Smartphone case", new BigDecimal("59.00"), 1, new BigDecimal("59.00"))),
                        new BigDecimal("59.00"));

        server.expect(requestTo(BASE_URL))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "operacion": "generar_comprobante",
                                          "tipo_de_comprobante": 3,
                                          "serie": "FFF1",
                                          "numero": 1,
                                          "total_gravada": 50.00,
                                          "total_igv": 9.00,
                                          "total": 59.00,
                                          "items": [
                                            {
                                              "valor_unitario": 50.00,
                                              "precio_unitario": 59.00,
                                              "subtotal": 50.00,
                                              "igv": 9.00,
                                              "total": 59.00
                                            }
                                          ]
                                        }
                                        """,
                                        false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        IssuedVoucher issued = adapter.issueCreditNote(request);

        assertThat(issued.accepted()).isTrue();
        assertThat(issued.voucherReference()).isEqualTo("FFF1-1");
    }

    @Test
    void checkStatusReturnsAcceptedWhenSunatAccepted() {
        server.expect(requestTo(BASE_URL))
                .andExpect(content().json("{\"operacion\": \"consultar_comprobante\"}", false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        VoucherStatus status = adapter.checkStatus("BBB1-1");

        assertThat(status).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void checkStatusReturnsPendingWhenNotAccepted() {
        server.expect(requestTo(BASE_URL))
                .andRespond(withSuccess(REJECTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        VoucherStatus status = adapter.checkStatus("BBB1-1");

        assertThat(status).isEqualTo(VoucherStatus.PENDING);
    }

    @Test
    void voidVoucherMapsAcceptedResponse() {
        server.expect(requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"operacion\": \"generar_anulacion\"}", false))
                .andRespond(withSuccess(ACCEPTED_RESPONSE_JSON, MediaType.APPLICATION_JSON));

        IssuedVoucher issued = adapter.voidVoucher("BBB1-1", "Anulacion por error de digitacion");

        assertThat(issued.accepted()).isTrue();
    }

    @Test
    void voidVoucherRejectsMalformedReference() {
        assertThatThrownBy(() -> adapter.voidVoucher("not-a-reference-format", "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private VoucherRequest boletaRequest() {
        return new VoucherRequest(
                "DNI", "45678912",
                List.of(new VoucherLineItem("Case protector", new BigDecimal("29.50"), 2, new BigDecimal("59.00"))),
                new BigDecimal("59.00"));
    }
}
