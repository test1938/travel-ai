package dev.travelpilot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Needs Docker; skipped otherwise. */
@IntegrationTest
class FlywayMigrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void v1MigrationSucceededAndVectorExtensionExists() {
        Boolean v1 = jdbc.sql("select success from flyway_schema_history where version = '1'")
                .query(Boolean.class).single();
        assertThat(v1).isTrue();

        Integer vector = jdbc.sql("select count(*) from pg_extension where extname = 'vector'")
                .query(Integer.class).single();
        assertThat(vector).isEqualTo(1);
    }

    @Test
    void vectorStoreTableIsCreatedWithConfiguredDimensions() {
        Integer rows = jdbc.sql("select count(*) from vector_store").query(Integer.class).single();
        assertThat(rows).isZero();
    }
}
