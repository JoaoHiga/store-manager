package com.storemanager.sales.domain;

import com.storemanager.shared.domain.RucValidator;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * Stateless format validation for a {@link Sale}'s buyer reference, plus the S/700 "boleta
 * simple" rule (research.md): below the threshold a sale may omit the buyer document entirely;
 * at or above it a document is required and, when present, format-validated. Deliberately kept
 * here rather than in {@code RecordSaleUseCase} (see design.md's Architecture Decisions) — every
 * fact this needs (the buyer fields, the sale total) is already owned by {@link Sale} at
 * construction time, no repository access required.
 *
 * <p>DNI: exactly 8 numeric digits. RUC: delegated to the shared {@link RucValidator}. Carnet de Extranjería / Pasaporte: no authoritative fixed format was
 * found (research.md), so only light validation applies — non-blank, 5-20 characters.
 */
public final class BuyerDocumentValidator {

    /** Below this total a sale may be recorded with no buyer document at all ("boleta simple"). */
    public static final BigDecimal BOLETA_SIMPLE_THRESHOLD = new BigDecimal("700.00");

    private static final int CE_PASAPORTE_MIN_LENGTH = 5;
    private static final int CE_PASAPORTE_MAX_LENGTH = 20;

    private BuyerDocumentValidator() {}

    /**
     * Validates a buyer reference against {@code total}. Both {@code buyerDocumentType} and
     * {@code buyerDocumentNumber} blank (or {@code null}) is only allowed when {@code total} is
     * below {@link #BOLETA_SIMPLE_THRESHOLD}; otherwise both must be present together and the
     * number must match the format for the given type.
     *
     * @throws IllegalArgumentException when the buyer reference is missing but required, only one
     *     of the two fields is present, the document type is unsupported, or the document number
     *     doesn't match its type's format.
     */
    public static void validate(String buyerDocumentType, String buyerDocumentNumber, BigDecimal total) {
        boolean typeBlank = isBlank(buyerDocumentType);
        boolean numberBlank = isBlank(buyerDocumentNumber);

        if (typeBlank && numberBlank) {
            if (total != null && total.compareTo(BOLETA_SIMPLE_THRESHOLD) >= 0) {
                throw new IllegalArgumentException(
                        "Buyer document is required for sales of S/ " + BOLETA_SIMPLE_THRESHOLD + " or more");
            }
            return;
        }
        if (typeBlank || numberBlank) {
            throw new IllegalArgumentException(
                    "Buyer documentType and documentNumber must both be present, or both absent");
        }

        switch (buyerDocumentType.toUpperCase(Locale.ROOT)) {
            case "DNI" -> validateDni(buyerDocumentNumber);
            case "RUC" -> RucValidator.validate(buyerDocumentNumber);
            case "CE", "PASAPORTE", "PASSPORT" -> validateLight(buyerDocumentNumber);
            default -> throw new IllegalArgumentException("Unsupported buyer documentType: " + buyerDocumentType);
        }
    }

    private static void validateDni(String number) {
        if (!number.matches("\\d{8}")) {
            throw new IllegalArgumentException("DNI must be exactly 8 numeric digits");
        }
    }

    private static void validateLight(String number) {
        int length = number.trim().length();
        if (length < CE_PASAPORTE_MIN_LENGTH || length > CE_PASAPORTE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Document number must be between " + CE_PASAPORTE_MIN_LENGTH + " and " + CE_PASAPORTE_MAX_LENGTH
                            + " characters");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
