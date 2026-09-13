package com.storemanager.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Category;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code replace = NONE} is required: otherwise {@code @DataJpaTest} swaps in
 * its own generic embedded datasource instead of the "test" profile's H2
 * (PostgreSQL-compatibility mode) datasource that our Flyway migration
 * actually targets (see application-test.yml).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CategoryRepositoryAdapterTest {

    @Autowired private CategoryJpaRepository jpaRepository;

    private CategoryRepositoryAdapter adapter;

    private CategoryRepositoryAdapter adapter() {
        if (adapter == null) {
            adapter = new CategoryRepositoryAdapter(jpaRepository);
        }
        return adapter;
    }

    @Test
    void savesAndFindsACategoryByName() {
        Category saved = adapter().save(Category.createNew("Celulares", "Telefonos", Instant.now()));

        assertThat(saved.getId()).isNotNull();
        assertThat(adapter().findByName("Celulares")).isPresent();
    }

    @Test
    void listsAllSavedCategories() {
        adapter().save(Category.createNew("Celulares", null, Instant.now()));
        adapter().save(Category.createNew("Accesorios", null, Instant.now()));

        assertThat(adapter().findAll()).hasSize(2);
    }

    @Test
    void returnsEmptyWhenCategoryDoesNotExist() {
        assertThat(adapter().findByName("No existe")).isEmpty();
    }
}
