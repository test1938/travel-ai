package dev.travelpilot.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the curated catalogs from {@code classpath:seed/catalog_*.json} (mapped from data/ by pom.xml).
 * Strict: unknown fields, unknown enum values, missing required fields and duplicate ids all fail fast.
 */
@Component
public class CatalogReader {

    static final String PATTERN = "classpath:seed/catalog_*.json";

    private final ResourcePatternResolver resolver;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public CatalogReader() {
        this(new PathMatchingResourcePatternResolver());
    }

    CatalogReader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    public List<Place> readAll() {
        return readAll(PATTERN);
    }

    List<Place> readAll(String pattern) {
        List<Place> places = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Resource resource : resources(pattern)) {
            for (Place place : read(resource)) {
                if (!ids.add(place.id())) {
                    throw new CatalogException(resource.getFilename() + ": duplicate place id " + place.id());
                }
                places.add(place);
            }
        }
        return places;
    }

    List<Place> read(Resource resource) {
        String file = resource.getFilename();
        List<PlaceJson> rows;
        try (InputStream in = resource.getInputStream()) {
            rows = mapper.readValue(in, new TypeReference<List<PlaceJson>>() {});
        } catch (JacksonException e) {
            throw new CatalogException(file + ": invalid catalog JSON: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        List<Place> places = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            PlaceJson row = rows.get(i);
            List<String> missing = row.missingRequiredFields();
            if (!missing.isEmpty()) {
                throw new CatalogException(
                        "%s: element %d (id=%s) is missing %s".formatted(file, i, row.id(), missing));
            }
            places.add(row.toPlace());
        }
        return places;
    }

    private Resource[] resources(String pattern) {
        try {
            Resource[] found = resolver.getResources(pattern);
            if (found.length == 0) {
                throw new CatalogException("No catalog files found at " + pattern);
            }
            return found;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The dataset on the classpath is unusable; the message names the file and the element. */
    public static class CatalogException extends RuntimeException {

        CatalogException(String message) {
            super(message);
        }

        CatalogException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
