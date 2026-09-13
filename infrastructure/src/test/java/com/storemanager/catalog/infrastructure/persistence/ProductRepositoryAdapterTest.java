package com.storemanager.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductRepositoryAdapterTest {

    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;

    private ProductRepositoryAdapter adapter;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        adapter = new ProductRepositoryAdapter(productJpaRepository);
        Instant now = Instant.now();
        categoryId =
                categoryJpaRepository.save(new CategoryJpaEntity(null, "Celulares", null, now, now)).getId();
    }

    @Test
    void assignsAUniqueSkuOnFirstSave() {
        Product saved =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSku()).matches("PRD-\\d{6}");
    }

    @Test
    void keepsTheSameSkuOnUpdate() {
        Product saved =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));

        Product updated =
                adapter.save(
                        saved.withUpdatedFields(
                                "iPhone 12", null, categoryId, null, null, new BigDecimal("320"), 5, Instant.now()));

        assertThat(updated.getSku()).isEqualTo(saved.getSku());
        assertThat(updated.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void findsOnlyActiveProductsByCategory() {
        Product active =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));
        Product toDeactivate =
                adapter.save(
                        Product.createNew("iPhone 13", null, categoryId, null, null, new BigDecimal("400"), 2, Instant.now()));
        adapter.save(toDeactivate.deactivated(Instant.now()));

        var result = adapter.findActiveByCategoryId(categoryId);

        assertThat(result).extracting(Product::getId).containsExactly(active.getId());
    }
}
