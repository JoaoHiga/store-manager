package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Product;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRepositoryPort;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import com.storemanager.invoicing.usecase.IssueVoucherForSaleUseCase;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    private final RecordingTransactionRunner transactionRunner = new RecordingTransactionRunner();
    private final RecordSaleUseCase useCase =
            new RecordSaleUseCase(saleRepository, productRepository, issueVoucherForSaleUseCase, transactionRunner);

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
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);

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
                        "12345678",
                        VoucherType.BOLETA);

        assertThat(sale.getTotal()).isEqualByComparingTo("759.70");
    }

    @Test
    void rejectsASaleReferencingANonexistentProduct() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(999L, 1)), "DNI", "12345678",
                                        VoucherType.BOLETA))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejectsASaleReferencingAnInactiveProduct() {
        Product inactiveProduct = productRepository.save(activeProduct.deactivated(Instant.now()));

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(inactiveProduct.getId(), 1)), "DNI",
                                        "12345678", VoucherType.BOLETA))
                .isInstanceOf(InactiveProductException.class);
    }

    @Test
    void rejectsASaleExceedingAvailableStock() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 4)), "DNI",
                                        "12345678", VoucherType.BOLETA))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void recordsASaleWithNoBuyerReferenceWhenTheTotalIsBelowTheBoletaSimpleThreshold() {
        // activeProduct's salePrice is 350; one unit keeps the total under S/700.
        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), null, null,
                        VoucherType.BOLETA);

        assertThat(sale.getBuyerDocumentType()).isNull();
        assertThat(sale.getBuyerDocumentNumber()).isNull();
    }

    @Test
    void rejectsASaleWithNoBuyerReferenceWhenTheTotalIsAtOrAboveTheBoletaSimpleThreshold() {
        // activeProduct's salePrice is 350; two units reach exactly S/700.
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 2)), null, null,
                                        VoucherType.BOLETA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decrementsProductStockAfterRecordingASale() {
        useCase.execute(
                List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678", VoucherType.BOLETA);

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
                                        "12345678",
                                        VoucherType.BOLETA))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void rejectsASecondSaleThatWouldExceedStockRemainingAfterAPriorSale() {
        useCase.execute(
                List.of(new SaleLineItemRequest(activeProduct.getId(), 2)), "DNI", "12345678", VoucherType.BOLETA);

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 2)), "DNI",
                                        "12345678", VoucherType.BOLETA))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void aLaterProductPriceChangeDoesNotAffectAnAlreadyRecordedSalesLineItem() {
        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);
        assertThat(sale.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");

        productRepository.save(
                activeProduct.withUpdatedFields(
                        "iPhone 12", "Usado, buen estado", 1L, "Apple", new BigDecimal("400"), Instant.now()));

        Sale reloaded = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(reloaded.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");
    }

    @Test
    void recordingASaleIssuesAnAcceptedVoucherForIt() {
        invoicingProvider.alwaysAccept();

        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);

        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
    }

    @Test
    void aSaleStillCompletesWhenTheInvoicingProviderRejectsTheVoucherLeavingItPending() {
        invoicingProvider.alwaysReject("Numero de documento invalido");

        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
    }

    @Test
    void aSaleStillCompletesWhenTheInvoicingProviderThrowsLeavingTheVoucherPending() {
        invoicingProvider.alwaysThrow();

        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
    }

    @Test
    void aFacturaRequestForARucBuyerProducesAFacturaVoucher() {
        invoicingProvider.alwaysAccept();

        Sale sale =
                useCase.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "RUC", "20131312955",
                        VoucherType.FACTURA);

        Voucher voucher = voucherRepository.findBySaleId(sale.getId()).orElseThrow();
        assertThat(voucher.getType()).isEqualTo(VoucherType.FACTURA);
    }

    @Test
    void aFacturaRequestForANonRucBuyerIsRejectedAndNoSaleIsRecorded() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI",
                                        "12345678", VoucherType.FACTURA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Factura requires a RUC buyer document");

        // The rejection must happen before saleRepository.save — there's no @Transactional
        // boundary around this use case to roll a premature save back (found by RDD's
        // R3-voucher-validation-after-sale-persist).
        assertThat(saleRepository.findAll()).isEmpty();
        assertThat(productRepository.findById(activeProduct.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(activeProduct.getStockQuantity());
    }

    @Test
    void aNullVoucherTypeIsRejectedBeforeAnySaleIsRecorded() {
        // RecordSaleRequest's @NotNull keeps this from happening over the REST API, but this use
        // case is called directly by tests and any future caller too, so it must not rely on that
        // DTO-layer guard alone — otherwise a null voucherType reaches Voucher.createNew's own
        // null check AFTER saleRepository.save, leaving the same kind of phantom sale that
        // R3-voucher-validation-after-sale-persist already found for the FACTURA/RUC case (found
        // by its follow-up, R3-null-voucher-type-unguarded).
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI",
                                        "12345678", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Voucher type is required");

        assertThat(saleRepository.findAll()).isEmpty();
        assertThat(productRepository.findById(activeProduct.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(activeProduct.getStockQuantity());
    }

    @Test
    void passesTheRequestedVoucherTypeThroughToIssueVoucherForSaleUseCaseUnchanged() {
        RecordingIssueVoucherForSaleUseCase recordingIssueVoucherForSaleUseCase =
                new RecordingIssueVoucherForSaleUseCase(voucherRepository, invoicingProvider);
        RecordSaleUseCase useCaseWithRecordingIssuance =
                new RecordSaleUseCase(
                        saleRepository, productRepository, recordingIssueVoucherForSaleUseCase, transactionRunner);
        invoicingProvider.alwaysAccept();

        Sale sale =
                useCaseWithRecordingIssuance.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "RUC", "20131312955",
                        VoucherType.FACTURA);

        assertThat(recordingIssueVoucherForSaleUseCase.capturedRequestedType).isEqualTo(VoucherType.FACTURA);
        assertThat(recordingIssueVoucherForSaleUseCase.capturedSale.getId()).isEqualTo(sale.getId());
    }

    @Test
    void commitsTheSaleTheStockDecrementAndAPendingVoucherInOneTransaction() {
        invoicingProvider.alwaysAccept();
        List<String> writtenAtCommit = new ArrayList<>();
        transactionRunner.atCommit(
                () -> {
                    Sale recorded = saleRepository.findAll().get(0);
                    writtenAtCommit.add("sales=" + saleRepository.findAll().size());
                    writtenAtCommit.add(
                            "stock=" + productRepository.findById(activeProduct.getId()).orElseThrow().getStockQuantity());
                    writtenAtCommit.add(
                            "voucher=" + voucherRepository.findBySaleId(recorded.getId()).orElseThrow().getStatus());
                });

        useCase.execute(
                List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678", VoucherType.BOLETA);

        assertThat(transactionRunner.commitCount()).isEqualTo(1);
        assertThat(writtenAtCommit).containsExactly("sales=1", "stock=2", "voucher=PENDING");
    }

    @Test
    void callsTheInvoicingProviderOnlyAfterTheSaleTransactionHasCommitted() {
        List<Boolean> transactionOpenAtIssuance = new ArrayList<>();
        FakeInvoicingProvider probingProvider =
                new FakeInvoicingProvider() {
                    @Override
                    public IssuedVoucher issueBoleta(VoucherRequest request) {
                        transactionOpenAtIssuance.add(transactionRunner.isActive());
                        return super.issueBoleta(request);
                    }
                };
        RecordSaleUseCase useCaseWithProbe =
                new RecordSaleUseCase(
                        saleRepository, productRepository,
                        new IssueVoucherForSaleUseCase(voucherRepository, probingProvider), transactionRunner);

        Sale sale =
                useCaseWithProbe.execute(
                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI", "12345678",
                        VoucherType.BOLETA);

        assertThat(transactionRunner.commitCount()).isEqualTo(1);
        assertThat(transactionOpenAtIssuance).containsExactly(false);
        assertThat(voucherRepository.findBySaleId(sale.getId()).orElseThrow().getStatus())
                .isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void rejectsTheSaleWithoutIssuingWhenAConcurrentSaleTakesTheStockAfterTheAvailabilityCheck() {
        invoicingProvider.alwaysAccept();
        productRepository.loseStockToAConcurrentSaleOnNextDecrement();

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        List.of(new SaleLineItemRequest(activeProduct.getId(), 1)), "DNI",
                                        "12345678", VoucherType.BOLETA))
                .isInstanceOf(InsufficientStockException.class);

        // The transaction is abandoned (a real one rolls back the sale insert) and nothing reaches NubeFacT.
        assertThat(transactionRunner.commitCount()).isZero();
        assertThat(invoicingProvider.issueCallCount()).isZero();
    }

    /**
     * A recording test double for {@link IssueVoucherForSaleUseCase} — a concrete class, not a
     * port, so it's subclassed (rather than mocked) to verify {@code RecordSaleUseCase} forwards
     * the requested {@link VoucherType} unchanged, consistent with this module's existing
     * hand-written fakes (e.g. {@code FakeInvoicingProvider}) instead of adding a mocking
     * dependency to a module that must not depend on any framework.
     */
    private static final class RecordingIssueVoucherForSaleUseCase extends IssueVoucherForSaleUseCase {
        private Sale capturedSale;
        private VoucherType capturedRequestedType;

        RecordingIssueVoucherForSaleUseCase(
                VoucherRepositoryPort voucherRepository, InvoicingProvider invoicingProvider) {
            super(voucherRepository, invoicingProvider);
        }

        @Override
        public Voucher registerPending(Sale sale, VoucherType requestedType) {
            this.capturedSale = sale;
            this.capturedRequestedType = requestedType;
            return super.registerPending(sale, requestedType);
        }
    }
}
