package com.storemanager.purchasing.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchases")
public class PurchaseJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_name", nullable = false, length = 160)
    private String supplierName;

    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<PurchaseItemJpaEntity> items = new ArrayList<>();

    protected PurchaseJpaEntity() {
        // required by JPA
    }

    public PurchaseJpaEntity(
            Long id,
            Long supplierId,
            String supplierName,
            String invoiceNumber,
            LocalDate purchaseDate,
            BigDecimal total,
            Instant createdAt,
            List<PurchaseItemJpaEntity> items) {
        this.id = id;
        this.supplierId = supplierId;
        this.supplierName = supplierName;
        this.invoiceNumber = invoiceNumber;
        this.purchaseDate = purchaseDate;
        this.total = total;
        this.createdAt = createdAt;
        if (items != null) {
            items.forEach(item -> item.setPurchase(this));
            this.items.addAll(items);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<PurchaseItemJpaEntity> getItems() {
        return items;
    }
}
