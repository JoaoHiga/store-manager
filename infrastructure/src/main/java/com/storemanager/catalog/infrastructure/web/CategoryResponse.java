package com.storemanager.catalog.infrastructure.web;

import com.storemanager.catalog.domain.Category;
import java.time.Instant;

public record CategoryResponse(Long id, String name, String description, Instant createdAt, Instant updatedAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
