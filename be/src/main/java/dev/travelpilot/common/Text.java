package dev.travelpilot.common;

import java.text.Normalizer;
import java.util.Locale;

/** Text helpers shared by keyword matching (intent heuristic, tool gate, injection check). */
public final class Text {

    private Text() {}

    /** Lowercase, no Vietnamese accents ("Đà Nẵng" -> "da nang"), only letters, digits and single spaces. */
    public static String plain(String text) {
        String noMarks = Normalizer.normalize(text.toLowerCase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noMarks.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }
}
