package com.storemanager.purchasing.usecase;

import com.storemanager.shared.domain.port.TransactionRunner;
import java.util.function.Supplier;

/**
 * {@link TransactionRunner} test double, a copy of the {@code sales} one (package-private there). In-memory
 * repositories cannot roll back, so this only records the boundary: how many transactions committed, plus
 * an optional hook that runs at commit time so a test can assert what the transaction wrote.
 */
class RecordingTransactionRunner implements TransactionRunner {

    private int commitCount = 0;
    private Runnable atCommit = () -> {};

    int commitCount() {
        return commitCount;
    }

    void atCommit(Runnable hook) {
        this.atCommit = hook;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        T result = work.get();
        atCommit.run();
        commitCount++;
        return result;
    }
}
