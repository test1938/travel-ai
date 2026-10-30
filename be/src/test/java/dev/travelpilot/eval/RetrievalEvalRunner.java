package dev.travelpilot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.ask.AskService;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.eval.RetrievalMetrics.CaseResult;
import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.knowledge.IngestionService;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.support.PgvectorContainerConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Retrieval quality on the golden set with the REAL embedding model (and, unless -Deval.ask=false, the real
 * chat model for the mustMention check). Run: {@code ./mvnw test -Dgroups=eval -Dtest=RetrievalEvalRunner}.
 * Needs Docker and AI_API_KEY. Writes be/eval/results/retrieval-YYYYMMDD.md; it reports numbers, it does not
 * fail on a low score.
 */
@Tag("eval")
@SpringBootTest
@Import(PgvectorContainerConfig.class)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dev.travelpilot.eval.ApiKey#present")
class RetrievalEvalRunner {

    static final int TOP_K = 5;

    @Autowired
    IngestionService ingestion;

    @Autowired
    Retriever retriever;

    @Autowired
    AskService askService;

    @Value("${spring.ai.google.genai.embedding.text.model}")
    String embeddingModel;

    @Value("${spring.ai.google.genai.chat.model}")
    String chatModel;

    @Test
    void goldenSet() throws IOException {
        boolean checkAnswers = Boolean.parseBoolean(System.getProperty("eval.ask", "true"));
        ingestion.ingestAll();

        List<CaseResult> results = new ArrayList<>();
        for (GoldenCase golden : GoldenCase.load()) {
            List<String> retrieved = retriever.search(golden.destination(), golden.question(), TOP_K, 0.0)
                    .stream().map(Evidence::docId).toList();
            Boolean mentionOk = checkAnswers && !golden.mustMention().isEmpty()
                    ? mentionsAll(golden.destination(), golden.question(), golden.mustMention())
                    : null;
            results.add(CaseResult.of(golden, retrieved, mentionOk));
        }

        LocalDate today = LocalDate.now();
        String config = "embedding=%s, chat=%s, topK=%d, threshold=0 (raw ranking), ask check=%s"
                .formatted(embeddingModel, chatModel, TOP_K, checkAnswers);
        String report = RetrievalReport.markdown(today, config, results);
        Path out = Path.of("eval", "results", "retrieval-" + today.format(DateTimeFormatter.BASIC_ISO_DATE) + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.println(report);
        System.out.println("Report written to " + out.toAbsolutePath());

        assertThat(results).hasSize(24);
    }

    private boolean mentionsAll(Destination destination, String question, List<String> mustMention) {
        String answer = askService.ask(destination, question).answer().toLowerCase(Locale.ROOT);
        return mustMention.stream().allMatch(m -> answer.contains(m.toLowerCase(Locale.ROOT)));
    }
}
