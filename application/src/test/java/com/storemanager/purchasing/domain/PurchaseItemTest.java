package com.storemanager.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PurchaseItemTest {

    @Test
    void computesTheSubtotalAsQuantityTimesUnitCostWithScaleTwo() {
        PurchaseItem item = PurchaseItem.createNew(1L, "Funda", 3, new BigDecimal("4.5"));

        assertThat(item.getUnitCost()).isEqualTo(new BigDecimal("4.50"));
        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("13.50"));
    }

    @Test
    void acceptsAZeroUnitCost() {
        PurchaseItem item = PurchaseItem.createNew(1L, "Funda de regalo", 2, BigDecimal.ZERO);

        assertThat(item.getSubtotal()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void rejectsAQuantityThatIsNotPositive() {
        assertThatThrownBy(() -> PurchaseItem.createNew(1L, "Funda", 0, new BigDecimal("4.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsANegativeOrMissingUnitCost() {
        assertThatThrownBy(() -> PurchaseItem.createNew(1L, "Funda", 1, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit cost");
        assertThatThrownBy(() -> PurchaseItem.createNew(1L, "Funda", 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit cost");
    }

    @Test
    void rejectsAUnitCostWithMoreThanTwoDecimals() {
        assertThatThrownBy(() -> PurchaseItem.createNew(1L, "Funda", 1, new BigDecimal("4.505")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 decimals");
    }

    @Test
    void rejectsAMissingProduct() {
        assertThatThrownBy(() -> PurchaseItem.createNew(null, "Funda", 1, new BigDecimal("4.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId");
    }
}
