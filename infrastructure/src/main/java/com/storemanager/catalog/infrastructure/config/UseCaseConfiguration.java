package com.storemanager.catalog.infrastructure.config;

import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.usecase.CreateCategoryUseCase;
import com.storemanager.catalog.usecase.ListCategoriesUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires application-module use cases as Spring beans. This class has to live
 * here, not in {@code application}: use cases carry no Spring annotations
 * themselves (that module has zero Spring dependency), so infrastructure is
 * responsible for constructing and registering them explicitly.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    public CreateCategoryUseCase createCategoryUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        return new CreateCategoryUseCase(categoryRepositoryPort);
    }

    @Bean
    public ListCategoriesUseCase listCategoriesUseCase(CategoryRepositoryPort categoryRepositoryPort) {
        return new ListCategoriesUseCase(categoryRepositoryPort);
    }
}
