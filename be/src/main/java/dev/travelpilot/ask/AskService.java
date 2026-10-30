package dev.travelpilot.ask;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.catalog.PlaceRepository;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.common.ai.PromptLoader;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.knowledge.Evidence;
import dev.travelpilot.knowledge.Retriever;
import dev.travelpilot.knowledge.Source;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * Grounded Q&A: retrieve passages for the destination, add catalog facts (prices and hours come only from the
 * catalog), let the model answer with [ev-n] citations. Without evidence the model is not called at all.
 */
@Service
public class AskService {

    static final String NO_EVIDENCE_ANSWER =
            "Mình chưa có đủ dữ liệu về câu này cho %s. Bạn có thể hỏi về điểm tham quan, ăn uống, đi lại, "
                    + "thời tiết hoặc lưu ý khi đi cùng trẻ em.";
    private static final Pattern CITATION = Pattern.compile("\\[(ev-\\d+)]");

    private final Retriever retriever;
    private final PlaceRepository places;
    private final PromptLoader prompts;
    private final ChatClient chatClient;
    private final LlmGateway gateway;

    public AskService(Retriever retriever, PlaceRepository places, PromptLoader prompts, ChatClient chatClient,
            LlmGateway gateway) {
        this.retriever = retriever;
        this.places = places;
        this.prompts = prompts;
        this.chatClient = chatClient;
        this.gateway = gateway;
    }

    public AskResponse ask(Destination destination, String question) {
        List<Evidence> evidence = retriever.search(destination, question);
        if (evidence.isEmpty()) {
            return new AskResponse(NO_EVIDENCE_ANSWER.formatted(destination.displayName()), List.of(), true, null);
        }
        List<Source> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < evidence.size(); i++) {
            String id = "ev-" + (i + 1);
            Evidence e = evidence.get(i);
            sources.add(Source.of(id, e));
            context.append('[').append(id).append("] ").append(e.content()).append("\n\n");
        }

        PromptLoader.VersionedPrompt prompt = prompts.load("ask");
        String system = prompt.renderSystem(Map.of("destination", destination.displayName()));
        String user = prompt.renderUser(Map.of(
                "context", context.toString().strip(),
                "facts", facts(evidence),
                "question", question));
        // Pre-rendered messages: knowledge text may contain "{", which ChatClient would treat as a template.
        Prompt modelPrompt = new Prompt(List.of(new SystemMessage(system), new UserMessage(user)));
        String answer = gateway.call("ASK", () -> chatClient.prompt(modelPrompt).call().content());
        return new AskResponse(answer, cited(answer, sources), false, prompt.id());
    }

    /** Catalog facts for every place mentioned by the retrieved passages. */
    private String facts(List<Evidence> evidence) {
        Set<String> ids = new LinkedHashSet<>();
        evidence.forEach(e -> ids.addAll(e.placeIds()));
        List<Place> found = places.findByIds(ids);
        if (found.isEmpty()) {
            return "(không có)";
        }
        StringBuilder sb = new StringBuilder();
        for (Place p : found) {
            String approx = p.isPriceEstimated() ? "khoảng " : "";
            sb.append("- ").append(p.name())
                    .append(": vé người lớn ").append(p.estCostVnd() == 0 ? "miễn phí" : approx + Vnd.format(p.estCostVnd()))
                    .append(", trẻ em ").append(p.childCostVnd() == 0 ? "miễn phí" : approx + Vnd.format(p.childCostVnd()))
                    .append("; giờ mở cửa (cú pháp OSM): ").append(p.openingHours())
                    .append("; ghi chú giá: ").append(p.priceNote())
                    .append('\n');
        }
        return sb.toString().strip();
    }

    /** Sources the answer actually cites, in citation order; all retrieved sources if it cites none. */
    static List<Source> cited(String answer, List<Source> sources) {
        Set<String> ids = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            ids.add(m.group(1));
        }
        List<Source> result = sources.stream().filter(s -> ids.contains(s.id())).toList();
        return result.isEmpty() ? sources : result;
    }
}
