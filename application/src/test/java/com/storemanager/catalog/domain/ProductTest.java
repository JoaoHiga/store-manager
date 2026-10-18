package com.storemanager.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductTest {

    private static final Long CATEGORY_ID = 1L;

    @Test
    void createsANewActiveProductWithNoSkuYet() {
        Product product =
                Product.createNew("iPhone 12", null, CATEGORY_ID, null, null, new BigDecimal("350"), 3, Instant.now());

        assertThat(product.getId()).isNull();
        assertThat(product.getSku()).isNull(); // assigned by the persistence adapter, not the domain
        assertThat(product.isActive()).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(
                        () ->
                                Product.createNew(
                                        "  ", null, CATEGORY_ID, null, null, new BigDecimal("10"), 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingCategoryId() {
        assertThatThrownBy(
                        () -> Product.createNew("Cargador", null, null, null, null, new BigDecimal("10"), 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeStockQuantity() {
        assertThatThrownBy(
                        () ->
                                Product.createNew(
                                        "Cargador", null, CATEGORY_ID, null, null, new BigDecimal("10"), -1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAZeroOrNegativeSalePrice() {
        assertThatThrownBy(
                        () ->
                                Product.createNew(
                                        "Cargador", null, CATEGORY_ID, null, null, BigDecimal.ZERO, 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeCostPrice() {
        assertThatThrownBy(
                        () ->
                                Product.createNew(
                                        "Cargador", null, CATEGORY_ID, null, new BigDecimal("-1"), new BigDecimal("10"),
                                        1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsANullOrZeroCostPrice() {
        Product nullCostPrice =
                Product.createNew(
                        "Cargador", null, CATEGORY_ID, null, null, new BigDecimal("10"), 1, Instant.now());
        Product zeroCostPrice =
                Product.createNew(
                        "Cargador", null, CATEGORY_ID, null, BigDecimal.ZERO, new BigDecimal("10"), 1, Instant.now());

        assertThat(nullCostPrice.getCostPrice()).isNull();
        assertThat(zeroCostPrice.getCostPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deactivatingSetsActiveFalseAndKeepsEverythingElse() {
        Instant createdAt = Instant.now();
        Product product =
                Product.reconstitute(
                        1L, "PRD-000001", "iPhone 12", null, CATEGORY_ID, null, null, new BigDecimal("350"), 3, true,
                        createdAt, createdAt);

        Product deactivated = product.deactivated(Instant.now());

        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.getId()).isEqualTo(1L);
        assertThat(deactivated.getSku()).isEqualTo("PRD-000001");
    }

    @Test
    void withUpdatedFieldsNeverChangesIdSkuStockOrCost() {
        Instant createdAt = Instant.now();
        Product product =
                Product.reconstitute(
                        1L, "PRD-000001", "iPhone 12", null, CATEGORY_ID, null, new BigDecimal("250"),
                        new BigDecimal("350"), 3, true, createdAt, createdAt);

        Product updated =
                product.withUpdatedFields(
                        "iPhone 12 (usado)", "Usado, buen estado", CATEGORY_ID, "Apple", new BigDecimal("320"),
                        Instant.now());

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getSku()).isEqualTo("PRD-000001");
        assertThat(updated.getName()).isEqualTo("iPhone 12 (usado)");
        assertThat(updated.getStockQuantity()).isEqualTo(3);
        assertThat(updated.getCostPrice()).isEqualByComparingTo("250");
    }
}
