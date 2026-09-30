package com.storemanager.shared.domain.port;

import java.util.function.Supplier;

/**
 * Runs a unit of work atomically: every write made through repository ports inside {@code work} is
 * committed together, or none is if {@code work} throws (the exception propagates unchanged). A port so
 * use cases can demand atomicity without depending on Spring; the infrastructure adapter backs it with a
 * database transaction.
 *
 * <p>Keep remote calls (NubeFacT, any HTTP) out of {@code work}: a transaction held open while waiting on
 * the network locks rows for the whole wait.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
