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
    void allowsNoBuyerDocumentWhenTotalIsBelowTheBoletaSimpleThreshold() {
        SaleItem cheapItem = SaleItem.createNew(1L, "Funda", new BigDecimal("50"), 1);

        Sale sale = Sale.createNew(List.of(cheapItem), null, null, Instant.now());

        assertThat(sale.getBuyerDocumentType()).isNull();
        assertThat(sale.getBuyerDocumentNumber()).isNull();
        assertThat(sale.getTotal()).isEqualByComparingTo("50");
    }

    @Test
    void rejectsNoBuyerDocumentWhenTotalIsAtOrAboveTheBoletaSimpleThreshold() {
        SaleItem expensiveItem = SaleItem.createNew(1L, "iPhone 12", new BigDecimal("700"), 1);

        assertThatThrownBy(() -> Sale.createNew(List.of(expensiveItem), null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMalformedDniEvenWhenTotalIsBelowTheThreshold() {
        SaleItem cheapItem = SaleItem.createNew(1L, "Funda", new BigDecimal("50"), 1);

        assertThatThrownBy(() -> Sale.createNew(List.of(cheapItem), "DNI", "123", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucWithAnInvalidCheckDigitEvenAboveTheThreshold() {
        SaleItem expensiveItem = SaleItem.createNew(1L, "iPhone 12", new BigDecimal("900"), 1);

        assertThatThrownBy(
                        () -> Sale.createNew(List.of(expensiveItem), "RUC", "20131312950", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsARucWithAValidCheckDigitAboveTheThreshold() {
        SaleItem expensiveItem = SaleItem.createNew(1L, "iPhone 12", new BigDecimal("900"), 1);

        Sale sale = Sale.createNew(List.of(expensiveItem), "RUC", "20131312955", Instant.now());

        assertThat(sale.getBuyerDocumentType()).isEqualTo("RUC");
        assertThat(sale.getBuyerDocumentNumber()).isEqualTo("20131312955");
    }

    @Test
    void reconstituteAcceptsALegacyMalformedBuyerDocumentWithoutThrowing() {
        // "00165490fddfgdsgdf" is a real legacy sales.id = 1 value that predates
        // buyer-identification-hardening's RUC format validation (design.md, Migration/Rollout).
        Sale sale =
                Sale.reconstitute(
                        1L,
                        List.of(SaleItem.reconstitute(1L, 1L, "iPhone 12", new BigDecimal("900"), 1)),
                        "RUC",
                        "00165490fddfgdsgdf",
                        SaleStatus.COMPLETED,
                        Instant.now());

        assertThat(sale.getBuyerDocumentType()).isEqualTo("RUC");
        assertThat(sale.getBuyerDocumentNumber()).isEqualTo("00165490fddfgdsgdf");
    }

    @Test
    void createNewStillRejectsTheSameMalformedBuyerDocumentThatReconstituteAccepts() {
        assertThatThrownBy(
                        () ->
                                Sale.createNew(
                                        List.of(SaleItem.createNew(1L, "iPhone 12", new BigDecimal("900"), 1)),
                                        "RUC",
                                        "00165490fddfgdsgdf",
                                        Instant.now()))
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

    @Test
    void cancellingACompletedSaleTransitionsItToCancelledPreservingItsOtherFields() {
        Sale sale = Sale.createNew(List.of(oneItem()), "DNI", "12345678", Instant.now());

        Sale cancelled = sale.cancelled(Instant.now());

        assertThat(cancelled.getStatus()).isEqualTo(SaleStatus.CANCELLED);
        assertThat(cancelled.getId()).isEqualTo(sale.getId());
        assertThat(cancelled.getTotal()).isEqualByComparingTo(sale.getTotal());
        assertThat(cancelled.getItems()).isEqualTo(sale.getItems());
        assertThat(cancelled.getBuyerDocumentType()).isEqualTo(sale.getBuyerDocumentType());
        assertThat(cancelled.getBuyerDocumentNumber()).isEqualTo(sale.getBuyerDocumentNumber());
        assertThat(cancelled.getCreatedAt()).isEqualTo(sale.getCreatedAt());
    }
}
