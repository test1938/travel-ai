package dev.travelpilot.support;

import dev.travelpilot.security.GuestTokenService;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** MockMvc helper: sends a real guest token for the given guest id. */
public final class Guests {

    private Guests() {}

    public static RequestPostProcessor as(GuestTokenService tokens, String guestId) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(guestId).token());
            return request;
        };
    }
}
