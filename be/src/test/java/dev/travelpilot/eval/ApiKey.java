package dev.travelpilot.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Eval tests call the real model; they only run when AI_API_KEY is set (env var or repo-root .env). */
public final class ApiKey {

    private ApiKey() {}

    public static boolean present() {
        String env = System.getenv("AI_API_KEY");
        if (env != null && !env.isBlank()) {
            return true;
        }
        for (Path candidate : new Path[] {Path.of("../.env"), Path.of(".env")}) {
            try {
                if (Files.exists(candidate) && Files.readAllLines(candidate).stream()
                        .map(String::strip)
                        .anyMatch(l -> l.startsWith("AI_API_KEY=") && l.length() > "AI_API_KEY=".length())) {
                    return true;
                }
            } catch (IOException ignored) {
                // unreadable .env counts as "no key"
            }
        }
        return false;
    }
}
