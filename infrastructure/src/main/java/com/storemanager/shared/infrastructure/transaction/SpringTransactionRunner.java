package com.storemanager.shared.infrastructure.transaction;

import com.storemanager.shared.domain.port.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TransactionRunner} backed by Spring's transaction manager: the work runs in one database
 * transaction that commits when it returns and rolls back when it throws. Programmatic
 * ({@link TransactionTemplate}) rather than {@code @Transactional} because use cases live in the Spring-free
 * {@code application} module and cannot carry the annotation.
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
