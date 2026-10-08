package com.storemanager.purchasing.domain;

import com.storemanager.shared.domain.RucValidator;
import java.time.Instant;

/**
 * A supplier the store buys merchandise from. This class enforces its own structural invariants
 * (a non-blank name and a valid RUC); cross-entity uniqueness of {@code ruc} is enforced by
 * {@link com.storemanager.purchasing.usecase.CreateSupplierUseCase} via the repository port.
 *
 * <p>The RUC format and modulo-11 check digit are validated by the shared {@link RucValidator}.
 */
public final class Supplier {

    private final Long id;
    private final String name;
    private final String ruc;
    private final String phone;
    private final String email;
    private final String address;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Supplier(
            Long id,
            String name,
            String ruc,
            String phone,
            String email,
            String address,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name must not be blank");
        }
        if (ruc == null || ruc.isBlank()) {
            throw new IllegalArgumentException("Supplier RUC is required");
        }
        RucValidator.validate(ruc);
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Supplier createNew(
            String name, String ruc, String phone, String email, String address, Instant now) {
        return new Supplier(null, name, ruc, phone, email, address, true, now, now);
    }

    public static Supplier reconstitute(
            Long id,
            String name,
            String ruc,
            String phone,
            String email,
            String address,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        return new Supplier(id, name, ruc, phone, email, address, active, createdAt, updatedAt);
    }

    public Supplier deactivated(Instant now) {
        return new Supplier(id, name, ruc, phone, email, address, false, createdAt, now);
    }

    public Supplier activated(Instant now) {
        return new Supplier(id, name, ruc, phone, email, address, true, createdAt, now);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRuc() {
        return ruc;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
