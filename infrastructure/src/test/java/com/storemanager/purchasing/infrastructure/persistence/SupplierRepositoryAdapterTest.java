package com.storemanager.purchasing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.purchasing.domain.Supplier;
import com.storemanager.purchasing.usecase.DuplicateSupplierRucException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** {@code replace = NONE}: see {@code CategoryRepositoryAdapterTest} for why the test profile's H2 is required. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SupplierRepositoryAdapterTest {

    private static final String RUC_A = "20131312955";
    private static final String RUC_B = "20100000009";

    @Autowired private SupplierJpaRepository jpaRepository;

    private SupplierRepositoryAdapter adapter;

    private SupplierRepositoryAdapter adapter() {
        if (adapter == null) {
            adapter = new SupplierRepositoryAdapter(jpaRepository);
        }
        return adapter;
    }

    @Test
    void savesAndFindsASupplierByRucAndId() {
        Supplier saved =
                adapter().save(
                                Supplier.createNew(
                                        "Distribuidora Lima", RUC_A, "987654321", "ventas@dl.pe", "Av. Lima 123",
                                        Instant.now()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(adapter().findByRuc(RUC_A)).isPresent();
        Supplier found = adapter().findById(saved.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo("ventas@dl.pe");
        assertThat(found.getAddress()).isEqualTo("Av. Lima 123");
    }

    @Test
    void listsSuppliersOrderedByNameAndFiltersByActiveState() {
        adapter().save(Supplier.createNew("Zeta Importaciones", RUC_A, null, null, null, Instant.now()));
        Supplier alfa = adapter().save(Supplier.createNew("Alfa Accesorios", RUC_B, null, null, null, Instant.now()));
        adapter().save(alfa.deactivated(Instant.now()));

        assertThat(adapter().findAll())
                .extracting(Supplier::getName)
                .containsExactly("Alfa Accesorios", "Zeta Importaciones");
        assertThat(adapter().findByActive(true)).extracting(Supplier::getName).containsExactly("Zeta Importaciones");
        assertThat(adapter().findByActive(false)).extracting(Supplier::getName).containsExactly("Alfa Accesorios");
    }

    @Test
    void savingTheSameRucTwiceThrowsDuplicateSupplierRuc() {
        // Deterministic proxy for the concurrent case: the unique constraint on suppliers.ruc is the real guard.
        adapter().save(Supplier.createNew("Distribuidora Lima", RUC_A, null, null, null, Instant.now()));

        assertThatThrownBy(
                        () -> adapter().save(Supplier.createNew("Otro nombre", RUC_A, null, null, null, Instant.now())))
                .isInstanceOf(DuplicateSupplierRucException.class);
    }

    @Test
    void doesNotReportOtherIntegrityViolationsAsADuplicateRuc() {
        // The name column is VARCHAR(160); only uq_suppliers_ruc means a duplicate RUC.
        String tooLongName = "a".repeat(161);

        assertThatThrownBy(
                        () -> adapter().save(Supplier.createNew(tooLongName, RUC_A, null, null, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateSupplierRucException.class);
    }
}
