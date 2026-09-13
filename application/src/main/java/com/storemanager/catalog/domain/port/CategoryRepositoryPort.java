package com.storemanager.catalog.domain.port;

import com.storemanager.catalog.domain.Category;
import java.util.List;
import java.util.Optional;

/**
 * Output port for Category persistence. Implemented by
 * {@code CategoryRepositoryAdapter} in the infrastructure module.
 */
public interface CategoryRepositoryPort {

    Category save(Category category);

    Optional<Category> findByName(String name);

    Optional<Category> findById(Long id);

    List<Category> findAll();
}
