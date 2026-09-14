package com.storemanager.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaleTest {

    private static SaleItem oneItem() {
        return SaleItem.createNew(1L, "iPhone 12", new BigDecimal("350"), 1);
    }

    @Test
    void createsANewCompletedSaleWithComputedTotal() {
        Sale sale = Sale.createNew(List.of(oneItem()), "DNI", "12345678", Instant.now());

        assertThat(sale.getId()).isNull();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getTotal()).isEqualByComparingTo("350");
        assertThat(sale.getBuyerDocumentType()).isEqualTo("DNI");
        assertThat(sale.getBuyerDocumentNumber()).isEqualTo("12345678");
    }

    @Test
    void sumsSubtotalsOfMultipleLineItemsIntoTheTotal() {
        SaleItem first = SaleItem.createNew(1L, "iPhone 12", new BigDecimal("350"), 2);
        SaleItem second = SaleItem.createNew(2L, "Funda", new BigDecimal("19.90"), 3);

        Sale sale = Sale.createNew(List.of(first, second), "DNI", "12345678", Instant.now());

        assertThat(sale.getTotal()).isEqualByComparingTo("759.70");
    }

    @Test
    void rejectsASaleWithNoLineItems() {
        assertThatThrownBy(() -> Sale.createNew(List.of(), "DNI", "12345678", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingBuyerDocumentType() {
        assertThatThrownBy(() -> Sale.createNew(List.of(oneItem()), null, "12345678", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sale.createNew(List.of(oneItem()), "  ", "12345678", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingBuyerDocumentNumber() {
        assertThatThrownBy(() -> Sale.createNew(List.of(oneItem()), "DNI", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sale.createNew(List.of(oneItem()), "DNI", "  ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstitutesAnExistingSaleKeepingItsIdAndStatus() {
        Instant createdAt = Instant.now();
        Sale sale =
                Sale.reconstitute(
                        5L, List.of(SaleItem.reconstitute(1L, 1L, "iPhone 12", new BigDecimal("350"), 1)), "DNI",
                        "12345678", SaleStatus.COMPLETED, createdAt);

        assertThat(sale.getId()).isEqualTo(5L);
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void itemsAreExposedAsAnImmutableList() {
        Sale sale = Sale.createNew(List.of(oneItem()), "DNI", "12345678", Instant.now());

        assertThatThrownBy(() -> sale.getItems().add(oneItem())).isInstanceOf(UnsupportedOperationException.class);
    }
}
