package com.storemanager.catalog.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Deliberately has no {@code sku} field — sku can never be changed through this capability. */
public record UpdateProductRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotNull Long categoryId,
        @Size(max = 80) String brand,
        @PositiveOrZero BigDecimal costPrice,
        @NotNull @Positive BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer stockQuantity) {}
