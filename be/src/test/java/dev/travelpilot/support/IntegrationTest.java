package dev.travelpilot.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full application context against a real pgvector database (Testcontainers) and offline AI stubs.
 * Skipped automatically when Docker is not available.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import({PgvectorContainerConfig.class, StubAiConfig.class})
@Testcontainers(disabledWithoutDocker = true)
public @interface IntegrationTest {}
