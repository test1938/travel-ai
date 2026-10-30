package dev.travelpilot.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.conversation.Intent;
import dev.travelpilot.conversation.MessageController;
import dev.travelpilot.support.Guests;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** T6.02 through the real MVC stack: small limits, the next request is 429 and never reaches the service. */
@WebMvcTest({MessageController.class, GuestController.class})
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "travelpilot.security.rate-limit.messages-per-hour=3",
        "travelpilot.security.rate-limit.guests-per-hour-per-ip=2"})
class RateLimitWebTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GuestTokenService tokens;

    @MockitoBean
    ChatService chat;

    @Test
    void fourthMessageInAnHourIs429WithRetryAfter() throws Exception {
        when(chat.handle(any(), any(), any()))
                .thenReturn(new ChatService.ChatReply("ok", Intent.SMALL_TALK, List.of(), null, null, null));
        UUID trip = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/trips/" + trip + "/messages").with(Guests.as(tokens, "spammer"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/v1/trips/" + trip + "/messages").with(Guests.as(tokens, "spammer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        verify(chat, times(3)).handle(any(), any(), any());

        // Another guest is not affected.
        mvc.perform(post("/api/v1/trips/" + trip + "/messages").with(Guests.as(tokens, "someone-else"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void mintingGuestTokensIsLimitedPerIp() throws Exception {
        mvc.perform(post("/api/v1/guest").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; }))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/guest").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; }))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/guest").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; }))
                .andExpect(status().isTooManyRequests());
    }
}
