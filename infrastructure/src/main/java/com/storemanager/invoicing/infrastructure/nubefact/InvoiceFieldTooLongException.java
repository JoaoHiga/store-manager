package com.storemanager.invoicing.infrastructure.nubefact;

/**
 * Thrown when an outbound field would exceed the configured OSE's documented length limit (e.g.
 * NubeFacT's {@code descripcion} max of 250 characters, per research.md). Raised by {@link
 * NubeFactInvoicingAdapter} before any HTTP call is made — see spec.md's "Outbound Field
 * Validation" requirement: the system must reject locally instead of sending a request known to
 * be invalid.
 */
public class InvoiceFieldTooLongException extends RuntimeException {

    public InvoiceFieldTooLongException(String fieldName, int maxLength, int actualLength) {
        super(
                "Field '%s' exceeds the invoicing provider's maximum length of %d characters (was %d)"
                        .formatted(fieldName, maxLength, actualLength));
    }
}
