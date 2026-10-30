package dev.travelpilot.catalog;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Upserts the curated catalog into {@code places} on startup. Idempotent: running it again updates rows in
 * place instead of duplicating them. Disable with {@code travelpilot.seed.enabled=false}.
 */
@Component
@Order(1)
@ConditionalOnProperty(prefix = "travelpilot.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final CatalogReader reader;
    private final PlaceRepository repository;

    public SeedLoader(CatalogReader reader, PlaceRepository repository) {
        this.reader = reader;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Returns the number of places in the table after seeding. */
    public long seed() {
        long start = System.currentTimeMillis();
        List<Place> places = reader.readAll();
        repository.upsertAll(places);
        long total = repository.count();
        log.info("Seeded {} catalog places ({} rows in table) in {} ms",
                places.size(), total, System.currentTimeMillis() - start);
        return total;
    }
}
