package com.storemanager.invoicing.infrastructure.nubefact;

/**
 * Runs {@code operation} for the next correlativo of {@code series}, holding exclusive access for
 * the WHOLE call (see {@link JpaCorrelativoAllocator}'s row lock) so concurrent callers can never
 * collide. Advances only if {@code operation} returns normally, so a thrown exception leaves the
 * correlativo available for a safe retry.
 */
public interface CorrelativoAllocator {

    <T> T withCorrelativo(String series, CorrelativoOperation<T> operation);

    @FunctionalInterface
    interface CorrelativoOperation<T> {
        T perform(long correlativo);
    }
}
