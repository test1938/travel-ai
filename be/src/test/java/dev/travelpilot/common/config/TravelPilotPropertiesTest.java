package dev.travelpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class TravelPilotPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TravelPilotProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "travelpilot.ai.chat-timeout-seconds=30",
                    "travelpilot.scoring.weights.preference=0.30",
                    "travelpilot.scoring.weights.budget=0.20",
                    "travelpilot.scoring.weights.distance=0.15",
                    "travelpilot.scoring.weights.schedule=0.15",
                    "travelpilot.scoring.weights.group=0.10",
                    "travelpilot.scoring.weights.popularity=0.10",
                    "travelpilot.planner.max-repairs=2",
                    "travelpilot.planner.timeout-seconds=40",
                    "travelpilot.rag.top-k=5",
                    "travelpilot.rag.threshold=0.45",
                    "travelpilot.ingest.batch-size=50",
                    "travelpilot.prompts.ask=v1");

    @Test
    void bindsAllSettings() {
        runner.run(ctx -> {
            TravelPilotProperties props = ctx.getBean(TravelPilotProperties.class);
            assertThat(props.scoring().weights().preference()).isEqualTo(0.30);
            assertThat(props.planner().maxRepairs()).isEqualTo(2);
            assertThat(props.rag().topK()).isEqualTo(5);
            assertThat(props.ai().chatTimeoutSeconds()).isEqualTo(30);
        });
    }

    @Test
    void changedWeightInConfigIsPickedUp() {
        runner.withPropertyValues(
                        "travelpilot.scoring.weights.preference=0.40",
                        "travelpilot.scoring.weights.popularity=0.00")
                .run(ctx -> assertThat(ctx.getBean(TravelPilotProperties.class).scoring().weights().preference())
                        .isEqualTo(0.40));
    }

    @Test
    void weightsThatDoNotSumToOneStopTheApplication() {
        runner.withPropertyValues("travelpilot.scoring.weights.preference=0.90")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("must add up to 1.0"));
    }

    @Test
    void outOfRangeValueIsRejected() {
        runner.withPropertyValues("travelpilot.rag.top-k=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void applicationYamlWeightsAreValid() {
        var weights = new TravelPilotProperties.Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10);
        assertThat(weights.budget()).isEqualTo(0.20);
    }
}
