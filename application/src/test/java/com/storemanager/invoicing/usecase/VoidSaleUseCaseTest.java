package com.storemanager.invoicing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoidSaleUseCaseTest {

    private FakeInvoicingProvider invoicingProvider;
    private InMemoryVoucherRepository voucherRepository;
    private VoidSaleUseCase useCase;

    @BeforeEach
    void setUp() {
        invoicingProvider = new FakeInvoicingProvider();
        voucherRepository = new InMemoryVoucherRepository();
        useCase = new VoidSaleUseCase(voucherRepository, invoicingProvider);
    }

    private Voucher seedVoucher(Long saleId, VoucherStatus status) {
        Voucher voucher =
                Voucher.reconstitute(
                        null, saleId, VoucherType.BOLETA, status, "DNI", "12345678", 0, null, "REF-1", "pdf", "xml",
                        "cdr", "hash", Instant.now(), Instant.now());
        return voucherRepository.save(voucher);
    }

    @Test
    void voidingAnAcceptedVoucherTransitionsToVoided() {
        Voucher voucher = seedVoucher(1L, VoucherStatus.ACCEPTED);
        invoicingProvider.alwaysAccept();

        Voucher voided = useCase.execute(1L, "Cliente solicito anulacion");

        assertThat(voided.getStatus()).isEqualTo(VoucherStatus.VOIDED);
        assertThat(voided.getId()).isEqualTo(voucher.getId());
    }

    @Test
    void voidRejectedByTheOseDoesNotTransitionTheVoucher() {
        seedVoucher(4L, VoucherStatus.ACCEPTED);
        invoicingProvider.alwaysReject("SUNAT no acepto la baja");

        assertThatThrownBy(() -> useCase.execute(4L, "reason")).isInstanceOf(VoidRejectedException.class);

        Voucher unchanged = voucherRepository.findBySaleId(4L).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void voidingAPendingVoucherIsRejected() {
        seedVoucher(2L, VoucherStatus.PENDING);

        assertThatThrownBy(() -> useCase.execute(2L, "reason"))
                .isInstanceOf(VoucherNotVoidableException.class);
    }

    @Test
    void voidingANeedsAttentionVoucherIsRejected() {
        seedVoucher(3L, VoucherStatus.NEEDS_ATTENTION);

        assertThatThrownBy(() -> useCase.execute(3L, "reason"))
                .isInstanceOf(VoucherNotVoidableException.class);
    }

    @Test
    void voidingASaleWithNoVoucherIsRejected() {
        assertThatThrownBy(() -> useCase.execute(999L, "reason")).isInstanceOf(VoucherNotFoundException.class);
    }
}
