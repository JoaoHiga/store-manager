package com.storemanager.sales.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.infrastructure.persistence.CategoryJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.CategoryJpaRepository;
import com.storemanager.catalog.infrastructure.persistence.ProductJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.ProductJpaRepository;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.domain.SaleStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SaleRepositoryAdapterTest {

    @Autowired private SaleJpaRepository saleJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;

    private SaleRepositoryAdapter adapter;
    private Long productId;

    @BeforeEach
    void setUp() {
        adapter = new SaleRepositoryAdapter(saleJpaRepository);
        Instant now = Instant.now();
        Long categoryId = categoryJpaRepository.save(new CategoryJpaEntity(null, "Celulares", null, now, now)).getId();
        productId =
                productJpaRepository
                        .save(
                                new ProductJpaEntity(
                                        null, "PRD-000001", "iPhone 12", "Usado", categoryId, "Apple",
                                        new BigDecimal("300"), new BigDecimal("350"), 3, true, now, now))
                        .getId();
    }

    private Sale newSale() {
        return Sale.createNew(
                List.of(SaleItem.createNew(productId, "Usado", new BigDecimal("350"), 2)), "DNI", "12345678",
                Instant.now());
    }

    @Test
    void savesANewSaleAssigningIdsToTheSaleAndItsItems() {
        Sale saved = adapter.save(newSale());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(saved.getTotal()).isEqualByComparingTo("700");
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getId()).isNotNull();
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualByComparingTo("350");
    }

    @Test
    void findByIdReturnsTheSaleWithItsLineItems() {
        Sale saved = adapter.save(newSale());

        Sale found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getBuyerDocumentType()).isEqualTo("DNI");
        assertThat(found.getBuyerDocumentNumber()).isEqualTo("12345678");
        assertThat(found.getItems()).extracting(SaleItem::getProductId).containsExactly(productId);
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(adapter.findById(999L)).isEmpty();
    }

    @Test
    void findAllReturnsEveryPersistedSale() {
        adapter.save(newSale());
        adapter.save(newSale());

        assertThat(adapter.findAll()).hasSize(2);
    }

    @Test
    void aLegacyRowWithAMalformedBuyerDocumentLoadsSuccessfullyViaReconstitute() {
        // Seeded directly through the JPA entity, bypassing Sale.createNew, to simulate a row
        // written before buyer-identification-hardening's RUC format validation existed.
        SaleItemJpaEntity itemEntity =
                new SaleItemJpaEntity(null, productId, "Usado", new BigDecimal("350"), 1, new BigDecimal("350"));
        SaleJpaEntity entity =
                new SaleJpaEntity(
                        null, "RUC", "00165490fddfgdsgdf", "COMPLETED", new BigDecimal("350"), Instant.now(),
                        List.of(itemEntity));
        Long savedId = saleJpaRepository.save(entity).getId();

        Sale found = adapter.findById(savedId).orElseThrow();

        assertThat(found.getBuyerDocumentType()).isEqualTo("RUC");
        assertThat(found.getBuyerDocumentNumber()).isEqualTo("00165490fddfgdsgdf");
    }

    @Test
    void aSaleWithNoBuyerDocumentRoundTripsAsNull() {
        Sale saleWithNoDocument =
                Sale.createNew(
                        List.of(SaleItem.createNew(productId, "Usado", new BigDecimal("50"), 1)), null, null,
                        Instant.now());

        Sale saved = adapter.save(saleWithNoDocument);
        Sale found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getBuyerDocumentType()).isNull();
        assertThat(found.getBuyerDocumentNumber()).isNull();
    }
}
