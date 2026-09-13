package com.storemanager.catalog.domain;

import java.time.Instant;

/**
 * A product category (e.g. "Celulares", "Accesorios"). This class only
 * enforces its own structural invariant (a non-blank name); cross-entity
 * uniqueness of {@code name} is enforced by
 * {@link com.storemanager.catalog.usecase.CreateCategoryUseCase} via the
 * repository port, since only the repository knows about other categories.
 */
public final class Category {

    private final Long id;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Category(Long id, String name, String description, Instant createdAt, Instant updatedAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Category createNew(String name, String description, Instant now) {
        return new Category(null, name, description, now, now);
    }

    public static Category reconstitute(
            Long id, String name, String description, Instant createdAt, Instant updatedAt) {
        return new Category(id, name, description, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
