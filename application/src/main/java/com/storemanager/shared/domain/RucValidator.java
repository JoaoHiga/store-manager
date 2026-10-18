package com.storemanager.shared.domain;

import java.util.Set;

/**
 * Format validation for a Peruvian RUC (SUNAT taxpayer number), shared by every domain that stores
 * one (sales buyers, purchasing suppliers).
 *
 * <p>11 numeric digits, a taxpayer-type prefix in {@code {10, 15, 17, 20}}, and a modulo-11 check
 * digit (weights {@code 5,4,3,2,7,6,5,4,3,2} on the first 10 digits; {@code checkDigit = 11 - (sum
 * mod 11)}, with {@code 10 -> 0} and {@code 11 -> 1}). Verified against SUNAT's own real RUC
 * {@code 20131312955} (weighted sum 94, {@code 94 mod 11 = 6}, {@code 11 - 6 = 5}).
 */
public final class RucValidator {

    private static final Set<String> VALID_RUC_PREFIXES = Set.of("10", "15", "17", "20");
    private static final int[] RUC_CHECK_DIGIT_WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private RucValidator() {}

    /** @throws IllegalArgumentException when {@code number} is not a well-formed RUC. */
    public static void validate(String number) {
        if (number == null || !number.matches("\\d{11}")) {
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
}
