package com.storemanager.shared.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RucValidatorTest {

    @Test
    void acceptsARealSunatRuc() {
        assertThatCode(() -> RucValidator.validate("20131312955")).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnythingButElevenDigits() {
        assertThatThrownBy(() -> RucValidator.validate("2013131295"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RUC must be exactly 11 numeric digits");
        assertThatThrownBy(() -> RucValidator.validate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownTaxpayerPrefix() {
        assertThatThrownBy(() -> RucValidator.validate("30131312955"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("RUC prefix must be one of");
    }

    @Test
    void rejectsAWrongCheckDigit() {
        assertThatThrownBy(() -> RucValidator.validate("20131312956"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid RUC check digit");
    }
}
