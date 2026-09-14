package com.storemanager.invoicing.usecase;

/**
 * Thrown when the {@link com.storemanager.invoicing.domain.port.InvoicingProvider} responds to a
 * void request but does not confirm the comunicación de baja ({@code accepted == false}). The
 * voucher is left unchanged (still {@code ACCEPTED}) — per review-reliability finding
 * R3-void-accept-ignored, a rejected/malformed void response must never silently transition the
 * voucher to {@code VOIDED}, since that would desync our record from SUNAT's actual state.
 */
public class VoidRejectedException extends RuntimeException {

    public VoidRejectedException(String oseDescription) {
        super("Void was not confirmed by the invoicing provider: " + oseDescription);
    }
}
