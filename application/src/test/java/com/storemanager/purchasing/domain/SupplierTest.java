package com.storemanager.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SupplierTest {

    /** Real SUNAT RUC, also used by {@code BuyerDocumentValidatorTest}. */
    private static final String VALID_RUC = "20131312955";

    @Test
    void createsAnActiveSupplierWithOptionalContactData() {
        Instant now = Instant.now();
        Supplier supplier =
                Supplier.createNew("Distribuidora Lima", VALID_RUC, "987654321", "ventas@dl.pe", "Av. Lima 123", now);

        assertThat(supplier.getId()).isNull();
        assertThat(supplier.getName()).isEqualTo("Distribuidora Lima");
        assertThat(supplier.getRuc()).isEqualTo(VALID_RUC);
        assertThat(supplier.getPhone()).isEqualTo("987654321");
        assertThat(supplier.getEmail()).isEqualTo("ventas@dl.pe");
        assertThat(supplier.getAddress()).isEqualTo("Av. Lima 123");
        assertThat(supplier.isActive()).isTrue();
        assertThat(supplier.getCreatedAt()).isEqualTo(now);
        assertThat(supplier.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void allowsNullContactData() {
        Supplier supplier = Supplier.createNew("Distribuidora Lima", VALID_RUC, null, null, null, Instant.now());

        assertThat(supplier.getPhone()).isNull();
        assertThat(supplier.getEmail()).isNull();
        assertThat(supplier.getAddress()).isNull();
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> Supplier.createNew("  ", VALID_RUC, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingRuc() {
        assertThatThrownBy(() -> Supplier.createNew("Distribuidora Lima", null, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucWithAnInvalidCheckDigit() {
        assertThatThrownBy(
                        () -> Supplier.createNew("Distribuidora Lima", "20131312956", null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RUC");
    }

    @Test
    void rejectsARucThatIsNotElevenDigits() {
        assertThatThrownBy(
                        () -> Supplier.createNew("Distribuidora Lima", "2013131295", null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivatesAndReactivatesKeepingCreatedAt() {
        Instant createdAt = Instant.parse("2026-10-01T10:00:00Z");
        Instant later = Instant.parse("2026-10-08T10:00:00Z");
        Supplier supplier = Supplier.createNew("Distribuidora Lima", VALID_RUC, null, null, null, createdAt);

        Supplier inactive = supplier.deactivated(later);
        Supplier reactivated = inactive.activated(later);

        assertThat(inactive.isActive()).isFalse();
        assertThat(inactive.getCreatedAt()).isEqualTo(createdAt);
        assertThat(inactive.getUpdatedAt()).isEqualTo(later);
        assertThat(reactivated.isActive()).isTrue();
    }
}
