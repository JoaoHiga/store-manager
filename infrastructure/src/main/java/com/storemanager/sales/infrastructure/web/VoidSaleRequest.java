package com.storemanager.sales.infrastructure.web;

import jakarta.validation.constraints.NotBlank;

public record VoidSaleRequest(@NotBlank String reason) {}
