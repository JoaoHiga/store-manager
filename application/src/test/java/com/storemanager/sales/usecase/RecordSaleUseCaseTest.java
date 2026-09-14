package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Product;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordSaleUseCaseTest {

    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final InMemorySaleRepository saleRepository = new InMemorySaleRepository();
    private final InMemoryVoucherRepository voucherRepository = new InMemoryVoucherRepository();
    private final FakeInvoicingProvider invoicingProvider = new FakeInvoicingProvider();
    private final IssueVoucherForSaleUseCase issueVoucherForSaleUseCase =
            new IssueVoucherForSaleUseCase(voucherRepository, invoicingProvider);
    private final RecordSaleUseCase useCase =
            new RecordSaleUseCase(saleRepository, productRepository, issueVoucherForSaleUseCase);

    private Product activeProduct;

    @BeforeEach
    void seedProduct() {
        activeProduct =
                productRepository.save(
                        Product.createNew(
                                "iPhone 12", "Usado, buen estado", 1L, "Apple", new BigDecimal("300"),
                                new BigDecimal("350"), 3, Instant.now()));
    }

    @Test
    void recordsAValidSaleSnapshottingPriceAndDescriptionAndComputingTheTotal() {
        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");

        assertThat(sale.getId()).isNotNull();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getTotal()).isEqualByComparingTo("350");
        assertThat(sale.getItems()).hasSize(1);
        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");
        assertThat(sale.getItems().get(0).getDescription()).isEqualTo("Usado, buen estado");
    }

    @Test
    void computesTheTotalAcrossMultipleLineItems() {
        Product secondProduct =
                productRepository.save(
                        Product.createNew(
                                "Funda", null, 1L, null, null, new BigDecimal("19.90"), 10, Instant.now()));

        Sale sale =
                useCase.execute(
                        List.of(
                                new SaleLineItemRequest(activeProduct.getId(), 2),
                                new SaleLineItemRequest(secondProduct.getId(), 3)),
                        "DNI",
                        "12345678");

        assertThat(sale.getTotal()).isEqualByComparingTo("759.70");
    }

    @Test
    void rejectsASaleReferencingANonexistentProduct() {
        assertThatThrownBy(
                        () -> useCase.execute(List.of(new SaleLineItemRequest(999L, 1)), "DNI", "12345678"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejectsASaleReferencingAnInactiveProduct() {
        Product inactiveProduct = productRepository.save(activeProduct.deactivated(Instant.now()));

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(inactiveProduct.getId(), 1)), "DNI",
                                        "12345678"))
                .isInstanceOf(InactiveProductException.class);
    }

    @Test
    void rejectsASaleExceedingAvailableStock() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 4)), "DNI",
                                        "12345678"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rejectsASaleWithNoBuyerReference() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrementsProductStockAfterRecordingASale() {
        useCase.execute(List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");

        Product reloaded = productRepository.findById(activeProduct.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void rejectsASaleWhoseCombinedQuantityForTheSameProductAcrossTwoLinesExceedsStock() {
        // activeProduct has stockQuantity 3; two lines request 2 + 2 = 4, each individually <= 3
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(
                                                new SaleLineItemRequest(activeProduct.getId(), 2),
                                                new SaleLineItemRequest(activeProduct.getId(), 2)),
                                        "DNI",
                                        "12345678"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rejectsASecondSaleThatWouldExceedStockRemainingAfterAPriorSale() {
        useCase.execute(List.of(new SaleLineItemRequest(activeProduct.getId(), 2)), "DNI", "12345678");

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 2)), "DNI",
                                        "12345678"))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void aLaterProductPriceChangeDoesNotAffectAnAlreadyRecordedSalesLineItem() {
        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");
        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");

        productRepository.save(
                activeProduct.withUpdatedFields(
                        "iPhone 12", "Usado, buen estado", 1L, "Apple", new BigDecimal("300"),
                        new BigDecimal("400"), 3, Instant.now()));

        Sale reloaded = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(reloaded.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");
    }

    @Test
    void recordingASaleIssuesAnAcceptedVoucherForIt() {
        invoicingProvider.alwaysAccept();

        Sale sale =
                useCase.execute(List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");

        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
    }

    @Test
    void aSaleStillCompletesWhenTheInvoicingProviderRejectsTheVoucherLeavingItPending() {
        invoicingProvider.alwaysReject("Numero de documento invalido");

        Sale sale =
                useCase.execute(List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
    }

    @Test
    void aSaleStillCompletesWhenTheInvoicingProviderThrowsLeavingTheVoucherPending() {
        invoicingProvider.alwaysThrow();

        Sale sale =
                useCase.execute(List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678");

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
    }
}
