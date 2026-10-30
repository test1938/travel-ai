package dev.travelpilot.integration.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.support.TestProps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Uses a recorded response (fixtures/weather) through MockRestServiceServer: no network. */
class OpenMeteoClientTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    private MockRestServiceServer server;
    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.ofHours(7)), Destination.ZONE);
        client = new OpenMeteoClient(builder, TestProps.defaults(), clock);
    }

    private static String fixture() throws IOException {
        return new ClassPathResource("fixtures/weather/open-meteo-danang.json")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void mapsCodesAndRainProbabilityToRainyDays() throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "16.0544"))
                .andExpect(queryParam("timezone", "Asia/Ho_Chi_Minh"))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        Forecast f = client.forecast(Destination.DANANG, List.of(TODAY, TODAY.plusDays(1), TODAY.plusDays(2),
                TODAY.plusDays(3), TODAY.plusDays(4)));

        assertThat(f.isRainy(TODAY)).isFalse();                       // overcast, 20 %
        assertThat(f.isRainy(TODAY.plusDays(1))).isTrue();            // showers code 80 although only 45 %
        assertThat(f.isRainy(TODAY.plusDays(2))).isTrue();            // partly cloudy but 65 %
        assertThat(f.isRainy(TODAY.plusDays(3))).isTrue();            // thunderstorm
        assertThat(f.isRainy(TODAY.plusDays(4))).isFalse();           // no data: unknown, treated as dry
        DayWeather day1 = f.days().get(TODAY);
        assertThat(day1.tempMinC()).isEqualTo(23.8);
        assertThat(day1.tempMaxC()).isEqualTo(29.4);
        assertThat(day1.summary()).isEqualTo("Nhiều mây (20% mưa)");
        assertThat(f.days().get(TODAY.plusDays(3)).summary()).isEqualTo("Dông (90% mưa)");
        server.verify();
    }

    @Test
    void datesOutsideTheForecastAreAbsent() throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        Forecast f = client.forecast(Destination.DANANG, List.of(TODAY.plusDays(30)));

        assertThat(f.isEmpty()).isTrue();
    }

    @Test
    void cachesOneRequestPerDestinationAndDay() throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        client.forecast(Destination.DANANG, List.of(TODAY));
        Forecast second = client.forecast(Destination.DANANG, List.of(TODAY.plusDays(1)));

        assertThat(second.isRainy(TODAY.plusDays(1))).isTrue();
        server.verify(); // exactly one HTTP call
    }

    @Test
    void providerErrorGivesNoForecastAndIsNotCached() throws IOException {
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withServerError());
        server.expect(once(), requestTo(Matchers.startsWith("https://api.open-meteo.com/v1/forecast")))
                .andRespond(withSuccess(fixture(), MediaType.APPLICATION_JSON));

        assertThat(client.forecast(Destination.DANANG, List.of(TODAY)).isEmpty()).isTrue();
        assertThat(client.forecast(Destination.DANANG, List.of(TODAY)).isEmpty()).isFalse();
        server.verify();
    }

    @Test
    void rainRuleFollowsThePlan() {
        assertThat(OpenMeteoClient.isRainy(61, 10)).isTrue();
        assertThat(OpenMeteoClient.isRainy(2, 60)).isTrue();
        assertThat(OpenMeteoClient.isRainy(2, 59)).isFalse();
        assertThat(OpenMeteoClient.isRainy(71, 0)).isFalse(); // snow is not rain
        assertThat(OpenMeteoClient.isRainy(null, null)).isFalse();
    }
}
