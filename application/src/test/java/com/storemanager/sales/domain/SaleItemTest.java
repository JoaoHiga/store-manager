package com.storemanager.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SaleItemTest {

    @Test
    void createsANewLineItemComputingItsSubtotal() {
        SaleItem item = SaleItem.createNew(1L, "iPhone 12", new BigDecimal("350"), 2);

        assertThat(item.getId()).isNull();
        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getDescription()).isEqualTo("iPhone 12");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("350");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getSubtotal()).isEqualByComparingTo("700");
    }

    @Test
    void rejectsAZeroQuantity() {
        assertThatThrownBy(() -> SaleItem.createNew(1L, "iPhone 12", new BigDecimal("350"), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeQuantity() {
        assertThatThrownBy(() -> SaleItem.createNew(1L, "iPhone 12", new BigDecimal("350"), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingProductId() {
        assertThatThrownBy(() -> SaleItem.createNew(null, "iPhone 12", new BigDecimal("350"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAZeroOrNegativeUnitPrice() {
        assertThatThrownBy(() -> SaleItem.createNew(1L, "iPhone 12", BigDecimal.ZERO, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaleItem.createNew(1L, "iPhone 12", new BigDecimal("-10"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void computesADifferentSubtotalForDifferentQuantitiesAndPrices() {
        SaleItem item = SaleItem.createNew(2L, "Funda", new BigDecimal("19.90"), 3);

        assertThat(item.getSubtotal()).isEqualByComparingTo("59.70");
    }

    @Test
    void reconstitutesAnExistingLineItemKeepingItsId() {
        SaleItem item = SaleItem.reconstitute(10L, 1L, "iPhone 12", new BigDecimal("350"), 2);

        assertThat(item.getId()).isEqualTo(10L);
        assertThat(item.getSubtotal()).isEqualByComparingTo("700");
    }
}
