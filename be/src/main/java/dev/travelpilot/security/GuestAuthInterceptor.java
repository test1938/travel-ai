package dev.travelpilot.security;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads {@code Authorization: Bearer <guest token>}. A valid token puts the {@link Guest} on the request; a token
 * that is present but invalid or expired is 401. No token is fine here: endpoints that need a guest declare a
 * {@link Guest} parameter and {@link GuestArgumentResolver} rejects the request then.
 */
public class GuestAuthInterceptor implements HandlerInterceptor {

    static final String ATTRIBUTE = Guest.class.getName();
    private static final String BEARER = "Bearer ";

    private final GuestTokenService tokens;

    public GuestAuthInterceptor(GuestTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return true;
        }
        Optional<String> guestId = header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? tokens.verify(header.substring(BEARER.length()).strip())
                : Optional.empty();
        if (guestId.isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        request.setAttribute(ATTRIBUTE, new Guest(guestId.get()));
        return true;
    }

    /** The verified guest of this request, if any. */
    public static Optional<Guest> current(HttpServletRequest request) {
        return Optional.ofNullable((Guest) request.getAttribute(ATTRIBUTE));
    }
}
