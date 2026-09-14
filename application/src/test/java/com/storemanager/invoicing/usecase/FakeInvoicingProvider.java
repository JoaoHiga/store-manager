package com.storemanager.invoicing.usecase;

import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.port.CreditNoteRequest;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.IssuedVoucher;
import com.storemanager.invoicing.domain.port.VoucherRequest;

/**
 * Configurable {@link InvoicingProvider} test double — mirrors {@code
 * InMemoryProductRepository}'s style. Configurable to always accept, always reject with a settable
 * reason, or always throw (simulating a timeout/unreachable OSE), so use-case tests can force every
 * code path deterministically without a real NubeFacT call.
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
