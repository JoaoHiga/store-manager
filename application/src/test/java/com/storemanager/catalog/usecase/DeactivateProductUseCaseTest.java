package com.storemanager.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeactivateProductUseCaseTest {

    private final InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final CreateProductUseCase createProductUseCase =
            new CreateProductUseCase(productRepository, categoryRepository);
    private final DeactivateProductUseCase useCase = new DeactivateProductUseCase(productRepository);

    private Category category;

    @BeforeEach
    void seedCategory() {
        category = new CreateCategoryUseCase(categoryRepository).execute("Celulares", null);
    }

    @Test
    void deactivatesAnActiveProduct() {
        Product created =
                createProductUseCase.execute("iPhone 12", null, category.getId(), null, null, new BigDecimal("350"), 3);

        useCase.execute(created.getId());

        assertThat(productRepository.findById(created.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void deactivatingAnAlreadyInactiveProductIsIdempotent() {
        Product created =
                createProductUseCase.execute("iPhone 12", null, category.getId(), null, null, new BigDecimal("350"), 3);
        useCase.execute(created.getId());

        useCase.execute(created.getId()); // second call must not throw

        assertThat(productRepository.findById(created.getId()).orElseThrow().isActive()).isFalse();
    }
}
