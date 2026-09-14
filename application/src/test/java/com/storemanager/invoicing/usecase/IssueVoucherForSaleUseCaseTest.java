package com.storemanager.invoicing.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssueVoucherForSaleUseCaseTest {

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private IssueVoucherForSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        useCase = new IssueVoucherForSaleUseCase(voucherRepository, invoicingProvider);
    }

    private Sale saleFor(String buyerDocumentType, String buyerDocumentNumber) {
        List<SaleItem> items = List.of(SaleItem.createNew(1L, "Usado", new BigDecimal("100"), 2));
        return Sale.reconstitute(
                10L, items, buyerDocumentType, buyerDocumentNumber, SaleStatus.COMPLETED, Instant.now());
    }

    @Test
    void aRucBuyerProducesAFactura() {
        invoicingProvider.alwaysAccept();

        Voucher voucher = useCase.execute(saleFor("RUC", "20131312955"));

        assertThat(voucher.getType()).isEqualTo(VoucherType.FACTURA);
    }

    @Test
    void aDniBuyerProducesABoleta() {
        invoicingProvider.alwaysAccept();

        Voucher voucher = useCase.execute(saleFor("DNI", "12345678"));

        assertThat(voucher.getType()).isEqualTo(VoucherType.BOLETA);
    }

    @Test
    void aSaleWithNoBuyerDocumentStillResolvesToBoleta() {
        // Regression proof for buyer-identification-hardening: determineType's existing
        // "RUC".equalsIgnoreCase(buyerDocumentType) was already null-safe (a String literal
        // receiver never NPEs on a null argument, per design.md's Architecture Decisions) — this
        // test proves that behavior wasn't accidentally broken while Sale/Voucher were relaxed to
        // accept a null buyer reference.
        invoicingProvider.alwaysAccept();

        Voucher voucher = useCase.execute(saleFor(null, null));

        assertThat(voucher.getType()).isEqualTo(VoucherType.BOLETA);
        assertThat(voucher.getBuyerDocumentType()).isNull();
        assertThat(voucher.getBuyerDocumentNumber()).isNull();
    }

    @Test
    void voucherAcceptedOnFirstAttemptIsPersistedAcceptedWithLinksAndHash() {
        invoicingProvider.alwaysAccept();

        Voucher voucher = useCase.execute(saleFor("DNI", "12345678"));

        assertThat(voucher.getId()).isNotNull();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(voucher.getPdfLink()).isNotBlank();
        assertThat(voucher.getXmlLink()).isNotBlank();
        assertThat(voucher.getCdrLink()).isNotBlank();
        assertThat(voucher.getHash()).isNotBlank();
        assertThat(voucher.getExternalReference()).isNotBlank();
    }

    @Test
    void saleCompletesAsPendingWhenTheProviderTimesOutOrIsUnreachable() {
        invoicingProvider.alwaysThrow();

        Voucher voucher = useCase.execute(saleFor("DNI", "12345678"));

        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(voucher.getRejectionReason()).isNotBlank();
    }

    @Test
    void saleCompletesAsPendingWithReasonRetainedWhenSunatRejects() {
        invoicingProvider.alwaysReject("Numero de documento del cliente invalido");

        Voucher voucher = useCase.execute(saleFor("RUC", "20131312955"));

        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(voucher.getRejectionReason()).isEqualTo("Numero de documento del cliente invalido");
    }

    @Test
    void thePersistedVoucherReferencesTheSourceSale() {
        invoicingProvider.alwaysAccept();
        Sale sale = saleFor("DNI", "12345678");

        Voucher voucher = useCase.execute(sale);

        assertThat(voucher.getSaleId()).isEqualTo(sale.getId());
    }
}
