package com.springbootai.incidentiq.util;

import java.util.regex.Pattern;

/**
 * Shared text rules, so request validation (DTO annotations) and domain validation (entities)
 * agree on what "blank" means.
 * <p>
 * Whitespace follows the Unicode {@code White_Space} property, so text consisting only of, for
 * example, no-break spaces ({@code U+00A0}) or ideographic spaces ({@code U+3000}) counts as blank.
 */
public final class TextRules {

    /**
     * For {@code @Pattern}: matches text containing at least one non-whitespace character.
     * {@code null} values are not checked by {@code @Pattern}; combine with {@code @NotNull} if required.
     */
    public static final String NOT_BLANK_PATTERN = "(?sU).*\\S.*";

    private static final Pattern NON_WHITESPACE = Pattern.compile("\\S", Pattern.UNICODE_CHARACTER_CLASS);

    private TextRules() {
    }

    /** Returns {@code true} if the text is {@code null} or contains only Unicode whitespace. */
    public static boolean isBlank(String text) {
        return text == null || !NON_WHITESPACE.matcher(text).find();
    }

    /** PostgreSQL text columns cannot store the NUL character ({@code U+0000}). */
    public static boolean containsNul(String text) {
        return text != null && text.indexOf('\u0000') >= 0;
    }
}
