package dev.travelpilot.knowledge;

import java.util.List;

/**
 * A retrieved knowledge chunk. {@code content} is the full embedded text (title + text) for prompts;
 * {@link #snippet()} is the short version for the UI.
 */
public record Evidence(
        String docId,
        DocType docType,
        String placeId,
        List<String> placeIds,
        String title,
        String content,
        String source,
        String url,
        String license,
        double score) {

    public static final int SNIPPET_CHARS = 400;

    public Evidence {
        placeIds = placeIds == null ? List.of() : List.copyOf(placeIds);
    }

    public String snippet() {
        String body = content.startsWith(title + "\n") ? content.substring(title.length() + 1) : content;
        return body.length() <= SNIPPET_CHARS ? body : body.substring(0, SNIPPET_CHARS - 1).stripTrailing() + "…";
    }
}
