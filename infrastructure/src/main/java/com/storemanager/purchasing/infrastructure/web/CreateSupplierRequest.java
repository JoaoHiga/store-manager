package com.storemanager.purchasing.infrastructure.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The RUC check digit is validated by the domain ({@code Supplier}); here only its shape. */
public record CreateSupplierRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "RUC must be exactly 11 numeric digits") String ruc,
        @Size(max = 30) String phone,
        @Email @Size(max = 120) String email,
        @Size(max = 300) String address) {}
