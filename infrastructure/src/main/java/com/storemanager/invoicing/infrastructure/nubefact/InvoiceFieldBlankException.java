package com.storemanager.invoicing.infrastructure.nubefact;

/**
 * Thrown when an outbound field required by the configured OSE is null or blank (e.g. NubeFacT's
 * item {@code descripcion}, which NubeFacT rejects with code 21). Raised by {@link
 * NubeFactInvoicingAdapter} before any HTTP call is made — see spec.md's "Outbound Field
 * Validation" requirement: the system must reject locally instead of sending a request known to
 * be invalid.
 */
public class InvoiceFieldBlankException extends RuntimeException {

    public InvoiceFieldBlankException(String fieldName) {
        super("Field '%s' must not be blank".formatted(fieldName));
    }
}
