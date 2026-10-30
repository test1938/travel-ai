package dev.travelpilot.common.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /** Plain client without a default system prompt: every feature sends its own versioned prompt. */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /** Runs blocking LLM calls so they can be abandoned on timeout (see LlmGateway). */
    @Bean(destroyMethod = "close")
    ExecutorService llmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
