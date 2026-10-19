package com.storemanager.catalog.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductRepositoryAdapterTest {

    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;
    @Autowired private TestEntityManager entityManager;

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
                                "iPhone 12", null, categoryId, null, new BigDecimal("320"), Instant.now()));

        assertThat(updated.getSku()).isEqualTo(saved.getSku());
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

        var result = adapter.findByActiveAndCategoryId(true, categoryId);

        assertThat(result).extracting(Product::getId).containsExactly(active.getId());
    }

    @Test
    void findsOnlyActiveProductsAcrossAllCategoriesWhenNoCategoryFilterIsGiven() {
        Long otherCategoryId =
                categoryJpaRepository.save(new CategoryJpaEntity(null, "Accesorios", null, Instant.now(), Instant.now()))
                        .getId();
        Product active =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));
        Product activeOther =
                adapter.save(
                        Product.createNew(
                                "Funda", null, otherCategoryId, null, null, new BigDecimal("10"), 5, Instant.now()));
        Product toDeactivate =
                adapter.save(
                        Product.createNew("iPhone 13", null, categoryId, null, null, new BigDecimal("400"), 2, Instant.now()));
        adapter.save(toDeactivate.deactivated(Instant.now()));

        var result = adapter.findByActive(true);

        assertThat(result).extracting(Product::getId).containsExactlyInAnyOrder(active.getId(), activeOther.getId());
    }

    @Test
    void decrementsStockWhenEnoughIsAvailable() {
        Product saved =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));

        boolean decremented = adapter.decrementStockIfAvailable(saved.getId(), 2, Instant.now());

        assertThat(decremented).isTrue();
        assertThat(adapter.findById(saved.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
    }

    @Test
    void leavesStockUntouchedWhenLessThanTheRequestedQuantityIsAvailable() {
        Product saved =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));

        boolean decremented = adapter.decrementStockIfAvailable(saved.getId(), 4, Instant.now());

        assertThat(decremented).isFalse();
        assertThat(adapter.findById(saved.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
    }

    private Product productWith(String costPrice, int stock) {
        BigDecimal cost = costPrice == null ? null : new BigDecimal(costPrice);
        return adapter.save(
                Product.createNew("iPhone 12", null, categoryId, null, cost, new BigDecimal("350"), stock, Instant.now()));
    }

    private Product incrementAndReload(Product product, int quantity, String unitCost) {
        assertThat(adapter.incrementStockAndAverageCost(product.getId(), quantity, new BigDecimal(unitCost), Instant.now()))
                .isTrue();
        return adapter.findById(product.getId()).orElseThrow();
    }

    @Test
    void incrementsStockAndAveragesTheCostWeightedByQuantity() {
        Product reloaded = incrementAndReload(productWith("300.00", 3), 2, "330.00");

        // (3 * 300 + 2 * 330) / 5
        assertThat(reloaded.getStockQuantity()).isEqualTo(5);
        assertThat(reloaded.getCostPrice()).isEqualByComparingTo("312.00");
    }

    @Test
    void takesTheUnitCostWhenTheProductHasNoCostPriceYet() {
        Product reloaded = incrementAndReload(productWith(null, 3), 2, "10.50");

        assertThat(reloaded.getStockQuantity()).isEqualTo(5);
        assertThat(reloaded.getCostPrice()).isEqualByComparingTo("10.50");
    }

    @Test
    void takesTheUnitCostWhenTheProductHasNoStock() {
        Product reloaded = incrementAndReload(productWith("50.00", 0), 4, "30.00");

        assertThat(reloaded.getStockQuantity()).isEqualTo(4);
        assertThat(reloaded.getCostPrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void roundsTheAverageCostHalfUpToTwoDecimals() {
        // (1 * 1.00 + 1 * 1.01) / 2 = 1.005: half-up gives 1.01 (half-even or truncation would give 1.00).
        assertThat(incrementAndReload(productWith("1.00", 1), 1, "1.01").getCostPrice()).isEqualTo(new BigDecimal("1.01"));
        // (2 * 2.00 + 1 * 2.02) / 3 = 2.00666...: rounds to 2.01, so the division is not truncated to 2 decimals.
        assertThat(incrementAndReload(productWith("2.00", 2), 1, "2.02").getCostPrice()).isEqualTo(new BigDecimal("2.01"));
        // (3 * 10.00 + 1 * 10.01) / 4 = 10.0025: rounds down to 10.00.
        assertThat(incrementAndReload(productWith("10.00", 3), 1, "10.01").getCostPrice())
                .isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void refusesToIncrementAnInactiveOrMissingProduct() {
        Product inactive = adapter.save(productWith("300.00", 3).deactivated(Instant.now()));

        assertThat(adapter.incrementStockAndAverageCost(inactive.getId(), 2, new BigDecimal("330.00"), Instant.now()))
                .isFalse();
        assertThat(adapter.incrementStockAndAverageCost(999_999L, 2, new BigDecimal("330.00"), Instant.now())).isFalse();
        Product reloaded = adapter.findById(inactive.getId()).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(3);
        assertThat(reloaded.getCostPrice()).isEqualByComparingTo("300.00");
    }

    @Test
    void savingAStaleProductNeverOverwritesStockChangedConcurrently() {
        Product saved =
                adapter.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 3, Instant.now()));
        // A sale lands between the edit loading the product (stock 3) and saving it.
        adapter.decrementStockIfAvailable(saved.getId(), 2, Instant.now());

        adapter.save(
                Product.reconstitute(
                        saved.getId(), saved.getSku(), "iPhone 12 (usado)", null, categoryId, null, null,
                        new BigDecimal("320"), saved.getStockQuantity(), true, saved.getCreatedAt(), Instant.now()));
        entityManager.flush();
        entityManager.clear();

        Product reloaded = adapter.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("iPhone 12 (usado)");
        assertThat(reloaded.getStockQuantity()).isEqualTo(1);
    }

    @Test
    void savingAStaleProductNeverOverwritesCostChangedByAPurchase() {
        Product saved = productWith("300.00", 3);
        // A purchase lands between the edit loading the product (cost 300) and saving it.
        adapter.incrementStockAndAverageCost(saved.getId(), 2, new BigDecimal("330.00"), Instant.now());

        adapter.save(
                Product.reconstitute(
                        saved.getId(), saved.getSku(), "iPhone 12 (usado)", null, categoryId, null,
                        saved.getCostPrice(), new BigDecimal("320"), saved.getStockQuantity(), true,
                        saved.getCreatedAt(), Instant.now()));
        entityManager.flush();
        entityManager.clear();

        Product reloaded = adapter.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("iPhone 12 (usado)");
        assertThat(reloaded.getCostPrice()).isEqualByComparingTo("312.00");
        assertThat(reloaded.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void savingAStaleProductReturnsThePersistedStockAndCostNotTheStaleOnes() {
        Product loadedForEdit = productWith("300.00", 3);
        // A purchase and a sale land between the edit loading the product (stock 3, cost 300) and saving it.
        adapter.incrementStockAndAverageCost(loadedForEdit.getId(), 2, new BigDecimal("330.00"), Instant.now());
        adapter.decrementStockIfAvailable(loadedForEdit.getId(), 1, Instant.now());

        Product saved =
                adapter.save(
                        loadedForEdit.withUpdatedFields(
                                "iPhone 12 (usado)", null, categoryId, null, new BigDecimal("320"), Instant.now()));

        // What the PUT response shows: the edited fields plus the stock and cost as they are in the database.
        assertThat(saved.getName()).isEqualTo("iPhone 12 (usado)");
        assertThat(saved.getSalePrice()).isEqualByComparingTo("320");
        assertThat(saved.getStockQuantity()).isEqualTo(4);
        assertThat(saved.getCostPrice()).isEqualByComparingTo("312.00");
    }

    @Test
    void incrementsStockForActiveAndInactiveProductsWithoutChangingCost() {
        Product active = productWith("300.00", 3);
        Product inactive = adapter.save(productWith("200.00", 1).deactivated(Instant.now()));

        assertThat(adapter.incrementStock(active.getId(), 2, Instant.now())).isTrue();
        assertThat(adapter.incrementStock(inactive.getId(), 4, Instant.now())).isTrue();
        assertThat(adapter.incrementStock(999_999L, 1, Instant.now())).isFalse();

        Product reloadedActive = adapter.findById(active.getId()).orElseThrow();
        assertThat(reloadedActive.getStockQuantity()).isEqualTo(5);
        assertThat(reloadedActive.getCostPrice()).isEqualByComparingTo("300.00");

        Product reloadedInactive = adapter.findById(inactive.getId()).orElseThrow();
        assertThat(reloadedInactive.getStockQuantity()).isEqualTo(5);
        assertThat(reloadedInactive.getCostPrice()).isEqualByComparingTo("200.00");
    }
}
