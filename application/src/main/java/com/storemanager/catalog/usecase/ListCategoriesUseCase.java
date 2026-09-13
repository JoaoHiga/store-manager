package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import java.util.List;

public class ListCategoriesUseCase {

    private final CategoryRepositoryPort categoryRepository;

    public ListCategoriesUseCase(CategoryRepositoryPort categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> execute() {
        return categoryRepository.findAll();
    }
}
