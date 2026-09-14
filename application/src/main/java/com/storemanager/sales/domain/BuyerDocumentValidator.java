package com.storemanager.sales.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/**
 * Stateless format validation for a {@link Sale}'s buyer reference, plus the S/700 "boleta
 * simple" rule (research.md): below the threshold a sale may omit the buyer document entirely;
 * at or above it a document is required and, when present, format-validated. Deliberately kept
 * here rather than in {@code RecordSaleUseCase} (see design.md's Architecture Decisions) — every
 * fact this needs (the buyer fields, the sale total) is already owned by {@link Sale} at
 * construction time, no repository access required.
 *
 * <p>DNI: exactly 8 numeric digits. RUC: 11 numeric digits, a taxpayer-type prefix in {@code {10,
 * 15, 17, 20}}, and a modulo-11 check digit (weights {@code 5,4,3,2,7,6,5,4,3,2} on the first 10
 * digits; {@code checkDigit = 11 - (sum mod 11)}, with {@code 10 -> 0} and {@code 11 -> 1}).
 * Verified against SUNAT's own real RUC {@code 20131312955} (weighted sum 94, {@code 94 mod 11 =
 * 6}, {@code 11 - 6 = 5}). Carnet de Extranjería / Pasaporte: no authoritative fixed format was
 * found (research.md), so only light validation applies — non-blank, 5-20 characters.
 */
public final class BuyerDocumentValidator {

    /** Below this total a sale may be recorded with no buyer document at all ("boleta simple"). */
    public static final BigDecimal BOLETA_SIMPLE_THRESHOLD = new BigDecimal("700.00");

    private static final Set<String> VALID_RUC_PREFIXES = Set.of("10", "15", "17", "20");
    private static final int[] RUC_CHECK_DIGIT_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

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
            case "RUC" -> validateRuc(buyerDocumentNumber);
            case "CE", "PASAPORTE", "PASSPORT" -> validateLight(buyerDocumentNumber);
            default -> throw new IllegalArgumentException("Unsupported buyer documentType: " + buyerDocumentType);
        }
    }

    private static void validateDni(String number) {
        if (!number.matches("\\d{8}")) {
            throw new IllegalArgumentException("DNI must be exactly 8 numeric digits");
        }
    }

    private static void validateRuc(String number) {
        if (!number.matches("\\d{11}")) {
            throw new IllegalArgumentException("RUC must be exactly 11 numeric digits");
        }
        String prefix = number.substring(0, 2);
        if (!VALID_RUC_PREFIXES.contains(prefix)) {
            throw new IllegalArgumentException("RUC prefix must be one of " + VALID_RUC_PREFIXES);
        }
        int weightedSum = 0;
        for (int i = 0; i < RUC_CHECK_DIGIT_WEIGHTS.length; i++) {
            weightedSum += Character.getNumericValue(number.charAt(i)) * RUC_CHECK_DIGIT_WEIGHTS[i];
        }
        int expectedCheckDigit = 11 - (weightedSum % 11);
        if (expectedCheckDigit == 10) {
            expectedCheckDigit = 0;
        } else if (expectedCheckDigit == 11) {
            expectedCheckDigit = 1;
        }
        int actualCheckDigit = Character.getNumericValue(number.charAt(10));
        if (expectedCheckDigit != actualCheckDigit) {
            throw new IllegalArgumentException("Invalid RUC check digit");
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
