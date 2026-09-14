package com.storemanager.invoicing.infrastructure.nubefact;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double for {@link CorrelativoAllocator} — keeps {@link NubeFactInvoicingAdapterTest}
 * isolated from a real database. A single monitor lock stands in for {@link
 * JpaCorrelativoAllocator}'s row-level lock: the whole operation runs while holding it.
 */
class InMemoryCorrelativoAllocator implements CorrelativoAllocator {

    private final Map<String, Long> nextCorrelativoBySeries = new HashMap<>();
    private final Object lock = new Object();

    @Override
    public <T> T withCorrelativo(String series, CorrelativoOperation<T> operation) {
        synchronized (lock) {
            long correlativo = nextCorrelativoBySeries.getOrDefault(series, 1L);
            T result = operation.perform(correlativo);
            nextCorrelativoBySeries.put(series, correlativo + 1);
            return result;
        }
    }
}
