package com.storemanager.purchasing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.purchasing.domain.Supplier;
import org.junit.jupiter.api.Test;

class CreateSupplierUseCaseTest {

    private final InMemorySupplierRepository repository = new InMemorySupplierRepository();
    private final CreateSupplierUseCase useCase = new CreateSupplierUseCase(repository);

    @Test
    void createsAnActiveSupplier() {
        Supplier result = useCase.execute("Distribuidora Lima", "20131312955", "987654321", null, null);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getRuc()).isEqualTo("20131312955");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void rejectsADuplicateRuc() {
        useCase.execute("Distribuidora Lima", "20131312955", null, null, null);

        assertThatThrownBy(() -> useCase.execute("Otro nombre", "20131312955", null, null, null))
                .isInstanceOf(DuplicateSupplierRucException.class)
                .hasMessageContaining("20131312955");
    }

    @Test
    void rejectsAnInvalidRucWithoutSaving() {
        assertThatThrownBy(() -> useCase.execute("Distribuidora Lima", "20131312956", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
