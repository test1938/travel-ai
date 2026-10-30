package dev.travelpilot.playground;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.ai.AiCallLogger;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.support.StubChatModel;
import java.time.Duration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiChatController.class)
@Import({AiChatService.class, LlmGateway.class, GlobalExceptionHandler.class, AiChatControllerTest.Config.class})
@TestPropertySource(properties = {"travelpilot.ai.chat-timeout-seconds=1", "travelpilot.ai.retry.max-retries=0"})
@ActiveProfiles("dev")   // the playground only exists in dev
class AiChatControllerTest {

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

        @Bean
        AiCallLogger aiCallLogger() {
            return AiCallLogger.none();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StubChatModel model;

    @BeforeEach
    void reset() {
        model.reset();
    }

    @Test
    void returnsAnswerFromModel() throws Exception {
        model.reply("Đi Bà Nà Hills buổi sáng.");

        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Đà Nẵng có gì chơi?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Đi Bà Nà Hills buổi sáng."))
                .andExpect(jsonPath("$.latencyMs").isNumber());

        assertThat(model.prompts()).hasSize(1);
        assertThat(model.prompts().getFirst().getContents()).contains("Đà Nẵng có gì chơi?");
    }

    @Test
    void blankMessageIsRejectedWithoutCallingModel() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.message").exists());

        assertThat(model.prompts()).isEmpty();
    }

    @Test
    void messageLongerThan2000CharsIsRejected() throws Exception {
        String longMessage = "a".repeat(2001);
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + longMessage + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void providerErrorBecomes502() throws Exception {
        model.fail(new RuntimeException("upstream 500"));

        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("LLM_ERROR"))
                .andExpect(jsonPath("$.detail").value("Dịch vụ AI đang gặp lỗi, vui lòng thử lại."));
    }

    @Test
    void slowProviderBecomes504() throws Exception {
        model.replyAfter(Duration.ofSeconds(3), "too late");

        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("LLM_TIMEOUT"));
    }
}
