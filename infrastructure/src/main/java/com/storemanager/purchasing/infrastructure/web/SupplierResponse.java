package com.storemanager.purchasing.infrastructure.web;

import com.storemanager.purchasing.domain.Supplier;
import java.time.Instant;

public record SupplierResponse(
        Long id,
        String name,
        String ruc,
        String phone,
        String email,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static SupplierResponse from(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getName(),
                supplier.getRuc(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt());
    }
}
