package com.storemanager.catalog.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record CreateCategoryRequest(@NotBlank String name, String description) {}
