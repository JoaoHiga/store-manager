package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import java.time.Instant;

public class CreateCategoryUseCase {

    private final CategoryRepositoryPort categoryRepository;

    public CreateCategoryUseCase(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category execute(String name, String description) {
        categoryRepository
                .findByName(name)
                .ifPresent(
                        existing -> {
                            throw new DuplicateCategoryNameException(name);
                        });

        Category category = Category.createNew(name, description, Instant.now());
        return categoryRepository.save(category);
    }
}
