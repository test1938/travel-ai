package dev.travelpilot.common.ai;

import java.util.List;

/**
 * What a model call is for, as recorded in ai_call_logs: the operation (PLAN, REPAIR, ASK, INTENT, CHANGE, TOOLS,
 * CHAT), the prompt id (e.g. "ask-v3") and the ids of the retrieved passages sent with it.
 */
public record AiCall(String operation, String promptVersion, List<String> retrievedIds) {

    public AiCall {
        retrievedIds = retrievedIds == null ? List.of() : List.copyOf(retrievedIds);
    }

    public static AiCall of(String operation, String promptVersion) {
        return new AiCall(operation, promptVersion, List.of());
    }
}
