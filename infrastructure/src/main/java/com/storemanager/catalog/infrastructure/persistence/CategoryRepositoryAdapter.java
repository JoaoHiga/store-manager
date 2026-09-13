package com.storemanager.catalog.infrastructure.persistence;

import com.storemanager.catalog.domain.Category;
import com.storemanager.catalog.domain.port.CategoryRepositoryPort;
import com.storemanager.catalog.usecase.DuplicateCategoryNameException;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final CategoryJpaRepository jpaRepository;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Category save(Category category) {
        CategoryJpaEntity entity =
                new CategoryJpaEntity(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        category.getCreatedAt(),
                        category.getUpdatedAt());
        try {
            return toDomain(jpaRepository.save(entity));
        } catch (DataIntegrityViolationException ex) {
            // The use case's findByName check-then-act isn't race-safe: two
            // concurrent inserts for the same name can both pass it. The
            // unique constraint on categories.name is the real guard; a
            // violation here means someone else committed first.
            throw new DuplicateCategoryNameException(category.getName());
        }
    }

    @Override
    public Optional<Category> findByName(String name) {
        return jpaRepository.findByName(name).map(this::toDomain);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private Category toDomain(CategoryJpaEntity entity) {
        return Category.reconstitute(
                entity.getId(), entity.getName(), entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
