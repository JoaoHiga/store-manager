package com.storemanager.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Category;
import org.junit.jupiter.api.Test;

class CreateCategoryUseCaseTest {

    private final InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
    private final CreateCategoryUseCase useCase = new CreateCategoryUseCase(repository);

    @Test
    void createsACategoryWithAUniqueName() {
        Category result = useCase.execute("Celulares", "Telefonos moviles");

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Celulares");
    }

    @Test
    void rejectsADuplicateName() {
        useCase.execute("Celulares", null);

        assertThatThrownBy(() -> useCase.execute("Celulares", null))
                .isInstanceOf(DuplicateCategoryNameException.class);
    }
}
