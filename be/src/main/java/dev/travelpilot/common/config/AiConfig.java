package dev.travelpilot.common.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    static final String PLAYGROUND_SYSTEM_PROMPT = """
            You are TravelPilot, a travel assistant for Hanoi, Da Nang and Quang Ninh (Ha Long) in Vietnam.
            Answer briefly in the user's language. If you are not sure about prices or opening hours, say so.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem(PLAYGROUND_SYSTEM_PROMPT).build();
    }

    /** Runs blocking LLM calls so they can be abandoned on timeout. */
    @Bean(destroyMethod = "close")
    ExecutorService llmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
