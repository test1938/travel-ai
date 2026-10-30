package dev.travelpilot.security;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans free text before it reaches a prompt or the database: NFC, CRLF to LF, no control characters (except
 * newline and tab) and no invisible formatting characters (zero-width, bidi overrides) that can hide injected
 * instructions from a human reviewer. Too-long text is rejected with INPUT_TOO_LONG, never silently cut.
 */
public final class InputGuard {

    public static final int MAX_MESSAGE = 2000;
    public static final int MAX_QUESTION = 1000;
    public static final int MAX_NOTE = 200;

    /** C0/C1 controls except \t and \n, plus zero-width and bidirectional formatting characters. */
    private static final Pattern UNSAFE = Pattern.compile(
            "[\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]");

    private InputGuard() {}

    /** Cleaned text without the length check; null stays null. */
    public static String clean(String text) {
        if (text == null) {
            return null;
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).replace("\r\n", "\n").replace('\r', '\n');
        return UNSAFE.matcher(normalized).replaceAll("").strip();
    }

    /** Cleans and enforces the limit (counted on the cleaned text). */
    public static String clean(String text, int maxLength, String field) {
        String cleaned = clean(text);
        if (cleaned != null && cleaned.length() > maxLength) {
            throw new ApiException(ErrorCode.INPUT_TOO_LONG,
                    "Nội dung \"" + field + "\" quá dài (tối đa " + maxLength + " ký tự).", null);
        }
        return cleaned;
    }
}
