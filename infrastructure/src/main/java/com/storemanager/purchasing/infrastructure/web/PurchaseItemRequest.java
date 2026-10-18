package com.storemanager.purchasing.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PurchaseItemRequest(
        @NotNull Long productId,
        @Positive int quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {}
