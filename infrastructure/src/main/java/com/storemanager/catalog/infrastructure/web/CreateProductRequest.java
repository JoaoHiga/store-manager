package com.storemanager.catalog.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 1000) String description,
        @NotNull Long categoryId,
        @Size(max = 80) String brand,
        @PositiveOrZero BigDecimal costPrice,
        @NotNull @Positive BigDecimal salePrice,
        @NotNull @PositiveOrZero Integer stockQuantity) {}
