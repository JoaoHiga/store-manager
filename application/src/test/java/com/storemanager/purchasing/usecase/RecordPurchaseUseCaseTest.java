package com.storemanager.purchasing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Product;
import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.Supplier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecordPurchaseUseCaseTest {

    private static final LocalDate TODAY = LocalDate.now(RecordPurchaseUseCase.STORE_ZONE);

    private final InMemorySupplierRepository supplierRepository = new InMemorySupplierRepository();
    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final InMemoryPurchaseRepository purchaseRepository = new InMemoryPurchaseRepository();
    private final RecordingTransactionRunner transactionRunner = new RecordingTransactionRunner();
    private final RecordPurchaseUseCase useCase =
            new RecordPurchaseUseCase(purchaseRepository, supplierRepository, productRepository, transactionRunner);

    private Supplier supplier;
    private Product phone;
    private Product caseProduct;

    @BeforeEach
    void seed() {
        supplier =
                supplierRepository.save(
                        Supplier.createNew("Distribuidora Lima", "20131312955", null, null, null, Instant.now()));
        phone =
                productRepository.save(
                        Product.createNew(
                                "iPhone 12", null, 1L, "Apple", new BigDecimal("300.00"), new BigDecimal("350"), 3,
                                Instant.now()));
        caseProduct =
                productRepository.save(
                        Product.createNew("Funda", null, 1L, null, null, new BigDecimal("19.90"), 0, Instant.now()));
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    @Test
    void recordsAPurchaseSnapshottingNamesAndComputingTheTotal() {
        Purchase purchase =
                useCase.execute(
                        supplier.getId(), "F001-123", TODAY,
                        List.of(
                                new PurchaseLineItemRequest(phone.getId(), 2, new BigDecimal("330.00")),
                                new PurchaseLineItemRequest(caseProduct.getId(), 10, new BigDecimal("4.50"))));

        assertThat(purchase.getId()).isNotNull();
        assertThat(purchase.getSupplierName()).isEqualTo("Distribuidora Lima");
        assertThat(purchase.getTotal()).isEqualTo(new BigDecimal("705.00"));
        assertThat(purchase.getItems()).extracting(item -> item.getProductName()).containsExactly("iPhone 12", "Funda");
        assertThat(purchaseRepository.findById(purchase.getId())).isPresent();
    }

    @Test
    void incrementsStockAndSetsAWeightedAverageCostPerProduct() {
        useCase.execute(
                supplier.getId(), "F001-123", TODAY,
                List.of(
                        new PurchaseLineItemRequest(phone.getId(), 2, new BigDecimal("330.00")),
                        new PurchaseLineItemRequest(caseProduct.getId(), 10, new BigDecimal("4.50"))));

        // (3 * 300 + 2 * 330) / 5 = 312
        assertThat(reload(phone).getStockQuantity()).isEqualTo(5);
        assertThat(reload(phone).getCostPrice()).isEqualByComparingTo("312.00");
        // Zero stock and no previous cost: the unit cost becomes the cost price.
        assertThat(reload(caseProduct).getStockQuantity()).isEqualTo(10);
        assertThat(reload(caseProduct).getCostPrice()).isEqualByComparingTo("4.50");
    }

    @Test
    void writesEverythingInsideOneTransaction() {
        transactionRunner.atCommit(
                () -> {
                    assertThat(purchaseRepository.findAll()).hasSize(1);
                    assertThat(reload(phone).getStockQuantity()).isEqualTo(4);
                });

        useCase.execute(
                supplier.getId(), "F001-123", TODAY,
                List.of(new PurchaseLineItemRequest(phone.getId(), 1, new BigDecimal("300.00"))));

        assertThat(transactionRunner.commitCount()).isEqualTo(1);
    }

    @Test
    void rejectsAnUnknownSupplier() {
        assertThatThrownBy(
                        () -> useCase.execute(
                                99L, "F001-123", TODAY,
                                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void rejectsAnInactiveSupplier() {
        supplierRepository.save(supplier.deactivated(Instant.now()));

        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY,
                                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(InactiveSupplierException.class)
                .hasMessageContaining("not active");
        assertThat(purchaseRepository.findAll()).isEmpty();
        assertThat(reload(phone).getStockQuantity()).isEqualTo(3);
    }

    @Test
    void rejectsAnUnknownProduct() {
        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY,
                                List.of(new PurchaseLineItemRequest(99L, 1, BigDecimal.ONE))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void rejectsAnInactiveProductWithoutTouchingAnyStock() {
        productRepository.save(caseProduct.deactivated(Instant.now()));

        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY,
                                List.of(
                                        new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE),
                                        new PurchaseLineItemRequest(caseProduct.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(InactiveProductException.class);
        assertThat(purchaseRepository.findAll()).isEmpty();
        assertThat(reload(phone).getStockQuantity()).isEqualTo(3);
    }

    @Test
    void failsTheWholePurchaseWhenAProductIsDeactivatedBeforeItsStockUpdate() {
        productRepository.deactivateBeforeNextIncrement(caseProduct.getId());

        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY,
                                List.of(
                                        new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE),
                                        new PurchaseLineItemRequest(caseProduct.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(InactiveProductException.class);
        // The in-memory doubles cannot roll back; the real rollback is covered by the infrastructure tests.
        // What matters here is that the failure escapes the transaction, so the runner never commits it.
        assertThat(transactionRunner.commitCount()).isZero();
    }

    @Test
    void rejectsADuplicateInvoiceNumberForTheSameSupplier() {
        useCase.execute(
                supplier.getId(), "F001-123", TODAY,
                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE)));

        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), " F001-123 ", TODAY,
                                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(DuplicatePurchaseInvoiceException.class)
                .hasMessageContaining("F001-123");
        assertThat(reload(phone).getStockQuantity()).isEqualTo(4);
    }

    @Test
    void allowsTheSameInvoiceNumberFromAnotherSupplier() {
        Supplier other =
                supplierRepository.save(
                        Supplier.createNew("Alfa Accesorios", "20100000009", null, null, null, Instant.now()));
        useCase.execute(
                supplier.getId(), "F001-123", TODAY,
                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE)));

        Purchase second =
                useCase.execute(
                        other.getId(), "F001-123", TODAY,
                        List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE)));

        assertThat(second.getId()).isNotNull();
    }

    @Test
    void rejectsAFuturePurchaseDate() {
        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY.plusDays(1),
                                List.of(new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("future");
        assertThat(reload(phone).getStockQuantity()).isEqualTo(3);
    }

    @Test
    void rejectsTheSameProductTwiceWithoutTouchingStock() {
        assertThatThrownBy(
                        () -> useCase.execute(
                                supplier.getId(), "F001-123", TODAY,
                                List.of(
                                        new PurchaseLineItemRequest(phone.getId(), 1, BigDecimal.ONE),
                                        new PurchaseLineItemRequest(phone.getId(), 2, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
        assertThat(reload(phone).getStockQuantity()).isEqualTo(3);
    }
}
