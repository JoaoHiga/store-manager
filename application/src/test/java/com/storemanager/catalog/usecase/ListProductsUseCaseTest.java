package com.storemanager.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.Product;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListProductsUseCaseTest {

    private final InMemoryCategoryRepository categoryRepository = new InMemoryCategoryRepository();
    private final InMemoryProductRepository productRepository = new InMemoryProductRepository();
    private final CreateProductUseCase createProductUseCase =
            new CreateProductUseCase(productRepository, categoryRepository);
    private final DeactivateProductUseCase deactivateProductUseCase = new DeactivateProductUseCase(productRepository);
    private final ListProductsUseCase useCase = new ListProductsUseCase(productRepository);

    private Category phones;
    private Category accessories;

    @BeforeEach
    void seedCategories() {
        CreateCategoryUseCase createCategoryUseCase = new CreateCategoryUseCase(categoryRepository);
        phones = createCategoryUseCase.execute("Celulares", null);
        accessories = createCategoryUseCase.execute("Accesorios", null);
    }

    @Test
    void listsOnlyActiveProductsByDefault() {
        Product active =
                createProductUseCase.execute("iPhone 12", null, phones.getId(), null, null, new BigDecimal("350"), 1);
        Product toDeactivate =
                createProductUseCase.execute("Funda", null, accessories.getId(), null, null, new BigDecimal("10"), 5);
        deactivateProductUseCase.execute(toDeactivate.getId());

        var result = useCase.execute(null);

        assertThat(result).extracting(Product::getId).containsExactly(active.getId());
    }

    @Test
    void filtersByCategoryWhenProvided() {
        createProductUseCase.execute("iPhone 12", null, phones.getId(), null, null, new BigDecimal("350"), 1);
        createProductUseCase.execute("Funda", null, accessories.getId(), null, null, new BigDecimal("10"), 5);

        var result = useCase.execute(accessories.getId());

        assertThat(result).extracting(Product::getName).containsExactly("Funda");
    }
}
