package dev.travelpilot.security;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues anonymous guest tokens. With a still-valid token the same guest gets a renewed token (their trips stay
 * reachable); without one a new guest is created. Rate-limited per IP.
 */
@RestController
@RequestMapping("/api/v1/guest")
public class GuestController {

    public record TokenResponse(String token, Instant expiresAt) {}

    private final GuestTokenService tokens;

    public GuestController(GuestTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse issue(HttpServletRequest request) {
        GuestTokenService.IssuedToken t = GuestAuthInterceptor.current(request)
                .map(g -> tokens.issue(g.id()))
                .orElseGet(tokens::issueNew);
        return new TokenResponse(t.token(), t.expiresAt());
    }
}
