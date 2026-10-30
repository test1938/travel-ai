package dev.travelpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.travelpilot.common.config.TravelPilotProperties;
import dev.travelpilot.support.TestProps;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GuestTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T03:00:00Z");
    private static final TravelPilotProperties.Security CFG = TestProps.defaults().security();

    private static GuestTokenService at(Instant now) {
        return new GuestTokenService(CFG, Clock.fixed(now, ZoneOffset.UTC));
    }

    private final GuestTokenService tokens = at(NOW);

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void roundTripGivesTheGuestBack() {
        GuestTokenService.IssuedToken t = tokens.issueNew();
        assertThat(t.token().split("\\.")).hasSize(3);
        assertThat(t.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(tokens.verify(t.token())).contains(t.guestId());
        assertThat(tokens.issueNew().guestId()).isNotEqualTo(t.guestId());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = tokens.issue("g1").token();
        assertThat(at(NOW.plus(Duration.ofDays(7)).minusSeconds(1)).verify(token)).contains("g1");
        assertThat(at(NOW.plus(Duration.ofDays(7))).verify(token)).isEmpty();
    }

    @Test
    void tamperedPayloadIsRejected() {
        String[] parts = tokens.issue("g1").token().split("\\.");
        String otherGuest = b64("{\"sub\":\"g2\",\"iat\":1,\"exp\":9999999999}");
        assertThat(tokens.verify(parts[0] + "." + otherGuest + "." + parts[2])).isEmpty();
    }

    @Test
    void algNoneAndOtherKeysAreRejected() {
        String payload = b64("{\"sub\":\"g1\",\"iat\":1,\"exp\":9999999999}");
        assertThat(tokens.verify(b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + payload + ".")).isEmpty();

        GuestTokenService otherKey = new GuestTokenService(new TravelPilotProperties.Security(
                "another-secret-another-secret-another-1", 7, 32768, CFG.rateLimit()), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(tokens.verify(otherKey.issue("g1").token())).isEmpty();
    }

    @Test
    void garbageIsRejectedWithoutExceptions() {
        assertThat(tokens.verify(null)).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify("a.b.c")).isEmpty();
        assertThat(tokens.verify(GuestTokenService.HEADER + ".!!!.???")).isEmpty();
        assertThat(tokens.verify("x".repeat(5000))).isEmpty();
    }

    @Test
    void shortSecretStopsStartupBlankSecretIsRandom() {
        assertThatThrownBy(() -> new GuestTokenService(
                new TravelPilotProperties.Security("short", 7, 32768, CFG.rateLimit()), Clock.systemUTC()))
                .hasMessageContaining("at least 32");
        GuestTokenService random1 = new GuestTokenService(
                new TravelPilotProperties.Security("", 7, 32768, CFG.rateLimit()), Clock.systemUTC());
        GuestTokenService random2 = new GuestTokenService(
                new TravelPilotProperties.Security(null, 7, 32768, CFG.rateLimit()), Clock.systemUTC());
        assertThat(random2.verify(random1.issue("g1").token())).isEmpty();
    }

    @Test
    void hashIsStableAndShort() {
        assertThat(GuestTokenService.hash("g1")).hasSize(12).isEqualTo(GuestTokenService.hash("g1"));
    }
}
