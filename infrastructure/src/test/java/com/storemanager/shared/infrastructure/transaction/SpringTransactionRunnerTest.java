package com.storemanager.shared.infrastructure.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.infrastructure.persistence.CategoryJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.CategoryJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code NOT_SUPPORTED} turns off {@code @DataJpaTest}'s own per-test transaction: otherwise every write
 * would sit inside that outer transaction and a rollback could not be told apart from the test's cleanup.
 * Rows are therefore really committed, so each test uses a unique name and deletes it afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SpringTransactionRunnerTest {

    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CategoryJpaRepository categoryJpaRepository;

    private SpringTransactionRunner runner;
    private String categoryName;

    @BeforeEach
    void setUp() {
        runner = new SpringTransactionRunner(transactionManager);
        categoryName = "tx-" + UUID.randomUUID();
    }

    @AfterEach
    void deleteCommittedRows() {
        categoryJpaRepository.findAll().stream()
                .filter(category -> category.getName().equals(categoryName))
                .forEach(categoryJpaRepository::delete);
    }

    private boolean categoryExists() {
        return categoryJpaRepository.findAll().stream().anyMatch(category -> category.getName().equals(categoryName));
    }

    private CategoryJpaEntity newCategory() {
        return new CategoryJpaEntity(null, categoryName, null, Instant.now(), Instant.now());
    }

    @Test
    void commitsTheWorkAndReturnsItsResult() {
        CategoryJpaEntity saved = runner.inTransaction(() -> categoryJpaRepository.save(newCategory()));

        assertThat(saved.getId()).isNotNull();
        assertThat(categoryExists()).isTrue();
    }

    @Test
    void rollsBackEveryWriteAndRethrowsWhenTheWorkThrows() {
        assertThatThrownBy(
                        () ->
                                runner.inTransaction(
                                        () -> {
                                            categoryJpaRepository.save(newCategory());
                                            throw new IllegalStateException("stock taken by a concurrent sale");
                                        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("stock taken by a concurrent sale");

        assertThat(categoryExists()).isFalse();
    }
}
