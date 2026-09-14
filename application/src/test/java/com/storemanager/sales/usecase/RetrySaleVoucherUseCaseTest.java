package com.storemanager.sales.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.usecase.RetryPendingVoucherUseCase;
import com.storemanager.invoicing.usecase.VoucherNotFoundException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrySaleVoucherUseCaseTest {

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private InMemorySaleRepository saleRepository;
    private RetrySaleVoucherUseCase useCase;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        saleRepository = new InMemorySaleRepository();
        RetryPendingVoucherUseCase retryPendingVoucherUseCase =
                new RetryPendingVoucherUseCase(voucherRepository, saleRepository, invoicingProvider);
        useCase = new RetrySaleVoucherUseCase(voucherRepository, retryPendingVoucherUseCase);
    }

    private Long seedSale() {
        return saleRepository
                .save(
                        com.storemanager.sales.domain.Sale.createNew(
                                java.util.List.of(
                                        com.storemanager.sales.domain.SaleItem.createNew(
                                                1L, "Usado", new java.math.BigDecimal("100"), 1)),
                                "DNI",
                                "12345678",
                                Instant.now()))
                .getId();
    }

    private void seedPendingVoucher(Long saleId) {
        voucherRepository.save(
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, VoucherStatus.PENDING, "DNI", "12345678", 1,
                        "prior failure", null, null, null, null, null, Instant.now(), Instant.now()));
    }

    @Test
    void retryingBySaleIdResolvesTheVoucherAndRetriesIt() {
        Long saleId = seedSale();
        seedPendingVoucher(saleId);
        invoicingProvider.alwaysAccept();

        Voucher retried = useCase.execute(saleId);

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(retried.getSaleId()).isEqualTo(saleId);
    }

    @Test
    void retryingASaleWithNoVoucherIsRejected() {
        Long saleId = seedSale();

        assertThatThrownBy(() -> useCase.execute(saleId)).isInstanceOf(VoucherNotFoundException.class);
    }
}
