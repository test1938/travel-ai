package dev.travelpilot.knowledge;

/** A citation shown to the user; {@code id} is the evidence id used in the answer, e.g. "ev-2". */
public record Source(String id, String docId, String title, String url, String license, String snippet) {

    public static Source of(String id, Evidence e) {
        return new Source(id, e.docId(), e.title(), firstUrl(e.url()), e.license(), e.snippet());
    }

    /** PLACE chunks may list several URLs separated by spaces; the first is the most specific. */
    private static String firstUrl(String urls) {
        if (urls == null || urls.isBlank()) {
            return null;
        }
        return urls.trim().split("\\s+")[0];
    }
}
