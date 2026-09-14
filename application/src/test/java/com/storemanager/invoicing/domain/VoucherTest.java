package com.storemanager.invoicing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VoucherTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-15T10:05:00Z");

    private Voucher newPendingVoucher() {
        return Voucher.createNew(1L, VoucherType.BOLETA, "DNI", "12345678", NOW);
    }

    @Test
    void createNewStartsPendingWithNoAttemptsAndNoLinks() {
        Voucher voucher = newPendingVoucher();

        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(voucher.getAttemptCount()).isZero();
        assertThat(voucher.getRejectionReason()).isNull();
        assertThat(voucher.getHash()).isNull();
        assertThat(voucher.getType()).isEqualTo(VoucherType.BOLETA);
    }

    @Test
    void requiresASaleId() {
        assertThatThrownBy(() -> Voucher.createNew(null, VoucherType.BOLETA, "DNI", "12345678", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNullBuyerDocumentFieldsForABoletaSimple() {
        Voucher voucher = Voucher.createNew(1L, VoucherType.BOLETA, null, null, NOW);

        assertThat(voucher.getBuyerDocumentType()).isNull();
        assertThat(voucher.getBuyerDocumentNumber()).isNull();
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatus.PENDING);
    }

    @Test
    void reconstituteAcceptsNullBuyerDocumentFieldsForABoletaSimple() {
        Voucher voucher =
                Voucher.reconstitute(
                        1L, 1L, VoucherType.BOLETA, VoucherStatus.ACCEPTED, null, null, 0, null, "REF-1", "pdf",
                        "xml", "cdr", "hash", NOW, NOW);

        assertThat(voucher.getBuyerDocumentType()).isNull();
        assertThat(voucher.getBuyerDocumentNumber()).isNull();
    }

    @Test
    void pendingFromPendingIncrementsAttemptCountAndKeepsStatus() {
        Voucher voucher = newPendingVoucher();

        Voucher retried = voucher.pending("Timed out", LATER);

        assertThat(retried.getStatus()).isEqualTo(VoucherStatus.PENDING);
        assertThat(retried.getAttemptCount()).isEqualTo(1);
        assertThat(retried.getRejectionReason()).isEqualTo("Timed out");
    }

    @Test
    void pendingFromAcceptedIsNotAllowed() {
        Voucher accepted =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER);

        assertThatThrownBy(() -> accepted.pending("late failure", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pendingFromVoidedIsNotAllowed() {
        Voucher voided =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER).voided(LATER);

        assertThatThrownBy(() -> voided.pending("late failure", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void needsAttentionFromPendingIncrementsAttemptCount() {
        Voucher voucher = newPendingVoucher().pending("fail 1", LATER);

        Voucher escalated = voucher.needsAttention("exhausted retries", LATER);

        assertThat(escalated.getStatus()).isEqualTo(VoucherStatus.NEEDS_ATTENTION);
        assertThat(escalated.getAttemptCount()).isEqualTo(2);
        assertThat(escalated.getRejectionReason()).isEqualTo("exhausted retries");
    }

    @Test
    void needsAttentionFromAcceptedIsNotAllowed() {
        Voucher accepted =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER);

        assertThatThrownBy(() -> accepted.needsAttention("late failure", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptedFromPendingClearsRejectionReasonAndStoresLinks() {
        Voucher voucher = newPendingVoucher().pending("first failure", LATER);

        Voucher accepted = voucher.accepted("REF-1", "pdf-link", "xml-link", "cdr-link", "hash-1", LATER);

        assertThat(accepted.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
        assertThat(accepted.getRejectionReason()).isNull();
        assertThat(accepted.getExternalReference()).isEqualTo("REF-1");
        assertThat(accepted.getPdfLink()).isEqualTo("pdf-link");
        assertThat(accepted.getXmlLink()).isEqualTo("xml-link");
        assertThat(accepted.getCdrLink()).isEqualTo("cdr-link");
        assertThat(accepted.getHash()).isEqualTo("hash-1");
    }

    @Test
    void acceptedFromNeedsAttentionSucceedsOnManualRetry() {
        Voucher escalated = newPendingVoucher().needsAttention("exhausted", LATER);

        Voucher accepted = escalated.accepted("REF-2", "pdf", "xml", "cdr", "hash-2", LATER);

        assertThat(accepted.getStatus()).isEqualTo(VoucherStatus.ACCEPTED);
    }

    @Test
    void acceptedFromAcceptedIsNotAllowed() {
        Voucher accepted =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER);

        assertThatThrownBy(() -> accepted.accepted("REF-2", "pdf", "xml", "cdr", "hash", LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidedFromAcceptedSucceeds() {
        Voucher accepted =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER);

        Voucher voided = accepted.voided(LATER);

        assertThat(voided.getStatus()).isEqualTo(VoucherStatus.VOIDED);
    }

    @Test
    void voidedFromPendingIsNotAllowed() {
        Voucher pending = newPendingVoucher();

        assertThatThrownBy(() -> pending.voided(LATER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidedFromNeedsAttentionIsNotAllowed() {
        Voucher escalated = newPendingVoucher().needsAttention("exhausted", LATER);

        assertThatThrownBy(() -> escalated.voided(LATER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidedFromVoidedIsNotAllowed() {
        Voucher voided =
                newPendingVoucher().accepted("REF-1", "pdf", "xml", "cdr", "hash", LATER).voided(LATER);

        assertThatThrownBy(() -> voided.voided(LATER)).isInstanceOf(IllegalStateException.class);
    }
}
