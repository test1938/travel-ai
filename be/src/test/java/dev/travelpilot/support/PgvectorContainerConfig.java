package dev.travelpilot.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Postgres 16 + pgvector in Docker; Spring Boot wires the datasource to it via @ServiceConnection. */
@TestConfiguration(proxyBeanMethods = false)
public class PgvectorContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer pgvector() {
        return new PostgreSQLContainer(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }
}
