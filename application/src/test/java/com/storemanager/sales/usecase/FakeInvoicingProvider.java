package com.storemanager.sales.usecase;

import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.CreditNoteRequest;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRequest;

/**
 * Configurable {@link InvoicingProvider} test double — own copy of {@code
 * com.storemanager.invoicing.usecase.FakeInvoicingProvider} (PR2), kept package-private in this
 * package rather than reused across packages because that one is package-private too, and each
 * bounded context/module test-source-root owns its own test doubles (see proposal.md, point 5,
 * and PR2's own copy of {@code InMemorySaleRepository}).
 */
class FakeInvoicingProvider implements InvoicingProvider {

    private enum Mode {
        ACCEPT,
        REJECT,
        THROW
    }

    private Mode mode = Mode.ACCEPT;
    private String rejectionReason = "SUNAT did not accept the voucher";
    private long nextReference = 1;
    private int voidVoucherCallCount = 0;

    int voidVoucherCallCount() {
        return voidVoucherCallCount;
    }

    void alwaysAccept() {
        this.mode = Mode.ACCEPT;
    }

    void alwaysReject(String reason) {
        this.mode = Mode.REJECT;
        this.rejectionReason = reason;
    }

    void alwaysThrow() {
        this.mode = Mode.THROW;
    }

    @Override
    public IssuedVoucher issueBoleta(VoucherRequest request) {
        return issue();
    }

    @Override
    public IssuedVoucher issueFactura(VoucherRequest request) {
        return issue();
    }

    @Override
    public IssuedVoucher issueCreditNote(CreditNoteRequest request) {
        return issue();
    }

    @Override
    public VoucherStatus checkStatus(String voucherReference) {
        if (mode == Mode.THROW) {
            throw new RuntimeException("Simulated OSE timeout while checking status");
        }
        return mode == Mode.ACCEPT ? VoucherStatus.ACCEPTED : VoucherStatus.PENDING;
    }

    @Override
    public IssuedVoucher voidVoucher(String voucherReference, String reason) {
        voidVoucherCallCount++;
        if (mode == Mode.THROW) {
            throw new RuntimeException("Simulated OSE timeout while voiding");
        }
        if (mode == Mode.REJECT) {
            return new IssuedVoucher(false, voucherReference, rejectionReason, "2", null, null, null, null);
        }
        return new IssuedVoucher(true, voucherReference, "Comunicacion de baja aceptada", "0", "hash-void", null, null, null);
    }

    private IssuedVoucher issue() {
        if (mode == Mode.THROW) {
            throw new RuntimeException("Simulated OSE timeout while issuing");
        }
        if (mode == Mode.REJECT) {
            return new IssuedVoucher(false, null, rejectionReason, "2", null, null, null, null);
        }
        String reference = "REF-%06d".formatted(nextReference++);
        return new IssuedVoucher(
                true,
                reference,
                "La factura numero " + reference + " ha sido aceptada",
                "0",
                "hash-" + reference,
                "https://fake-ose.test/pdf/" + reference,
                "https://fake-ose.test/xml/" + reference,
                "https://fake-ose.test/cdr/" + reference);
    }
}
