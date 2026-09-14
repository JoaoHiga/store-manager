package com.storemanager.invoicing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vouchers")
public class VoucherJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "buyer_document_type", nullable = true, length = 10)
    private String buyerDocumentType;

    @Column(name = "buyer_document_number", nullable = true, length = 20)
    private String buyerDocumentNumber;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "pdf_link", length = 500)
    private String pdfLink;

    @Column(name = "xml_link", length = 500)
    private String xmlLink;

    @Column(name = "cdr_link", length = 500)
    private String cdrLink;

    @Column(length = 255)
    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoucherJpaEntity() {
        // required by JPA
    }

    public VoucherJpaEntity(
            Long id,
            Long saleId,
            String type,
            String status,
            String buyerDocumentType,
            String buyerDocumentNumber,
            int attemptCount,
            String rejectionReason,
            String externalReference,
            String pdfLink,
            String xmlLink,
            String cdrLink,
            String hash,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.saleId = saleId;
        this.type = type;
        this.status = status;
        this.buyerDocumentType = buyerDocumentType;
        this.buyerDocumentNumber = buyerDocumentNumber;
        this.attemptCount = attemptCount;
        this.rejectionReason = rejectionReason;
        this.externalReference = externalReference;
        this.pdfLink = pdfLink;
        this.xmlLink = xmlLink;
        this.cdrLink = cdrLink;
        this.hash = hash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getSaleId() {
        return saleId;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getBuyerDocumentType() {
        return buyerDocumentType;
    }

    public String getBuyerDocumentNumber() {
        return buyerDocumentNumber;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getPdfLink() {
        return pdfLink;
    }

    public String getXmlLink() {
        return xmlLink;
    }

    public String getCdrLink() {
        return cdrLink;
    }

    public String getHash() {
        return hash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
