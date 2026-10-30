package dev.travelpilot.knowledge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code classpath:seed/knowledge_*.jsonl}. Strict: every error names the file and line, unknown fields,
 * missing required fields and duplicate docIds fail fast.
 */
@Component
public class KnowledgeLoader {

    static final String PATTERN = "classpath:seed/knowledge_*.jsonl";

    private final ResourcePatternResolver resolver;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public KnowledgeLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    KnowledgeLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public List<KnowledgeChunk> loadAll() {
        return loadAll(PATTERN);
    }

    List<KnowledgeChunk> loadAll(String pattern) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Resource resource : resources(pattern)) {
            for (KnowledgeChunk chunk : load(resource)) {
                if (!ids.add(chunk.docId())) {
                    throw new KnowledgeException(resource.getFilename() + ": duplicate docId " + chunk.docId());
                }
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    List<KnowledgeChunk> load(Resource resource) {
        String file = resource.getFilename();
        List<KnowledgeChunk> chunks = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = in.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                chunks.add(parseLine(file, lineNo, line));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return chunks;
    }

    private KnowledgeChunk parseLine(String file, int lineNo, String line) {
        KnowledgeChunk chunk;
        try {
            chunk = mapper.readValue(line, KnowledgeChunk.class);
        } catch (JacksonException e) {
            throw new KnowledgeException("%s:%d: invalid JSON: %s".formatted(file, lineNo, e.getOriginalMessage()), e);
        }
        List<String> missing = missingRequiredFields(chunk);
        if (!missing.isEmpty()) {
            throw new KnowledgeException("%s:%d: missing %s (docId=%s)".formatted(file, lineNo, missing, chunk.docId()));
        }
        return chunk;
    }

    private static List<String> missingRequiredFields(KnowledgeChunk c) {
        Map<String, Object> required = new LinkedHashMap<>();
        required.put("docId", c.docId());
        required.put("destination", c.destination());
        required.put("docType", c.docType());
        required.put("title", c.title());
        required.put("text", c.text());
        required.put("license", c.license());
        List<String> missing = new ArrayList<>();
        required.forEach((field, value) -> {
            if (value == null || (value instanceof String s && s.isBlank())) {
                missing.add(field);
            }
        });
        return missing;
    }

    private Resource[] resources(String pattern) {
        try {
            Resource[] found = resolver.getResources(pattern);
            if (found.length == 0) {
                throw new KnowledgeException("No knowledge files found at " + pattern);
            }
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The knowledge files on the classpath are unusable; the message names file and line. */
    public static class KnowledgeException extends RuntimeException {

        KnowledgeException(String message) {
            super(message);
        }

        KnowledgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
