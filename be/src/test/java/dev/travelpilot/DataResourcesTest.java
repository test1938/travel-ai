package dev.travelpilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** The dataset in ../data is mapped onto the classpath by pom.xml (seed/ and eval/). */
class DataResourcesTest {

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    @Test
    void catalogsAndKnowledgeAreOnClasspathUnderSeed() throws IOException {
        assertThat(names("classpath:seed/catalog_*.json"))
                .containsExactlyInAnyOrder("catalog_hanoi.json", "catalog_danang.json", "catalog_quangninh.json");
        assertThat(names("classpath:seed/knowledge_*.jsonl"))
                .containsExactlyInAnyOrder("knowledge_hanoi.jsonl", "knowledge_danang.jsonl", "knowledge_quangninh.jsonl");
    }

    @Test
    void poolAndDocsAreNotPackaged() throws IOException {
        assertThat(resolver.getResources("classpath*:seed/places_pool_*.json")).isEmpty();
        assertThat(resolver.getResource("classpath:seed/README.md").exists()).isFalse();
    }

    @Test
    void goldenSetIsOnTestClasspath() {
        assertThat(resolver.getResource("classpath:eval/golden_set.json").exists()).isTrue();
    }

    private String[] names(String pattern) throws IOException {
        return Arrays.stream(resolver.getResources(pattern)).map(Resource::getFilename).toArray(String[]::new);
    }
}
