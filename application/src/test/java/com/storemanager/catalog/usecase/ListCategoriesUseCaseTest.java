package com.storemanager.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Category;
import org.junit.jupiter.api.Test;

class ListCategoriesUseCaseTest {

    private final InMemoryCategoryRepository repository = new InMemoryCategoryRepository();
    private final CreateCategoryUseCase createCategoryUseCase = new CreateCategoryUseCase(repository);
    private final ListCategoriesUseCase useCase = new ListCategoriesUseCase(repository);

    @Test
    void listsAllCreatedCategories() {
        createCategoryUseCase.execute("Celulares", null);
        createCategoryUseCase.execute("Accesorios", null);

        var result = useCase.execute();

        assertThat(result).extracting(Category::getName).containsExactlyInAnyOrder("Celulares", "Accesorios");
    }

    @Test
    void returnsAnEmptyListWhenNoCategoriesExist() {
        assertThat(useCase.execute()).isEmpty();
    }
}
