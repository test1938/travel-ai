package dev.travelpilot.eval;

import dev.travelpilot.common.domain.Destination;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** One retrieval question from data/golden_set.json (on the test classpath as eval/golden_set.json). */
public record GoldenCase(
        String id,
        Destination destination,
        String question,
        List<String> expectedDocIds,
        List<String> expectedPlaceIds,
        List<String> mustMention) {

    public GoldenCase {
        expectedDocIds = List.copyOf(expectedDocIds);
        expectedPlaceIds = expectedPlaceIds == null ? List.of() : List.copyOf(expectedPlaceIds);
        mustMention = mustMention == null ? List.of() : List.copyOf(mustMention);
    }

    public static List<GoldenCase> load() {
        try (InputStream in = new ClassPathResource("eval/golden_set.json").getInputStream()) {
            return JsonMapper.builder().build().readValue(in, new TypeReference<List<GoldenCase>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
