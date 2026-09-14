package com.storemanager.sales.infrastructure.persistence;

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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
public class SaleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_document_type", nullable = true, length = 10)
    private String buyerDocumentType;

    @Column(name = "buyer_document_number", nullable = true, length = 20)
    private String buyerDocumentNumber;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<SaleItemJpaEntity> items = new ArrayList<>();

    protected SaleJpaEntity() {
        // required by JPA
    }

    public SaleJpaEntity(
            Long id,
            String buyerDocumentType,
            String buyerDocumentNumber,
            String status,
            BigDecimal total,
            Instant createdAt,
            List<SaleItemJpaEntity> items) {
        this.id = id;
        this.buyerDocumentType = buyerDocumentType;
        this.buyerDocumentNumber = buyerDocumentNumber;
        this.status = status;
        this.total = total;
        this.createdAt = createdAt;
        replaceItems(items);
    }

    private void replaceItems(List<SaleItemJpaEntity> newItems) {
        this.items.clear();
        if (newItems != null) {
            newItems.forEach(item -> item.setSale(this));
            this.items.addAll(newItems);
        }
    }

    public Long getId() {
        return id;
    }

    public String getBuyerDocumentType() {
        return buyerDocumentType;
    }

    public String getBuyerDocumentNumber() {
        return buyerDocumentNumber;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SaleItemJpaEntity> getItems() {
        return items;
    }
}
