package com.storemanager.purchasing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.purchasing.domain.Supplier;
import org.junit.jupiter.api.Test;

class DeactivateSupplierUseCaseTest {

    private final InMemorySupplierRepository repository = new InMemorySupplierRepository();
    private final DeactivateSupplierUseCase deactivate = new DeactivateSupplierUseCase(repository);
    private final ReactivateSupplierUseCase reactivate = new ReactivateSupplierUseCase(repository);

    @Test
    void deactivatesAndReactivatesASupplier() {
        Supplier created =
                new CreateSupplierUseCase(repository).execute("Distribuidora Lima", "20131312955", null, null, null);

        deactivate.execute(created.getId());
        assertThat(repository.findById(created.getId()).orElseThrow().isActive()).isFalse();

        reactivate.execute(created.getId());
        assertThat(repository.findById(created.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void rejectsAnUnknownSupplier() {
        assertThatThrownBy(() -> deactivate.execute(99L)).isInstanceOf(SupplierNotFoundException.class);
        assertThatThrownBy(() -> reactivate.execute(99L)).isInstanceOf(SupplierNotFoundException.class);
    }
}
