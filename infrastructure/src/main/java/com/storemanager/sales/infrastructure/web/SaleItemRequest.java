package com.storemanager.sales.infrastructure.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaleItemRequest(@NotNull Long productId, @Positive int quantity) {}
