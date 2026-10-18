package com.storemanager.purchasing.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.purchasing.domain.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListSuppliersUseCaseTest {

    private final InMemorySupplierRepository repository = new InMemorySupplierRepository();
    private final ListSuppliersUseCase useCase = new ListSuppliersUseCase(repository);

    @BeforeEach
    void seedSuppliers() {
        CreateSupplierUseCase create = new CreateSupplierUseCase(repository);
        create.execute("Zeta Importaciones", "20131312955", null, null, null);
        Supplier inactive = create.execute("Alfa Accesorios", "20100000009", null, null, null);
        new DeactivateSupplierUseCase(repository).execute(inactive.getId());
    }

    @Test
    void listsEverySupplierOrderedByNameWhenNoFilterIsGiven() {
        assertThat(useCase.execute(null))
                .extracting(Supplier::getName)
                .containsExactly("Alfa Accesorios", "Zeta Importaciones");
    }

    @Test
    void filtersByActiveState() {
        assertThat(useCase.execute(true)).extracting(Supplier::getName).containsExactly("Zeta Importaciones");
        assertThat(useCase.execute(false)).extracting(Supplier::getName).containsExactly("Alfa Accesorios");
    }
}
