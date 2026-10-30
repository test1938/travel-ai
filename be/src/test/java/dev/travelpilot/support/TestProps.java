package dev.travelpilot.support;

import dev.travelpilot.common.config.TravelPilotProperties;
import java.util.Map;

/** The same values as application.yml, for plain unit tests that do not start Spring. */
public final class TestProps {

    private TestProps() {}

    public static TravelPilotProperties defaults() {
        return new TravelPilotProperties(
                new TravelPilotProperties.Ai(30),
                new TravelPilotProperties.Scoring(
                        new TravelPilotProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10)),
                new TravelPilotProperties.Planner(2, 40),
                new TravelPilotProperties.Rag(5, 0.45),
                new TravelPilotProperties.Ingest(50),
                new TravelPilotProperties.Weather(false, "https://api.open-meteo.com", 3, 60),
                Map.of("ask", "v3", "planner", "v1", "repair", "v1", "intent", "v1", "change", "v1", "tools", "v1",
                        "replan", "v1"));
    }
}
