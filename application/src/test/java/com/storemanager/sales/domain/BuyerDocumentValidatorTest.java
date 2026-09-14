package com.storemanager.sales.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * DNI/RUC/CE/Pasaporte format rules and the S/700 "boleta simple" threshold, per research.md and
 * design.md's Testing Strategy. The RUC check-digit cases are anchored on SUNAT's own real RUC
 * ({@code 20131312955}, verified in research.md: weighted sum 94, {@code 94 mod 11 = 6}, check
 * digit {@code 11 - 6 = 5}), not just synthetic values.
 */
class BuyerDocumentValidatorTest {

    private static final BigDecimal BELOW_THRESHOLD = new BigDecimal("699.99");
    private static final BigDecimal AT_THRESHOLD = new BigDecimal("700.00");
    private static final BigDecimal ABOVE_THRESHOLD = new BigDecimal("700.01");

    @Test
    void acceptsSunatsOwnRealRuc() {
        assertThatCode(() -> BuyerDocumentValidator.validate("RUC", "20131312955", ABOVE_THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsARucWithAFlippedCheckDigit() {
        // Real RUC 20131312955 with its last digit changed from 5 to 6.
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("RUC", "20131312956", ABOVE_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucWithAnInvalidPrefix() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("RUC", "99131312955", ABOVE_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucWithTheWrongDigitCount() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("RUC", "2013131295", ABOVE_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADniWithSevenDigits() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("DNI", "1234567", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADniWithNineDigits() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("DNI", "123456789", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADniContainingLetters() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("DNI", "1234567A", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsADniOfExactlyEightDigits() {
        assertThatCode(() -> BuyerDocumentValidator.validate("DNI", "12345678", BELOW_THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsABlankCarnetDeExtranjeria() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("CE", "   ", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAReasonableCarnetDeExtranjeriaValue() {
        assertThatCode(() -> BuyerDocumentValidator.validate("CE", "001234567", BELOW_THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsABlankPasaporte() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("PASAPORTE", "", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsAReasonablePasaporteValue() {
        assertThatCode(() -> BuyerDocumentValidator.validate("PASAPORTE", "AB123456", BELOW_THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNoDocumentWhenTotalIsBelowTheThreshold() {
        assertThatCode(() -> BuyerDocumentValidator.validate(null, null, BELOW_THRESHOLD))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNoDocumentWhenTotalIsAtTheThreshold() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate(null, null, AT_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNoDocumentWhenTotalIsAboveTheThreshold() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate(" ", " ", ABOVE_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADocumentTypeWithNoDocumentNumber() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("DNI", null, BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADocumentNumberWithNoDocumentType() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate(null, "12345678", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnsupportedDocumentType() {
        assertThatThrownBy(() -> BuyerDocumentValidator.validate("PARTIDA", "12345678", BELOW_THRESHOLD))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
