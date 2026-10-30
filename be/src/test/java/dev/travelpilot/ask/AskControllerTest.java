package dev.travelpilot.ask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.catalog.CatalogReader;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.knowledge.DocType;
import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.support.StubChatModel;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AskController.class)
@Import({AskService.class, PromptLoader.class, LlmGateway.class, GlobalExceptionHandler.class,
        AskControllerTest.Config.class})
class AskControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TravelPilotProperties.class)
    static class Config {
        @Bean
        StubChatModel stubChatModel() {
            return new StubChatModel();
        }

        @Bean
        ChatClient chatClient(StubChatModel model) {
            return ChatClient.create(model);
        }

        @Bean(destroyMethod = "close")
        ExecutorService llmExecutor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    static final String BA_NA_ID = "danang-way-359114064";
    static final Place BA_NA = new CatalogReader().readAll().stream()
            .filter(p -> p.id().equals(BA_NA_ID)).findFirst().orElseThrow();
    static final Evidence BA_NA_EVIDENCE = new Evidence("danang-place-" + BA_NA_ID, DocType.PLACE, BA_NA_ID,
            List.of(BA_NA_ID), "Sun World Bà Nà Hills", "Sun World Bà Nà Hills\nKhu du lịch trên núi {cáp treo}.",
            "TravelPilot curation", "https://www.openstreetmap.org/way/359114064 https://vi.wikipedia.org/x",
            "ODbL", 0.82);
    static final Evidence WEATHER_EVIDENCE = new Evidence("danang-tips-weather-05", DocType.TIPS, null, List.of(),
            "Đà Nẵng – Thời tiết › Bà Nà", "Đà Nẵng – Thời tiết › Bà Nà\nTrên núi lạnh, nên mang áo khoác.",
            "TravelPilot (viết tay)", "", "CC BY-SA 4.0", 0.74);

    @Autowired
    MockMvc mvc;

    @Autowired
    StubChatModel model;

    @MockitoBean
    Retriever retriever;

    @MockitoBean
    PlaceRepository places;

    @BeforeEach
    void reset() {
        model.reset();
    }

    @Test
    void answersFromEvidenceWithCatalogFactsAndCitedSources() throws Exception {
        when(retriever.search(eq(Destination.DANANG), anyString())).thenReturn(List.of(BA_NA_EVIDENCE, WEATHER_EVIDENCE));
        when(places.findByIds(any())).thenReturn(List.of(BA_NA));
        model.reply("Nên mang áo khoác vì trên núi lạnh [ev-2].");

        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"DANANG\",\"question\":\"Đi Bà Nà cần chuẩn bị gì?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEvidence").value(false))
                .andExpect(jsonPath("$.answer").value("Nên mang áo khoác vì trên núi lạnh [ev-2]."))
                .andExpect(jsonPath("$.sources.length()").value(1))
                .andExpect(jsonPath("$.sources[0].id").value("ev-2"))
                .andExpect(jsonPath("$.sources[0].docId").value("danang-tips-weather-05"))
                .andExpect(jsonPath("$.promptVersion").value("ask-v3"));

        String sent = model.prompts().getFirst().getContents();
        assertThat(sent).contains("[ev-1] Sun World Bà Nà Hills").contains("[ev-2] Đà Nẵng – Thời tiết")
                .contains("{cáp treo}")                       // braces in knowledge text survive unchanged
                .contains("vé người lớn 1.000.000đ")            // price comes from the catalog, not the passage
                .contains("<question>Đi Bà Nà cần chuẩn bị gì?</question>")
                .contains("trẻ em từ 0 tuổi (chưa xác minh)")   // Bà Nà childMinAge is flagged for review
                .contains("never present it as certain");      // ask-v2+ rule for unverified facts
    }

    @Test
    void noEvidenceDoesNotCallTheModel() throws Exception {
        when(retriever.search(eq(Destination.HANOI), anyString())).thenReturn(List.of());

        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"HANOI\",\"question\":\"Giá bitcoin hôm nay?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noEvidence").value(true))
                .andExpect(jsonPath("$.sources.length()").value(0));

        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void modelFailureIs502() throws Exception {
        when(retriever.search(eq(Destination.DANANG), anyString())).thenReturn(List.of(WEATHER_EVIDENCE));
        when(places.findByIds(any())).thenReturn(List.of());
        model.fail(new RuntimeException("quota exceeded"));

        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"DANANG\",\"question\":\"Thời tiết?\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_ERROR"));
    }

    @Test
    void invalidRequestIs400() throws Exception {
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"PHUQUOC\",\"question\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\":\"HANOI\",\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void uncitedAnswerReturnsAllSources() {
        var sources = List.of(dev.travelpilot.knowledge.Source.of("ev-1", BA_NA_EVIDENCE),
                dev.travelpilot.knowledge.Source.of("ev-2", WEATHER_EVIDENCE));
        assertThat(AskService.cited("Không trích dẫn.", sources)).hasSize(2);
        assertThat(AskService.cited("A [ev-1] B [ev-1]", sources)).extracting(s -> s.id()).containsExactly("ev-1");
        assertThat(sources.getFirst().url()).isEqualTo("https://www.openstreetmap.org/way/359114064");
    }
}
