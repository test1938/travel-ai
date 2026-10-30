package dev.travelpilot.support;

import dev.travelpilot.common.ai.AiCallLog;
import dev.travelpilot.common.ai.AiCallLogStore;
import dev.travelpilot.common.ai.AiCallLogger;
import java.util.ArrayList;
import java.util.List;

/** In-memory ai_call_logs for tests: {@link #logger()} records into {@link #rows()}. */
public class RecordedAiCalls implements AiCallLogStore {

    private final List<AiCallLog> rows = new ArrayList<>();

    @Override
    public synchronized void insert(AiCallLog row) {
        rows.add(row);
    }

    public synchronized List<AiCallLog> rows() {
        return List.copyOf(rows);
    }

    public List<String> operations() {
        return rows().stream().map(AiCallLog::operation).toList();
    }

    public synchronized void clear() {
        rows.clear();
    }

    public AiCallLogger logger() {
        return new AiCallLogger(this);
    }
}
