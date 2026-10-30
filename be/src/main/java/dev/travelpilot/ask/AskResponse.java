package dev.travelpilot.ask;

import dev.travelpilot.knowledge.Source;
import java.util.List;

/** {@code noEvidence} = nothing relevant was retrieved and the model was not called. */
public record AskResponse(String answer, List<Source> sources, boolean noEvidence, String promptVersion) {

    public AskResponse {
        sources = List.copyOf(sources);
    }
}
