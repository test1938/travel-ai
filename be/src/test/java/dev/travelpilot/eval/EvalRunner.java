package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.catalog.CatalogReader;
import dev.travelpilot.catalog.Place;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.knowledge.IngestionService;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.support.PgvectorContainerConfig;
import dev.travelpilot.trip.TripService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Planner + chat eval on be/eval/cases.json with the REAL model, through the real orchestrator, TripService and
 * ChatService. Run: {@code ./mvnw test -Dgroups=eval -Dtest=EvalRunner}. Needs Docker and AI_API_KEY. Reports
 * numbers; it does not fail on low scores. Weather is off so runs are comparable.
 */
@Tag("eval")
@SpringBootTest(properties = {"travelpilot.weather.enabled=false", "travelpilot.security.rate-limit.enabled=false"})
@Import(PgvectorContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dev.travelpilot.eval.ApiKey#present")
class EvalRunner {

    @Autowired
    IngestionService ingestion;

    @Autowired
    PlannerOrchestrator orchestrator;

    @Autowired
    TripService trips;

    @Autowired
    ChatService chat;

    @Autowired
    MeterRegistry meters;

    @Value("${spring.ai.google.genai.chat.model}")
    String chatModel;

    @Test
    void cases() {
        ingestion.ingestAll();
        Map<String, Place> catalog = new CatalogReader().readAll().stream()
                .collect(Collectors.toMap(Place::id, Function.identity()));
        EvalTarget target = new SpringEvalTarget(orchestrator, trips, chat, meters, chatModel);

        List<CaseResult> results = new EvalEngine(catalog, LocalDate.now(), null).run(EvalCase.load(), target);

        System.out.println(EvalRunSummary.text(results));
        assertThat(results).hasSize(20);
    }
}
