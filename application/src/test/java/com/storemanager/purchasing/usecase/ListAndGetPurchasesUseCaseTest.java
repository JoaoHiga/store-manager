package com.storemanager.purchasing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListAndGetPurchasesUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private final InMemoryPurchaseRepository purchaseRepository = new InMemoryPurchaseRepository();
    private final ListPurchasesUseCase listUseCase = new ListPurchasesUseCase(purchaseRepository);
    private final GetPurchaseUseCase getUseCase = new GetPurchaseUseCase(purchaseRepository);

    private Purchase save(String invoiceNumber, LocalDate purchaseDate) {
        return purchaseRepository.save(
                Purchase.createNew(
                        1L, "Distribuidora Lima", invoiceNumber, purchaseDate,
                        List.of(PurchaseItem.createNew(1L, "Funda", 1, new BigDecimal("4.50"))), TODAY,
                        Instant.now()));
    }

    @Test
    void listsPurchasesNewestFirst() {
        save("F001-1", TODAY.minusDays(2));
        save("F001-2", TODAY);
        save("F001-3", TODAY.minusDays(1));

        assertThat(listUseCase.execute())
                .extracting(Purchase::getInvoiceNumber)
                .containsExactly("F001-2", "F001-3", "F001-1");
    }

    @Test
    void getsAPurchaseById() {
        Purchase saved = save("F001-1", TODAY);

        assertThat(getUseCase.execute(saved.getId()).getInvoiceNumber()).isEqualTo("F001-1");
    }

    @Test
    void rejectsAnUnknownPurchaseId() {
        assertThatThrownBy(() -> getUseCase.execute(99L))
                .isInstanceOf(PurchaseNotFoundException.class)
                .hasMessageContaining("99");
    }
}
