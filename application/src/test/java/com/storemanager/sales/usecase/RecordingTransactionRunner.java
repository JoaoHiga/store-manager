package com.storemanager.sales.usecase;

import com.storemanager.shared.domain.port.TransactionRunner;
import java.util.function.Supplier;

/**
 * {@link TransactionRunner} test double. In-memory repositories cannot roll back, so this one only records
 * the transaction boundary: whether a transaction is open right now, how many committed, and an optional
 * hook that runs at commit time (after the work, while still "inside") so a test can assert what the
 * transaction wrote before anything that runs after the commit.
 */
class RecordingTransactionRunner implements TransactionRunner {

    private boolean active = false;
    private int commitCount = 0;
    private Runnable atCommit = () -> {};

    boolean isActive() {
        return active;
    }

    int commitCount() {
        return commitCount;
    }

    void atCommit(Runnable hook) {
        this.atCommit = hook;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        active = true;
        try {
            T result = work.get();
            atCommit.run();
            commitCount++;
            return result;
        } finally {
            active = false;
        }
    }
}
