package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private CancelSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        saleRepository = new InMemorySaleRepository();
        VoidSaleUseCase voidSaleUseCase = new VoidSaleUseCase(voucherRepository, invoicingProvider);
        useCase = new CancelSaleUseCase(voidSaleUseCase, saleRepository);
    }

    private Long seedSale() {
        return saleRepository
                .save(
                        Sale.createNew(
                                List.of(SaleItem.createNew(1L, "Usado", new BigDecimal("100"), 1)), "DNI",
                                "12345678", Instant.now()))
                .getId();
    }

    private void seedAcceptedVoucher(Long saleId) {
        voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.ACCEPTED, "DNI", "12345678", 0, null,
                        "REF-1", "pdf", "xml", "cdr", "hash", Instant.now(), Instant.now()));
    }

    @Test
    void voidingASaleWithAnAcceptedVoucherVoidsTheVoucherAndCancelsTheSale() {
        Long saleId = seedSale();
        seedAcceptedVoucher(saleId);
        invoicingProvider.alwaysAccept();

        Sale cancelled = useCase.execute(saleId, "Cliente solicito anulacion");

        assertThat(cancelled.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        Voucher voucher = voucherRepository.findBySaleId(saleId).orElseThrow();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.VOIDED);
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
    }

    @Test
    void voidingASaleWithAPendingVoucherIsRejectedAndTheSaleStaysCompleted() {
        Long saleId = seedSale();
        voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.PENDING, "DNI", "12345678", 0, null, null,
                        null, null, null, null, Instant.now(), Instant.now()));

        assertThatThrownBy(() -> useCase.execute(saleId, "reason")).isInstanceOf(VoucherNotVoidableException.class);

        assertThat(saleRepository.findById(saleId).orElseThrow().getStatus()).isEqualTo(SaleStatus.COMPLETED);
    }
}
