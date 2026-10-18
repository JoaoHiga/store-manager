package com.storemanager.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PurchaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static PurchaseItem line(long productId, int quantity, String unitCost) {
        return PurchaseItem.createNew(productId, "Product " + productId, quantity, new BigDecimal(unitCost));
    }

    private static Purchase purchase(String invoiceNumber, LocalDate purchaseDate, List<PurchaseItem> items) {
        return Purchase.createNew(1L, "Distribuidora Lima", invoiceNumber, purchaseDate, items, TODAY, Instant.now());
    }

    @Test
    void computesTheTotalAsTheSumOfTheLineSubtotals() {
        Purchase purchase = purchase("F001-123", TODAY, List.of(line(1, 2, "300.00"), line(2, 10, "4.55")));

        assertThat(purchase.getTotal()).isEqualTo(new BigDecimal("645.50"));
        assertThat(purchase.getItems()).hasSize(2);
        assertThat(purchase.getSupplierName()).isEqualTo("Distribuidora Lima");
    }

    @Test
    void stripsSurroundingWhitespaceFromTheInvoiceNumber() {
        Purchase purchase = purchase("  F001-123 ", TODAY, List.of(line(1, 1, "1.00")));

        assertThat(purchase.getInvoiceNumber()).isEqualTo("F001-123");
    }

    @Test
    void rejectsABlankOrMissingInvoiceNumber() {
        assertThatThrownBy(() -> purchase("  ", TODAY, List.of(line(1, 1, "1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice number");
        assertThatThrownBy(() -> purchase(null, TODAY, List.of(line(1, 1, "1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invoice number");
    }

    @Test
    void rejectsAnInvoiceNumberLongerThanThirtyCharacters() {
        assertThatThrownBy(() -> purchase("X".repeat(31), TODAY, List.of(line(1, 1, "1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30");
    }

    @Test
    void acceptsAPurchaseDatedTodayOrInThePast() {
        assertThat(purchase("F001-1", TODAY, List.of(line(1, 1, "1.00"))).getPurchaseDate()).isEqualTo(TODAY);
        assertThat(purchase("F001-2", TODAY.minusDays(30), List.of(line(1, 1, "1.00"))).getPurchaseDate())
                .isEqualTo(TODAY.minusDays(30));
    }

    @Test
    void rejectsAMissingOrFuturePurchaseDate() {
        assertThatThrownBy(() -> purchase("F001-1", null, List.of(line(1, 1, "1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date");
        assertThatThrownBy(() -> purchase("F001-1", TODAY.plusDays(1), List.of(line(1, 1, "1.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
    }

    @Test
    void rejectsAPurchaseWithoutLines() {
        assertThatThrownBy(() -> purchase("F001-1", TODAY, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void rejectsTheSameProductOnTwoLines() {
        assertThatThrownBy(() -> purchase("F001-1", TODAY, List.of(line(1, 1, "1.00"), line(1, 2, "2.00"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void rejectsAMissingSupplier() {
        assertThatThrownBy(
                        () -> Purchase.createNew(
                                null, "Distribuidora Lima", "F001-1", TODAY, List.of(line(1, 1, "1.00")), TODAY,
                                Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supplierId");
    }

    @Test
    void reconstitutingDoesNotRejectADateThatWasValidWhenRecorded() {
        Purchase reloaded =
                Purchase.reconstitute(
                        7L, 1L, "Distribuidora Lima", "F001-1", TODAY.plusDays(1), List.of(line(1, 1, "1.00")),
                        Instant.now());

        assertThat(reloaded.getId()).isEqualTo(7L);
    }
}
