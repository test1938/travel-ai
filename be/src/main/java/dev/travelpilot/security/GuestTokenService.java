package dev.travelpilot.security;

import dev.travelpilot.common.config.TravelPilotProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guest tokens are HS256 JWTs ({@code sub} = random guest id, {@code iat}, {@code exp}). Only our exact HS256
 * header is accepted ("alg": "none" or any other algorithm is rejected) and signatures are compared in constant
 * time. Hand-written on purpose: three claims do not justify a JWT library.
 */
public class GuestTokenService {

    public static final int MIN_SECRET_LENGTH = 32;
    static final int MAX_TOKEN_LENGTH = 1024;
    private static final Logger log = LoggerFactory.getLogger(GuestTokenService.class);
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    static final String HEADER = B64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    /** A freshly issued token. */
    public record IssuedToken(String token, String guestId, Instant expiresAt) {}

    private final SecretKeySpec key;
    private final Duration lifetime;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();

    public GuestTokenService(TravelPilotProperties.Security cfg, Clock clock) {
        this.key = new SecretKeySpec(secret(cfg.guestTokenSecret()), ALGORITHM);
        this.lifetime = Duration.ofDays(cfg.guestTokenDays());
        this.clock = clock;
    }

    private static byte[] secret(String configured) {
        if (configured == null || configured.isBlank()) {
            log.warn("GUEST_TOKEN_SECRET is not set: using a random key, guest tokens will not survive a restart");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        }
        if (configured.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "GUEST_TOKEN_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        return configured.getBytes(StandardCharsets.UTF_8);
    }

    public IssuedToken issueNew() {
        return issue(UUID.randomUUID().toString());
    }

    /** Also used to renew a still-valid token for the same guest. */
    public IssuedToken issue(String guestId) {
        Instant now = clock.instant();
        Instant exp = now.plus(lifetime);
        String payload = B64.encodeToString(json.writeValueAsBytes(
                Map.of("sub", guestId, "iat", now.getEpochSecond(), "exp", exp.getEpochSecond())));
        String unsigned = HEADER + "." + payload;
        return new IssuedToken(unsigned + "." + B64.encodeToString(sign(unsigned)), guestId, exp);
    }

    /** The guest id of a valid, unexpired token signed with our key; empty for anything else. */
    public Optional<String> verify(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !parts[0].equals(HEADER)) {
            return Optional.empty();
        }
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected, B64D.decode(parts[2]))) {
                return Optional.empty();
            }
            Map<?, ?> claims = json.readValue(B64D.decode(parts[1]), Map.class);
            if (!(claims.get("sub") instanceof String id) || id.isBlank()
                    || !(claims.get("exp") instanceof Number exp)
                    || clock.instant().getEpochSecond() >= exp.longValue()) {
                return Optional.empty();
            }
            return Optional.of(id);
        } catch (RuntimeException e) {
            return Optional.empty();   // bad base64 or JSON
        }
    }

    /** Short stable hash of a guest id for logs and rate-limit keys in logs (never log the id itself). */
    public static String hash(String guestId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(guestId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
