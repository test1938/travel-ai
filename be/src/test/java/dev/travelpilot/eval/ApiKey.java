package dev.travelpilot.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Eval tests call the real model; they only run when a provider key is set (env var or repo-root .env):
 * AI_API_KEY / GEMINI_API_KEY for Gemini, OPENAI_API_KEY for OpenAI. AI_PROVIDER picks which one is used.
 */
public final class ApiKey {

    private static final List<String> NAMES = List.of("AI_API_KEY", "GEMINI_API_KEY", "OPENAI_API_KEY");

    private ApiKey() {}

    public static boolean present() {
        for (String name : NAMES) {
            String env = System.getenv(name);
            if (env != null && !env.isBlank()) {
                return true;
            }
        }
        for (Path candidate : new Path[] {Path.of("../.env"), Path.of(".env")}) {
            try {
                if (Files.exists(candidate) && Files.readAllLines(candidate).stream()
                        .map(String::strip)
                        .anyMatch(l -> NAMES.stream()
                                .anyMatch(n -> l.startsWith(n + "=") && l.length() > n.length() + 1))) {
                    return true;
                }
            } catch (IOException ignored) {
                // unreadable .env counts as "no key"
            }
        }
        return false;
    }
}
