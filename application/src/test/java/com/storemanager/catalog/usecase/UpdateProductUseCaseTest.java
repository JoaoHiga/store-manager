package com.storemanager.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdateProductUseCaseTest {

    private final InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final CreateProductUseCase createProductUseCase =
            new CreateProductUseCase(productRepository, categoryRepository);
    private final UpdateProductUseCase useCase = new UpdateProductUseCase(productRepository);

    private Category category;

    @BeforeEach
    void seedCategory() {
        category = new CreateCategoryUseCase(categoryRepository).execute("Celulares", null);
    }

    @Test
    void updatesSalePriceAndStockKeepingIdAndSku() {
        Product created =
                createProductUseCase.execute("iPhone 12", null, category.getId(), null, null, new BigDecimal("350"), 3);

        Product updated =
                useCase.execute(
                        created.getId(), "iPhone 12", null, category.getId(), null, null, new BigDecimal("320"), 5);

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getSku()).isEqualTo(created.getSku());
        assertThat(updated.getSalePrice()).isEqualByComparingTo("320");
        assertThat(updated.getStockQuantity()).isEqualTo(5);
    }

    @Test
    void rejectsANegativeStockQuantityOnUpdate() {
        Product created =
                createProductUseCase.execute("iPhone 12", null, category.getId(), null, null, new BigDecimal("350"), 3);

        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        created.getId(), "iPhone 12", null, category.getId(), null, null,
                                        new BigDecimal("320"), -2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUpdatingANonexistentProduct() {
        assertThatThrownBy(
                        () ->
                                useCase.execute(
                                        999L, "iPhone 12", null, category.getId(), null, null, new BigDecimal("320"), 5))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
