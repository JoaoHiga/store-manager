package com.storemanager.catalog.usecase;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link CategoryRepositoryPort} test double. Hexagonal ports are
 * small enough that a hand-written fake is clearer than a mocking framework
 * for these use-case tests, and it keeps the application module dependency
 * list minimal (JUnit5 + AssertJ only).
 */
class InMemoryCategoryRepository implements CategoryRepositoryPort {

    private final List<Category> categories = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Category save(Category category) {
        Category persisted =
                Category.reconstitute(
                        nextId++,
                        category.getName(),
                        category.getDescription(),
                        category.getCreatedAt(),
                        category.getUpdatedAt());
        categories.add(persisted);
        return persisted;
    }

    @Override
    public Optional<Category> findByName(String name) {
        return categories.stream().filter(c -> c.getName().equals(name)).findFirst();
    }

    @Override
    public Optional<Category> findById(Long id) {
        return categories.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    @Override
    public List<Category> findAll() {
        return List.copyOf(categories);
    }
}
