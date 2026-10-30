package dev.travelpilot.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.support.TestProps;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptLoaderTest {

    private static PromptLoader loaderWith(Map<String, String> versions) {
        TravelPilotProperties d = TestProps.defaults();
        return new PromptLoader(new TravelPilotProperties(d.ai(), d.scoring(), d.planner(), d.rag(), d.ingest(), versions));
    }

    @Test
    void versionInConfigSelectsTheFile() {
        PromptLoader.VersionedPrompt v1 = loaderWith(Map.of("demo", "v1")).load("demo");
        PromptLoader.VersionedPrompt v2 = loaderWith(Map.of("demo", "v2")).load("demo");

        assertThat(v1.id()).isEqualTo("demo-v1");
        assertThat(v2.id()).isEqualTo("demo-v2");
        assertThat(v1.renderUser(Map.of("question", "Bà Nà?"))).isEqualTo("Q1: Bà Nà?");
        assertThat(v2.renderUser(Map.of("question", "Bà Nà?"))).isEqualTo("Q2: Bà Nà?");
        assertThat(v2.renderSystem(Map.of("destination", "Đà Nẵng"))).isEqualTo("Demo system v2 for Đà Nẵng.");
    }

    @Test
    void askPromptRenders() {
        PromptLoader.VersionedPrompt ask = loaderWith(Map.of("ask", "v1")).load("ask");

        String user = ask.renderUser(Map.of("context", "[ev-1] (Bà Nà) ...", "facts", "-", "question", "Đi Bà Nà?"));

        assertThat(ask.id()).isEqualTo("ask-v1");
        assertThat(user).contains("<context>\n[ev-1] (Bà Nà) ...\n</context>").contains("<question>Đi Bà Nà?</question>");
        assertThat(ask.renderSystem(Map.of("destination", "Đà Nẵng"))).contains("for Đà Nẵng, Vietnam");
    }

    @Test
    void missingVersionOrFileFailsClearly() {
        assertThatThrownBy(() -> loaderWith(Map.of("demo", "v1")).load("ask"))
                .hasMessageContaining("No version configured for prompt 'ask'");
        assertThatThrownBy(() -> loaderWith(Map.of("demo", "v9")).load("demo"))
                .hasMessageContaining("classpath:prompts/demo-v9.system.st");
    }
}
