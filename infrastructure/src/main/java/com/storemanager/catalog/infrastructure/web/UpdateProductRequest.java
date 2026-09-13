package com.storemanager.catalog.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Deliberately has no {@code sku} field — sku can never be changed through this capability. */
public record UpdateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull Long categoryId,
        String brand,
        BigDecimal costPrice,
        @NotNull @Positive BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer stockQuantity) {}
