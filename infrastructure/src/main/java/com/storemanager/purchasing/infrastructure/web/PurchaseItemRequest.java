package com.storemanager.purchasing.infrastructure.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

public record PurchaseItemRequest(
        @NotNull Long productId,
        @NotNull @Positive @JsonDeserialize(using = WholeNumberDeserializer.class) Integer quantity,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {}
