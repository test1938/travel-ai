package dev.travelpilot.common.ai;

/** Where ai_call_logs rows go (port); the app writes to Postgres, unit tests collect rows in a list. */
@FunctionalInterface
public interface AiCallLogStore {

    void insert(AiCallLog row);
}
