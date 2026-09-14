package com.storemanager.invoicing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.infrastructure.persistence.CategoryJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.CategoryJpaRepository;
import com.storemanager.catalog.infrastructure.persistence.ProductJpaEntity;
import com.storemanager.catalog.infrastructure.persistence.ProductJpaRepository;
import com.storemanager.invoicing.domain.Voucher;
import com.storemanager.invoicing.domain.VoucherStatus;
import com.storemanager.invoicing.domain.VoucherType;
import com.storemanager.sales.domain.Sale;
import com.storemanager.sales.domain.SaleItem;
import com.storemanager.sales.infrastructure.persistence.SaleJpaRepository;
import com.storemanager.sales.infrastructure.persistence.SaleRepositoryAdapter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VoucherRepositoryAdapterTest {

    @Autowired private VoucherJpaRepository voucherJpaRepository;
    @Autowired private SaleJpaRepository saleJpaRepository;
    @Autowired private ProductJpaRepository productJpaRepository;
    @Autowired private CategoryJpaRepository categoryJpaRepository;

    private VoucherRepositoryAdapter adapter;
    private SaleRepositoryAdapter saleRepositoryAdapter;
    private Long productId;
    private Long saleId;

    @BeforeEach
    void setUp() {
        adapter = new VoucherRepositoryAdapter(voucherJpaRepository);
        saleRepositoryAdapter = new SaleRepositoryAdapter(saleJpaRepository);
        Instant now = Instant.now();
        Long categoryId = categoryJpaRepository.save(new CategoryJpaEntity(null, "Celulares", null, now, now)).getId();
        productId =
                productJpaRepository
                        .save(
                                new ProductJpaEntity(
                                        null, "PRD-000001", "iPhone 12", "Usado", categoryId, "Apple",
                                        new BigDecimal("300"), new BigDecimal("350"), 3, true, now, now))
                        .getId();
        saleId = newSaleId();
    }

    private Long newSaleId() {
        Sale sale =
                saleRepositoryAdapter.save(
                        Sale.createNew(
                                List.of(SaleItem.createNew(productId, "Usado", new BigDecimal("350"), 2)), "DNI",
                                "12345678", Instant.now()));
        return sale.getId();
    }

    private Voucher newPendingVoucher() {
        return Voucher.createNew(saleId, VoucherType.BOLETA, "DNI", "12345678", Instant.now());
    }

    @Test
    void savesANewPendingVoucherAssigningItAnId() {
        Voucher saved = adapter.save(newPendingVoucher());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(saved.getSaleId()).isEqualTo(saleId);
    }

    @Test
    void findByIdReturnsTheVoucherWithAcceptedFieldsPopulated() {
        Voucher accepted =
                adapter.save(newPendingVoucher())
                        .accepted("REF-1", "pdf-link", "xml-link", "cdr-link", "hash-1", Instant.now());
        Voucher saved = adapter.save(accepted);

        Voucher found = adapter.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(found.getExternalReference()).isEqualTo("REF-1");
        assertThat(found.getPdfLink()).isEqualTo("pdf-link");
        assertThat(found.getXmlLink()).isEqualTo("xml-link");
        assertThat(found.getCdrLink()).isEqualTo("cdr-link");
        assertThat(found.getHash()).isEqualTo("hash-1");
    }

    @Test
    void findByIdReturnsEmptyForAnUnknownId() {
        assertThat(adapter.findById(999L)).isEmpty();
    }

    @Test
    void findBySaleIdReturnsTheVoucherForThatSale() {
        Voucher saved = adapter.save(newPendingVoucher());

        Voucher found = adapter.findBySaleId(saleId).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void findAllPendingReturnsOnlyPendingVouchers() {
        Voucher pending = adapter.save(newPendingVoucher());
        Voucher acceptedOnAnotherSale =
                adapter.save(
                        Voucher.createNew(newSaleId(), VoucherType.BOLETA, "DNI", "87654321", Instant.now())
                                .accepted("REF-2", "pdf", "xml", "cdr", "hash", Instant.now()));

        List<Voucher> pendingVouchers = adapter.findAllPending();

        assertThat(pendingVouchers).extracting(Voucher::getId).contains(pending.getId());
        assertThat(pendingVouchers).extracting(Voucher::getId).doesNotContain(acceptedOnAnotherSale.getId());
    }
}
