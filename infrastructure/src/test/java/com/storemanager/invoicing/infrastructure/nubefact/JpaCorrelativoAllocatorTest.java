package com.storemanager.invoicing.infrastructure.nubefact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

/** Proves durability, safe-retry-on-throw, and (per R3-correlativo-concurrency-race) that concurrent callers never collide. {@code @Import} makes this a real {@code @Transactional}-proxied bean — a plain {@code new} would skip transaction management and hide the bug. */
@DataJpaTest
@Import(JpaCorrelativoAllocator.class)
class JpaCorrelativoAllocatorTest {

    @Autowired private JpaCorrelativoAllocator allocator;

    @Test
    void firstAllocationForASeriesIsOne() {
        long observed = allocator.withCorrelativo("BBB1", correlativo -> correlativo);

        assertThat(observed).isEqualTo(1L);
    }

    @Test
    void secondAllocationForTheSameSeriesContinuesFromThePersistedValue() {
        allocator.withCorrelativo("FFF1", correlativo -> correlativo);

        long second = allocator.withCorrelativo("FFF1", correlativo -> correlativo);

        assertThat(second).isEqualTo(2L);
    }

    @Test
    void aThrownOperationDoesNotAdvanceTheCounter() {
        assertThatThrownBy(
                        () ->
                                allocator.withCorrelativo(
                                        "BBB1",
                                        correlativo -> {
                                            throw new RuntimeException("simulated network failure");
                                        }))
                .isInstanceOf(RuntimeException.class);

        long next = allocator.withCorrelativo("BBB1", correlativo -> correlativo);
        assertThat(next).isEqualTo(1L);
    }

    @Test
    void differentSeriesAreAllocatedIndependently() {
        long boleta1 = allocator.withCorrelativo("BBB1", c -> c);
        long factura1 = allocator.withCorrelativo("FFF1", c -> c);
        long boleta2 = allocator.withCorrelativo("BBB1", c -> c);

        assertThat(boleta1).isEqualTo(1L);
        assertThat(factura1).isEqualTo(1L);
        assertThat(boleta2).isEqualTo(2L);
    }

    @Test
    @DirtiesContext // worker threads commit for real; reset context so no other test sees it
    void twoConcurrentCallersForTheSameSeriesNeverGetTheSameCorrelativo() throws InterruptedException {
        // Each operation holds the lock ~100ms (stands in for the HTTP round-trip) so a
        // released-too-early lock would let both threads see the same correlativo.
        CountDownLatch startTogether = new CountDownLatch(2);
        long[] results = new long[2];
        Runnable task =
                () -> {
                    startTogether.countDown();
                    try {
                        startTogether.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                };
        Thread t1 =
                new Thread(
                        () -> {
                            task.run();
                            results[0] = allocator.withCorrelativo("FFF1", c -> sleepAndReturn(c));
                        });
        Thread t2 =
                new Thread(
                        () -> {
                            task.run();
                            results[1] = allocator.withCorrelativo("FFF1", c -> sleepAndReturn(c));
                        });
        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        assertThat(results[0]).isNotEqualTo(results[1]);
    }

    private static long sleepAndReturn(long value) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return value;
    }
}
