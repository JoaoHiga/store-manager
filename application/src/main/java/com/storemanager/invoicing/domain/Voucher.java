package com.storemanager.invoicing.domain;

import java.time.Instant;

/**
 * A SUNAT electronic document (boleta or factura) issued for a {@link
 * com.storemanager.sales.domain.Sale}. Immutable, like {@code Sale}/{@code Product}: every
 * transition method returns a new {@code Voucher} rather than mutating this one, and throws {@link
 * IllegalStateException} when the transition isn't allowed from the current {@link
 * VoucherStatus}. This class only enforces the voucher's own status-transition invariants — the
 * actual call to the OSE (NubeFacT) is the {@code InvoicingProvider} port's job, orchestrated by
 * the {@code invoicing.usecase} classes.
 */
public final class Voucher {

    private final Long id;
    private final Long saleId;
    private final VoucherType type;
    private final VoucherStatus status;
    private final String buyerDocumentType;
    private final String buyerDocumentNumber;
    private final int attemptCount;
    private final String rejectionReason;
    private final String externalReference;
    private final String pdfLink;
    private final String xmlLink;
    private final String cdrLink;
    private final String hash;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Voucher(
            Long id,
            Long saleId,
            VoucherType type,
            VoucherStatus status,
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
        if (saleId == null) {
            throw new IllegalArgumentException("Voucher saleId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Voucher type is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Voucher status is required");
        }
        if (attemptCount < 0) {
            throw new IllegalArgumentException("Voucher attemptCount must not be negative");
        }
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

    /** A brand-new voucher for a just-completed sale, before any issuance attempt. */
    public static Voucher createNew(
            Long saleId, VoucherType type, String buyerDocumentType, String buyerDocumentNumber, Instant now) {
        return new Voucher(
                null, saleId, type, VoucherStatus.PENDING, buyerDocumentType, buyerDocumentNumber, 0, null, null,
                null, null, null, null, now, now);
    }

    /** Rebuilds a voucher from persisted state — used by the infrastructure adapter only. */
    public static Voucher reconstitute(
            Long id,
            Long saleId,
            VoucherType type,
            VoucherStatus status,
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
        return new Voucher(
                id, saleId, type, status, buyerDocumentType, buyerDocumentNumber, attemptCount, rejectionReason,
                externalReference, pdfLink, xmlLink, cdrLink, hash, createdAt, updatedAt);
    }

    /**
     * The OSE accepted the voucher (on first issuance, or on a retry). Allowed from {@code PENDING}
     * or {@code NEEDS_ATTENTION} only.
     */
    public Voucher accepted(
            String externalReference, String pdfLink, String xmlLink, String cdrLink, String hash, Instant now) {
        requireStatusIn("accept", VoucherStatus.PENDING, VoucherStatus.NEEDS_ATTENTION);
        return new Voucher(
                id, saleId, type, VoucherStatus.ACCEPTED, buyerDocumentType, buyerDocumentNumber, attemptCount, null,
                externalReference, pdfLink, xmlLink, cdrLink, hash, createdAt, now);
    }

    /**
     * A retry attempt failed but the retry cap hasn't been reached yet: stays {@code PENDING},
     * with the attempt count incremented and the rejection reason updated. Allowed from {@code
     * PENDING} only.
     */
    public Voucher pending(String rejectionReason, Instant now) {
        requireStatusIn("record a retry attempt on", VoucherStatus.PENDING);
        return new Voucher(
                id, saleId, type, VoucherStatus.PENDING, buyerDocumentType, buyerDocumentNumber, attemptCount + 1,
                rejectionReason, externalReference, pdfLink, xmlLink, cdrLink, hash, createdAt, now);
    }

    /**
     * Automatic retries are exhausted (from {@code PENDING}, incrementing the attempt count one
     * last time), or a manual retry from {@code NEEDS_ATTENTION} failed again (attempt count stays
     * put — automatic retrying already stopped). Allowed from {@code PENDING} or {@code
     * NEEDS_ATTENTION} only.
     */
    public Voucher needsAttention(String rejectionReason, Instant now) {
        requireStatusIn("flag as NEEDS_ATTENTION", VoucherStatus.PENDING, VoucherStatus.NEEDS_ATTENTION);
        int nextAttemptCount = status == VoucherStatus.PENDING ? attemptCount + 1 : attemptCount;
        return new Voucher(
                id, saleId, type, VoucherStatus.NEEDS_ATTENTION, buyerDocumentType, buyerDocumentNumber,
                nextAttemptCount, rejectionReason, externalReference, pdfLink, xmlLink, cdrLink, hash, createdAt,
                now);
    }

    /** The comunicación de baja was confirmed by the OSE. Allowed from {@code ACCEPTED} only. */
    public Voucher voided(Instant now) {
        requireStatusIn("void", VoucherStatus.ACCEPTED);
        return new Voucher(
                id, saleId, type, VoucherStatus.VOIDED, buyerDocumentType, buyerDocumentNumber, attemptCount,
                rejectionReason, externalReference, pdfLink, xmlLink, cdrLink, hash, createdAt, now);
    }

    private void requireStatusIn(String action, VoucherStatus... allowed) {
        for (VoucherStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("Cannot " + action + " a voucher in status " + status);
    }

    public Long getId() {
        return id;
    }

    public Long getSaleId() {
        return saleId;
    }

    public VoucherType getType() {
        return type;
    }

    public VoucherStatus getStatus() {
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
