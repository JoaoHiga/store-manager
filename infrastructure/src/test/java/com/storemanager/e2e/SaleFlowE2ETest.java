package com.storemanager.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Phase A of `automated-e2e-testing`: drives {@code SaleController} through a real Spring context
 * against a real PostgreSQL container (via Testcontainers + {@code @ServiceConnection}), so Flyway
 * runs the project's actual migrations instead of H2's approximation. Unlike {@code
 * SaleControllerTest} (a {@code @WebMvcTest} slice with every use-case mocked), this test proves
 * the full record -&gt; persist -&gt; list round trip against the real schema.
 *
 * <p>{@link InvoicingProvider} is mocked deliberately (design.md's Phase A/B split): Phase A never
 * touches the network or the real NubeFacT sandbox — that's Phase B's job. Mocking it here keeps
 * this suite fast and deterministic while still exercising every layer between the controller and
 * the real database.
 */
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SaleFlowE2ETest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired private TestRestTemplate restTemplate;

    @MockitoBean private InvoicingProvider invoicingProvider;

    private Long productId;

    @BeforeEach
    void seedCatalog() {
        // The container is shared across every test method in this class (design.md: one
        // container per class, not per test), and category names are uniquely constrained
        // (product-catalog's own duplicate-name protection) — so each test needs its own name.
        String categoryName = "Celulares-" + java.util.UUID.randomUUID();
        Map<String, Object> category = Map.of("name", categoryName, "description", "Smartphones");
        ResponseEntity<Map> categoryResponse = restTemplate.postForEntity("/api/categories", category, Map.class);
        assertThat(categoryResponse.getStatusCode())
                .as("category fixture creation: %s", categoryResponse.getBody())
                .isEqualTo(HttpStatus.CREATED);
        Long categoryId = ((Number) categoryResponse.getBody().get("id")).longValue();

        Map<String, Object> product =
                Map.of(
                        "name", "iPhone 12",
                        "categoryId", categoryId,
                        "salePrice", new BigDecimal("900.00"),
                        "stockQuantity", 10);
        ResponseEntity<Map> productResponse = restTemplate.postForEntity("/api/products", product, Map.class);
        assertThat(productResponse.getStatusCode())
                .as("product fixture creation: %s", productResponse.getBody())
                .isEqualTo(HttpStatus.CREATED);
        productId = ((Number) productResponse.getBody().get("id")).longValue();
    }

    private Map<String, Object> saleRequest(String voucherType, String buyerDocumentType, String buyerDocumentNumber) {
        return Map.of(
                "items", List.of(Map.of("productId", productId, "quantity", 1)),
                "buyerDocumentType", buyerDocumentType,
                "buyerDocumentNumber", buyerDocumentNumber,
                "voucherType", voucherType);
    }

    private IssuedVoucher acceptedVoucher() {
        return new IssuedVoucher(true, "B001-1", "ACEPTADA", "0", "hash", "pdf", "xml", "cdr");
    }

    private Long recordSale(String voucherType, String buyerDocumentType, String buyerDocumentNumber) {
        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        "/api/sales", saleRequest(voucherType, buyerDocumentType, buyerDocumentNumber), Map.class);
        return ((Number) response.getBody().get("id")).longValue();
    }

    @Test
    void recordsABoletaSaleAndReturns201() {
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(acceptedVoucher());

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/sales", saleRequest("BOLETA", "DNI", "12345678"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) response.getBody().get("total")).doubleValue()).isEqualTo(900.00);
    }

    @Test
    void recordsAFacturaSaleForARucBuyerAndReturns201() {
        Mockito.when(invoicingProvider.issueFactura(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(acceptedVoucher());

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/sales", saleRequest("FACTURA", "RUC", "20131312955"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void rejectsAFacturaSaleForANonRucBuyerWith400() {
        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/sales", saleRequest("FACTURA", "DNI", "12345678"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * The exact bug class from `explicit-voucher-type-selection`: the list must reflect each
     * sale's ACTUAL voucher type, not a derived/shared/defaulted one. `SaleResponse` currently
     * exposes only `voucherStatus`, never the type itself — this is new production behavior, not
     * a characterization of existing behavior (design.md's discovered gap, user-confirmed fix).
     */
    @Test
    void listReflectsEachSalesOwnVoucherType() {
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(acceptedVoucher());
        Mockito.when(invoicingProvider.issueFactura(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(acceptedVoucher());

        Long boletaSaleId = recordSale("BOLETA", "DNI", "12345678");
        Long facturaSaleId = recordSale("FACTURA", "RUC", "20131312955");

        ResponseEntity<List> listResponse = restTemplate.getForEntity("/api/sales", List.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> sales = listResponse.getBody();
        Map<String, Object> boletaSale =
                sales.stream().filter(sale -> boletaSaleId.equals(((Number) sale.get("id")).longValue())).findFirst().orElseThrow();
        Map<String, Object> facturaSale =
                sales.stream().filter(sale -> facturaSaleId.equals(((Number) sale.get("id")).longValue())).findFirst().orElseThrow();

        assertThat(boletaSale.get("voucherType")).isEqualTo("BOLETA");
        assertThat(facturaSale.get("voucherType")).isEqualTo("FACTURA");
    }

    @Test
    void voidingAnAcceptedSaleTransitionsItToCancelledAndRestoresStock() {
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(acceptedVoucher());
        Mockito.when(invoicingProvider.voidVoucher(ArgumentMatchers.eq("B001-1"), ArgumentMatchers.anyString()))
                .thenReturn(new IssuedVoucher(true, "B001-1", "ANULADA", "0", "hash", "pdf", "xml", "cdr"));

        Long saleId = recordSale("BOLETA", "DNI", "12345678");
        assertThat(currentProductStock()).isEqualTo(9);

        ResponseEntity<Map> voidResponse =
                restTemplate.postForEntity(
                        "/api/sales/" + saleId + "/void", Map.of("reason", "Cliente se arrepintió"), Map.class);

        assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(voidResponse.getBody().get("status")).isEqualTo("CANCELLED");
        assertThat(currentProductStock()).isEqualTo(10);
    }

    private int currentProductStock() {
        ResponseEntity<List> products = restTemplate.getForEntity("/api/products", List.class);
        Map<String, Object> product =
                ((List<Map<String, Object>>) products.getBody())
                        .stream()
                                .filter(candidate -> productId.equals(((Number) candidate.get("id")).longValue()))
                                .findFirst()
                                .orElseThrow();
        return ((Number) product.get("stockQuantity")).intValue();
    }

    /**
     * Real bug found running Phase B against the actual NubeFacT sandbox (not a hypothetical):
     * NubeFacT can return a rejection ({@code aceptada_por_sunat: false}) with a
     * {@code descripcion_sunat} longer than the {@code rejection_reason} column's old 1000-char
     * bound. That INSERT failure happened OUTSIDE {@code IssueVoucherForSaleUseCase}'s try/catch
     * (only the provider call itself is guarded), so it propagated uncaught and broke the sale
     * recording entirely — even though the whole point of the PENDING path is that a sale always
     * completes regardless of issuance outcome.
     */
    @Test
    void recordingASaleSurvivesAVeryLongRejectionReasonFromTheProvider() {
        String veryLongRejectionReason = "SUNAT rechazó el comprobante: ".repeat(50);
        assertThat(veryLongRejectionReason.length()).isGreaterThan(1000);
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenReturn(new IssuedVoucher(false, null, veryLongRejectionReason, "2", null, null, null, null));

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/sales", saleRequest("BOLETA", "DNI", "12345678"), Map.class);

        assertThat(response.getStatusCode())
                .as("sale recording must survive an oversized rejection reason: %s", response.getBody())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("voucherStatus")).isEqualTo("PENDING");
    }

    @Test
    void retryingAPendingVoucherReissuesItAndCanBeAccepted() {
        // First call (during record) fails -> the sale still completes with a PENDING voucher
        // (IssueVoucherForSaleUseCase's own contract). Second call (the manual retry) succeeds.
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenThrow(new RuntimeException("simulated NubeFacT outage"))
                .thenReturn(acceptedVoucher());

        Long saleId = recordSale("BOLETA", "DNI", "12345678");

        ResponseEntity<Map> retryResponse =
                restTemplate.postForEntity("/api/sales/" + saleId + "/retry-voucher", null, Map.class);

        assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retryResponse.getBody().get("status")).isEqualTo("ACCEPTED");
    }

    /**
     * atomic-record-sale: the sale, its stock decrement and its PENDING voucher commit together, and the
     * NubeFacT call happens after that commit, so a provider outage leaves a complete sale behind.
     */
    @Test
    void aProviderOutageStillCommitsTheSaleTheStockDecrementAndAPendingVoucher() {
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenThrow(new RuntimeException("simulated NubeFacT outage"));

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/sales", saleRequest("BOLETA", "DNI", "12345678"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("voucherStatus")).isEqualTo("PENDING");
        ResponseEntity<List> products = restTemplate.getForEntity("/api/products", List.class);
        Map<String, Object> product =
                ((List<Map<String, Object>>) products.getBody())
                        .stream()
                                .filter(candidate -> productId.equals(((Number) candidate.get("id")).longValue()))
                                .findFirst()
                                .orElseThrow();
        assertThat(((Number) product.get("stockQuantity")).intValue()).isEqualTo(9);
    }
}
