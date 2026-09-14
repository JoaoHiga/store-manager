package com.storemanager.invoicing.domain;

/**
 * A {@link Voucher}'s lifecycle state. Allowed transitions (enforced by {@link Voucher}'s own
 * transition methods, not by this enum):
 *
 * <ul>
 *   <li>{@code PENDING} -&gt; {@code PENDING} (a failed retry attempt, under the retry cap)
 *   <li>{@code PENDING} -&gt; {@code ACCEPTED} (issuance or a retry succeeds)
 *   <li>{@code PENDING} -&gt; {@code NEEDS_ATTENTION} (retries exhausted)
 *   <li>{@code NEEDS_ATTENTION} -&gt; {@code ACCEPTED} (a manual retry succeeds)
 *   <li>{@code NEEDS_ATTENTION} -&gt; {@code NEEDS_ATTENTION} (a manual retry fails again)
 *   <li>{@code ACCEPTED} -&gt; {@code VOIDED} (a comunicación de baja is confirmed)
 * </ul>
 *
 * No other transition is allowed — in particular, only an {@code ACCEPTED} voucher can ever be
 * voided.
 */
public enum VoucherStatus {
    PENDING,
    ACCEPTED,
    NEEDS_ATTENTION,
    VOIDED
}
