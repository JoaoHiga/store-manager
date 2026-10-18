package com.storemanager.purchasing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.domain.port.ProductRepositoryPort;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.invoicing.domain.port.InvoicingProvider;
import com.storemanager.invoicing.domain.port.VoucherRequest;
import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.domain.port.PurchaseRepositoryPort;
import com.storemanager.purchasing.domain.port.SupplierRepositoryPort;
import com.storemanager.purchasing.usecase.InactiveProductException;
import com.storemanager.purchasing.usecase.PurchaseLineItemRequest;
import com.storemanager.purchasing.usecase.RecordPurchaseUseCase;
import com.storemanager.sales.usecase.RecordSaleUseCase;
import com.storemanager.sales.usecase.SaleLineItemRequest;
import com.storemanager.shared.domain.port.TransactionRunner;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves, against the production database engine, that recording a purchase never loses a concurrent sale's
 * stock change, that a line failing at its stock update rolls the whole purchase back, and that the
 * weighted-average cost rounds the same way it does on H2
 * ({@code ProductRepositoryAdapterTest}). PostgreSQL rather than H2 because row locking and the re-check of a
 * blocked {@code UPDATE} against the newly committed row are exactly what is under test. Real commits
 * (no test-managed transaction), so every test seeds its own uniquely named rows.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PurchaseConcurrencyPostgresTest {

    static {
        // Same docker-java API pin as SaleFlowE2ETest (testcontainers/testcontainers-java#11212).
        System.setProperty("api.version", "1.44");
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String SUPPLIER_RUC = "20131312955";

    @Autowired private RecordPurchaseUseCase recordPurchaseUseCase;
    @Autowired private RecordSaleUseCase recordSaleUseCase;
    @Autowired private ProductRepositoryPort productRepository;
    @Autowired private CategoryRepositoryPort categoryRepository;
    @Autowired private SupplierRepositoryPort supplierRepository;
    @Autowired private PurchaseRepositoryPort purchaseRepository;
    @Autowired private TransactionRunner transactionRunner;

    /** No network: issuance fails and the voucher stays PENDING, which never affects the sale's stock. */
    @MockBean private InvoicingProvider invoicingProvider;

    private Long categoryId;
    private Long supplierId;

    @BeforeEach
    void seed() {
        Mockito.when(invoicingProvider.issueBoleta(ArgumentMatchers.any(VoucherRequest.class)))
                .thenThrow(new IllegalStateException("NubeFacT is not called in this test"));
        categoryId =
                categoryRepository.save(Category.createNew("Celulares-" + UUID.randomUUID(), null, Instant.now())).getId();
        supplierId =
                supplierRepository
                        .findByRuc(SUPPLIER_RUC)
                        .orElseGet(
                                () -> supplierRepository.save(
                                        Supplier.createNew(
                                                "Distribuidora Lima", SUPPLIER_RUC, null, null, null, Instant.now())))
                        .getId();
    }

    private Product product(String costPrice, int stock) {
        return productRepository.save(
                Product.createNew(
                        "iPhone 12", null, categoryId, null, costPrice == null ? null : new BigDecimal(costPrice),
                        new BigDecimal("350"), stock, Instant.now()));
    }

    private Product reload(Product product) {
        return productRepository.findById(product.getId()).orElseThrow();
    }

    private void purchase(Product product, int quantity, String unitCost) {
        recordPurchaseUseCase.execute(
                supplierId, "F-" + UUID.randomUUID().toString().substring(0, 8), LocalDate.now(RecordPurchaseUseCase.STORE_ZONE),
                List.of(new PurchaseLineItemRequest(product.getId(), quantity, new BigDecimal(unitCost))));
    }

    @Test
    void aPurchaseBlockedByAnUncommittedSaleBuildsOnTheSaleInsteadOfOverwritingIt() throws Exception {
        Product phone = product("100.00", 10);
        CountDownLatch saleHoldsTheRow = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // The sale's stock step (the same conditional decrement RecordSaleUseCase runs), kept uncommitted
            // while the purchase reads the product (stock 10) and then tries to update it.
            Future<?> sale =
                    executor.submit(
                            () -> transactionRunner.inTransaction(
                                    () -> {
                                        assertThat(productRepository.decrementStockIfAvailable(phone.getId(), 1, Instant.now()))
                                                .isTrue();
                                        saleHoldsTheRow.countDown();
                                        sleep(500);
                                        return null;
                                    }));
            assertThat(saleHoldsTheRow.await(10, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            purchase(phone, 2, "130.00"); // blocks on the row lock until the sale commits
            long blockedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            sale.get(10, TimeUnit.SECONDS);
            // Proves the two really overlapped: the purchase waited for the sale's transaction to end.
            assertThat(blockedMillis).isGreaterThanOrEqualTo(250);
        } finally {
            executor.shutdownNow();
        }

        Product reloaded = reload(phone);
        // 10 - 1 sold + 2 bought. A read-modify-write purchase would have written 12 over the sale.
        assertThat(reloaded.getStockQuantity()).isEqualTo(11);
        // Averaged over the post-sale stock: (9 * 100 + 2 * 130) / 11 = 105.4545... -> 105.45.
        assertThat(reloaded.getCostPrice()).isEqualTo(new BigDecimal("105.45"));
    }

    @Test
    void concurrentSalesAndPurchasesOnTheSameProductNeverLoseAnUpdate() throws Exception {
        Product phone = product("100.00", 20);
        int rounds = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(rounds * 2);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < rounds; i++) {
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return recordSaleUseCase.execute(
                                            List.of(new SaleLineItemRequest(phone.getId(), 1)), null, null,
                                            VoucherType.BOLETA);
                                }));
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    purchase(phone, 3, "100.00");
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // 20 - 4 * 1 sold + 4 * 3 bought
        assertThat(reload(phone).getStockQuantity()).isEqualTo(28);
    }

    @Test
    void aLineFailingAtItsStockUpdateRollsBackTheWholePurchase() {
        Product phone = product("100.00", 5);
        Product caseProduct = product("4.00", 5);
        // Deactivates the second product right before its stock update, after the use case checked it, so the
        // failure comes after the header and the first line's update were already written.
        ProductRepositoryPort deactivatesTheCaseBeforeItsUpdate =
                new DelegatingProductRepository(productRepository) {
                    @Override
                    public boolean incrementStockAndAverageCost(
                            Long productId, int quantity, BigDecimal unitCost, Instant now) {
                        if (productId.equals(caseProduct.getId())) {
                            productRepository.save(reload(caseProduct).deactivated(now));
                        }
                        return super.incrementStockAndAverageCost(productId, quantity, unitCost, now);
                    }
                };
        RecordPurchaseUseCase useCase =
                new RecordPurchaseUseCase(
                        purchaseRepository, supplierRepository, deactivatesTheCaseBeforeItsUpdate, transactionRunner);
        String invoiceNumber = "F-" + UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(
                        () -> useCase.execute(
                                supplierId, invoiceNumber, LocalDate.now(RecordPurchaseUseCase.STORE_ZONE),
                                List.of(
                                        new PurchaseLineItemRequest(phone.getId(), 2, new BigDecimal("130.00")),
                                        new PurchaseLineItemRequest(caseProduct.getId(), 2, new BigDecimal("5.00")))))
                .isInstanceOf(InactiveProductException.class);

        assertThat(purchaseRepository.existsBySupplierIdAndInvoiceNumber(supplierId, invoiceNumber)).isFalse();
        assertThat(reload(phone).getStockQuantity()).isEqualTo(5);
        assertThat(reload(phone).getCostPrice()).isEqualTo(new BigDecimal("100.00"));
        assertThat(reload(caseProduct).isActive()).isTrue();
    }

    @Test
    void roundsTheWeightedAverageCostHalfUpOnPostgresqlToo() {
        Product halfway = product("1.00", 1);
        Product repeating = product("2.00", 2);
        Product noCost = product(null, 3);

        purchase(halfway, 1, "1.01");
        purchase(repeating, 1, "2.02");
        purchase(noCost, 2, "10.50");

        assertThat(reload(halfway).getCostPrice()).isEqualTo(new BigDecimal("1.01")); // 1.005
        assertThat(reload(repeating).getCostPrice()).isEqualTo(new BigDecimal("2.01")); // 2.00666...
        assertThat(reload(noCost).getCostPrice()).isEqualTo(new BigDecimal("10.50"));
        assertThat(reload(noCost).getStockQuantity()).isEqualTo(5);
    }

    /** Forwards every call to the real adapter; tests override the one call they need to interfere with. */
    private static class DelegatingProductRepository implements ProductRepositoryPort {

        private final ProductRepositoryPort delegate;

        DelegatingProductRepository(ProductRepositoryPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public Product save(Product product) {
            return delegate.save(product);
        }

        @Override
        public Optional<Product> findById(Long id) {
            return delegate.findById(id);
        }

        @Override
        public List<Product> findByActive(boolean active) {
            return delegate.findByActive(active);
        }

        @Override
        public List<Product> findByActiveAndCategoryId(boolean active, Long categoryId) {
            return delegate.findByActiveAndCategoryId(active, categoryId);
        }

        @Override
        public boolean decrementStockIfAvailable(Long productId, int quantity, Instant now) {
            return delegate.decrementStockIfAvailable(productId, quantity, now);
        }

        @Override
        public boolean incrementStockAndAverageCost(Long productId, int quantity, BigDecimal unitCost, Instant now) {
            return delegate.incrementStockAndAverageCost(productId, quantity, unitCost, now);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
