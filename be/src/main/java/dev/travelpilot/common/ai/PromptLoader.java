package dev.travelpilot.common.ai;

import dev.travelpilot.common.config.TravelPilotProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads versioned prompt templates from {@code classpath:prompts/<name>-<version>.system.st} and
 * {@code .user.st}. The version comes from {@code travelpilot.prompts.<name>}, so switching versions needs no
 * code change. Placeholders use StringTemplate syntax: {@code {question}}.
 */
@Component
public class PromptLoader {

    private final TravelPilotProperties props;
    private final Map<String, VersionedPrompt> cache = new ConcurrentHashMap<>();

    public PromptLoader(TravelPilotProperties props) {
        this.props = props;
    }

    /** A system + user template pair; {@link #id()} (e.g. "ask-v1") is what we log as promptVersion. */
    public record VersionedPrompt(String name, String version, String system, String user) {

        public String id() {
            return name + "-" + version;
        }

        public String renderSystem(Map<String, Object> params) {
            return render(system, params);
        }

        public String renderUser(Map<String, Object> params) {
            return render(user, params);
        }

        private static String render(String template, Map<String, Object> params) {
            // StringTemplate writes the OS line separator; keep "\n" so prompts are identical on every OS.
            return params.isEmpty() ? template : new PromptTemplate(template).render(params).replace("\r\n", "\n");
        }
    }

    public VersionedPrompt load(String name) {
        String version = props.promptVersion(name);
        return cache.computeIfAbsent(name + "-" + version,
                id -> new VersionedPrompt(name, version, read(id + ".system.st"), read(id + ".user.st")));
    }

    private static String read(String file) {
        Resource resource = new ClassPathResource("prompts/" + file);
        if (!resource.exists()) {
            throw new IllegalStateException("Prompt file not found: classpath:prompts/" + file);
        }
        try {
            // Normalise Windows line endings so prompts (and their token counts) are identical on every OS.
            return resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt " + file, e);
        }
    }
}
