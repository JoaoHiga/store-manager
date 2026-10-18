package com.storemanager.purchasing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Product;
import com.storemanager.catalog.infrastructure.persistence.CategoryJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.CategoryJpaRepository;
import com.storemanager.catalog.infrastructure.persistence.ProductJpaRepository;
import com.storemanager.catalog.infrastructure.persistence.ProductRepositoryAdapter;
import com.storemanager.purchasing.domain.Purchase;
import com.storemanager.purchasing.domain.PurchaseItem;
import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.usecase.DuplicatePurchaseInvoiceException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** {@code replace = NONE}: see {@code CategoryRepositoryAdapterTest} for why the test profile's H2 is required. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PurchaseRepositoryAdapterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Autowired private PurchaseJpaRepository purchaseJpaRepository;
    @Autowired private SupplierJpaRepository supplierJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;
    @Autowired private TestEntityManager entityManager;

    private PurchaseRepositoryAdapter adapter;
    private Supplier supplier;
    private Product phone;
    private Product caseProduct;

    @BeforeEach
    void setUp() {
        adapter = new PurchaseRepositoryAdapter(purchaseJpaRepository);
        supplier =
                new SupplierRepositoryAdapter(supplierJpaRepository)
                        .save(Supplier.createNew("Distribuidora Lima", "20131312955", null, null, null, Instant.now()));
        Long categoryId =
                categoryJpaRepository
                        .save(new CategoryJpaEntity(null, "Celulares", null, Instant.now(), Instant.now()))
                        .getId();
        ProductRepositoryAdapter products = new ProductRepositoryAdapter(productJpaRepository);
        phone =
                products.save(
                        Product.createNew("iPhone 12", null, categoryId, null, null, new BigDecimal("350"), 0, Instant.now()));
        caseProduct =
                products.save(
                        Product.createNew("Funda", null, categoryId, null, null, new BigDecimal("19.90"), 0, Instant.now()));
    }

    private Purchase newPurchase(String invoiceNumber, LocalDate purchaseDate, Long productId) {
        return Purchase.createNew(
                supplier.getId(), supplier.getName(), invoiceNumber, purchaseDate,
                List.of(PurchaseItem.createNew(productId, "iPhone 12", 2, new BigDecimal("330.00"))), TODAY,
                Instant.now());
    }

    @Test
    void savesAndReloadsAPurchaseWithItsLines() {
        Purchase saved =
                adapter.save(
                        Purchase.createNew(
                                supplier.getId(), supplier.getName(), "F001-123", TODAY,
                                List.of(
                                        PurchaseItem.createNew(phone.getId(), "iPhone 12", 2, new BigDecimal("330.00")),
                                        PurchaseItem.createNew(caseProduct.getId(), "Funda", 10, new BigDecimal("4.50"))),
                                TODAY, Instant.now()));
        entityManager.flush();
        entityManager.clear();

        Purchase reloaded = adapter.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getSupplierId()).isEqualTo(supplier.getId());
        assertThat(reloaded.getSupplierName()).isEqualTo("Distribuidora Lima");
        assertThat(reloaded.getInvoiceNumber()).isEqualTo("F001-123");
        assertThat(reloaded.getPurchaseDate()).isEqualTo(TODAY);
        assertThat(reloaded.getTotal()).isEqualByComparingTo("705.00");
        assertThat(reloaded.getItems())
                .extracting(PurchaseItem::getProductName, PurchaseItem::getQuantity)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("iPhone 12", 2),
                        org.assertj.core.groups.Tuple.tuple("Funda", 10));
        assertThat(reloaded.getItems()).allSatisfy(item -> assertThat(item.getId()).isNotNull());
    }

    @Test
    void listsPurchasesNewestFirst() {
        adapter.save(newPurchase("F001-1", TODAY.minusDays(2), phone.getId()));
        adapter.save(newPurchase("F001-2", TODAY, phone.getId()));
        adapter.save(newPurchase("F001-3", TODAY.minusDays(1), phone.getId()));
        adapter.save(newPurchase("F001-4", TODAY, phone.getId()));

        assertThat(adapter.findAll())
                .extracting(Purchase::getInvoiceNumber)
                .containsExactly("F001-4", "F001-2", "F001-3", "F001-1");
    }

    @Test
    void tellsWhetherASupplierInvoiceNumberIsAlreadyRegistered() {
        adapter.save(newPurchase("F001-1", TODAY, phone.getId()));

        assertThat(adapter.existsBySupplierIdAndInvoiceNumber(supplier.getId(), "F001-1")).isTrue();
        assertThat(adapter.existsBySupplierIdAndInvoiceNumber(supplier.getId(), "F001-2")).isFalse();
    }

    @Test
    void mapsTheUniqueSupplierInvoiceConstraintToADuplicateInvoiceError() {
        adapter.save(newPurchase("F001-1", TODAY, phone.getId()));

        assertThatThrownBy(() -> adapter.save(newPurchase("F001-1", TODAY, phone.getId())))
                .isInstanceOf(DuplicatePurchaseInvoiceException.class);
    }

    @Test
    void doesNotReportOtherIntegrityViolationsAsADuplicateInvoice() {
        assertThatThrownBy(() -> adapter.save(newPurchase("F001-1", TODAY, 999_999L)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicatePurchaseInvoiceException.class);
    }
}
