package com.storemanager.catalog.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull Long categoryId,
        String brand,
        BigDecimal costPrice,
        @NotNull @Positive BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer stockQuantity) {}
