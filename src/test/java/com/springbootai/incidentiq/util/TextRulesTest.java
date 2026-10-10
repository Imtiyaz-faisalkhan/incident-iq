package com.springbootai.incidentiq.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextRulesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "\t\n\r", " ", " ", " ", "　", "  　 "})
    void blankTextIsBlank(String text) {
        assertThat(TextRules.isBlank(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", " x ", " x ", "　日本", "db timeouts"})
    void textWithVisibleCharactersIsNotBlank(String text) {
        assertThat(TextRules.isBlank(text)).isFalse();
    }

    /** The DTO regex and the entity check must agree, otherwise POST and PATCH behave differently. */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n", " ", " ", "　", "x", " x ", " x", "line1\nline2"})
    void notBlankPatternAgreesWithIsBlank(String text) {
        assertThat(text.matches(TextRules.NOT_BLANK_PATTERN)).isEqualTo(!TextRules.isBlank(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0000", "db\u0000down", "end\u0000"})
    void detectsNulCharacters(String text) {
        assertThat(TextRules.containsNul(text)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "db down", " "})
    void textWithoutNulIsAccepted(String text) {
        assertThat(TextRules.containsNul(text)).isFalse();
    }
}
