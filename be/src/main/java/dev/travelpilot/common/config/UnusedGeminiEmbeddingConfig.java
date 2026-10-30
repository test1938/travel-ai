package dev.travelpilot.common.config;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI creates the Gemini embedding connection even when another provider embeds, and it refuses to start
 * without a Gemini key. When Gemini does not embed, this inert connection takes its place (the auto-configured
 * one backs off), so an OpenAI-only setup needs no Gemini key. It is never used to call anything.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${spring.ai.model.embedding.text:google-genai}' != 'google-genai'")
class UnusedGeminiEmbeddingConfig {

    @Bean
    GoogleGenAiEmbeddingConnectionDetails unusedGeminiEmbeddingConnection() {
        return GoogleGenAiEmbeddingConnectionDetails.builder().apiKey("unused").build();
    }
}
