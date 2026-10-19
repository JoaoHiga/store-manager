package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Product;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.VoidSaleUseCase;
import com.storemanager.invoicing.usecase.VoucherNotVoidableException;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CancelSaleUseCaseTest {

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private InMemorySaleRepository saleRepository;
    private InMemoryProductRepository productRepository;
    private RecordingTransactionRunner transactionRunner;
    private CancelSaleUseCase useCase;
    private Product seededProduct;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        saleRepository = new InMemorySaleRepository();
        productRepository = new InMemoryProductRepository();
        transactionRunner = new RecordingTransactionRunner();
        seededProduct =
                productRepository.save(
                        Product.createNew(
                                "iPhone 12", "Usado", 1L, "Apple", new BigDecimal("80"), new BigDecimal("100"), 2,
                                Instant.now()));
        VoidSaleUseCase voidSaleUseCase = new VoidSaleUseCase(voucherRepository, invoicingProvider);
        useCase = new CancelSaleUseCase(voidSaleUseCase, saleRepository, productRepository, transactionRunner);
    }

    private Long seedSale() {
        return saleRepository
                .save(
                        Sale.createNew(
                                List.of(SaleItem.createNew(seededProduct.getId(), "iPhone 12", new BigDecimal("100"), 1)),
                                "DNI", "12345678", Instant.now()))
                .getId();
    }

    private void seedAcceptedVoucher(Long saleId) {
        voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.ACCEPTED, "DNI", "12345678", 0, null,
                        "REF-1", "pdf", "xml", "cdr", "hash", Instant.now(), Instant.now()));
    }

    @Test
    void voidingASaleWithAnAcceptedVoucherVoidsTheVoucherCancelsTheSaleAndRestoresStock() {
        Long saleId = seedSale();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();

        Sale cancelled = useCase.execute(saleId, "Cliente solicito anulacion");

        assertThat(cancelled.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        Voucher voucher = voucherRepository.findBySaleId(saleId).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.VOIDED);
        Product reloadedProduct = productRepository.findById(seededProduct.getId()).orElseThrow();
        assertThat(reloadedProduct.getStockQuantity()).isEqualTo(3);
        assertThat(reloadedProduct.getCostPrice()).isEqualByComparingTo("80");
        assertThat(transactionRunner.commitCount()).isEqualTo(1);
    }

    @Test
    void restoresStockAcrossMultipleLinesIncludingProductsDeactivatedSinceTheSale() {
        Product deactivatedProduct =
                productRepository.save(
                        productRepository
                                .save(
                                        Product.createNew(
                                                "Funda", null, 1L, null, new BigDecimal("10"), new BigDecimal("20"), 0,
                                                Instant.now()))
                                .deactivated(Instant.now()));
        Long saleId =
                saleRepository
                        .save(
                                Sale.createNew(
                                        List.of(
                                                SaleItem.createNew(
                                                        seededProduct.getId(), "iPhone 12", new BigDecimal("100"), 1),
                                                SaleItem.createNew(
                                                        seededProduct.getId(), "iPhone 12", new BigDecimal("100"), 2),
                                                SaleItem.createNew(
                                                        deactivatedProduct.getId(), "Funda", new BigDecimal("20"), 3)),
                                        "DNI",
                                        "12345678",
                                        Instant.now()))
                        .getId();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();

        useCase.execute(saleId, "reason");

        assertThat(productRepository.findById(seededProduct.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(productRepository.findById(deactivatedProduct.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(3);
    }

    @Test
    void nothingIsDeletedOnlyTransitioned() {
        Long saleId = seedSale();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();

        useCase.execute(saleId, "reason");

        assertThat(saleRepository.findById(saleId)).isPresent();
        assertThat(voucherRepository.findBySaleId(saleId)).isPresent();
    }

    @Test
    void voidingANonexistentSaleNeverCallsTheInvoicingProvider() {
        // Fixes RDD review-reliability finding R3-cancel-not-atomic: resolving the Sale must happen
        // BEFORE the irreversible OSE call, so a not-found sale can never leave a VOIDED voucher
        // orphaned from an unchanged COMPLETED sale.
        assertThatThrownBy(() -> useCase.execute(999L, "reason")).isInstanceOf(SaleNotFoundException.class);

        assertThat(invoicingProvider.voidVoucherCallCount()).isZero();
    }

    @Test
    void aSaveFailureAfterAConfirmedVoidRaisesADistinctReconciliationException() {
        // Fixes the remaining half of R3-cancel-not-atomic: voidSaleUseCase.execute() already
        // persisted the voucher as VOIDED (irreversible) before saleRepository.save fails, so the
        // resulting exception must be identifiable as needing manual reconciliation, not a generic
        // propagated RuntimeException indistinguishable from "nothing happened yet".
        Long saleId = seedSale();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();
        saleRepository.failOnNextSave();

        assertThatThrownBy(() -> useCase.execute(saleId, "reason"))
                .isInstanceOf(SaleCancellationInconsistentException.class);

        Voucher voucher = voucherRepository.findBySaleId(saleId).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.VOIDED);
        assertThat(transactionRunner.commitCount()).isZero();
        assertThat(productRepository.findById(seededProduct.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void aMissingProductDuringStockRestoreAbandonsTheTransactionAndRaisesReconciliationException() {
        Long saleId =
                saleRepository
                        .save(
                                Sale.createNew(
                                        List.of(SaleItem.createNew(999L, "Inexistente", new BigDecimal("100"), 1)),
                                        "DNI", "12345678", Instant.now()))
                        .getId();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();

        assertThatThrownBy(() -> useCase.execute(saleId, "reason"))
                .isInstanceOf(SaleCancellationInconsistentException.class);

        assertThat(transactionRunner.commitCount()).isZero();
    }

    @Test
    void voidingASaleWithAPendingVoucherIsRejectedAndTheSaleAndStockStayUnchanged() {
        Long saleId = seedSale();
        voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.PENDING, "DNI", "12345678", 0, null, null,
                        null, null, null, null, Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.execute(saleId, "reason")).isInstanceOf(VoucherNotVoidableException.class);

        assertThat(saleRepository.findById(saleId).orElseThrow().getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(productRepository.findById(seededProduct.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
    }
}
