package dev.travelpilot.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.security.GuestTokenService;
import dev.travelpilot.support.Guests;
import dev.travelpilot.knowledge.Source;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(MessageController.class)
@Import(GlobalExceptionHandler.class)
class MessageControllerTest {

    static final UUID TRIP = ConversationFixtures.TRIP_ID;

    @Autowired
    MockMvc mvc;

    @Autowired
    GuestTokenService tokens;

    private RequestPostProcessor as(String guestId) {
        return Guests.as(tokens, guestId);
    }

    @MockitoBean
    ChatService chat;

    @ParameterizedTest
    @EnumSource(Intent.class)
    void returnsTheReplyForEachIntent(Intent intent) throws Exception {
        boolean modify = intent == Intent.MODIFY_PLAN;
        when(chat.handle(eq(TRIP), eq("guest-1"), any())).thenReturn(new ChatService.ChatReply("trả lời", intent,
                intent == Intent.ASK_QUESTION ? List.of(new Source("ev-1", "d", "Bà Nà", null, "ODbL", "s")) : List.of(),
                modify ? 2 : null, modify ? ConversationFixtures.familyPlan().withIdentity(TRIP, 2) : null,
                modify ? "Ngày 2 được lập lại" : null));

        var result = mvc.perform(post("/api/v1/trips/" + TRIP + "/messages").with(as("guest-1"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  xin chào  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("trả lời"))
                .andExpect(jsonPath("$.intent").value(intent.name()));
        if (modify) {
            result.andExpect(jsonPath("$.newVersion").value(2))
                    .andExpect(jsonPath("$.plan.version").value(2))
                    .andExpect(jsonPath("$.changeSummary").value("Ngày 2 được lập lại"));
        }
        if (intent == Intent.ASK_QUESTION) {
            result.andExpect(jsonPath("$.sources[0].id").value("ev-1"));
        }
        verify(chat).handle(TRIP, "guest-1", "xin chào");
    }

    @Test
    void controlAndInvisibleCharactersAreRemovedBeforeTheService() throws Exception {
        mvc.perform(post("/api/v1/trips/" + TRIP + "/messages").with(as("guest-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Ngày 2\\u200b mưa\\u0007\\u202e\"}"))
                .andExpect(status().isOk());
        verify(chat).handle(TRIP, "guest-1", "Ngày 2 mưa");
    }

    @Test
    void blankOrTooLongMessageIs400() throws Exception {
        mvc.perform(post("/api/v1/trips/" + TRIP + "/messages").with(as("guest-1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.message").exists());
        mvc.perform(post("/api/v1/trips/" + TRIP + "/messages").with(as("guest-1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LONG"));
        verify(chat, never()).handle(any(), any(), any());
    }

    @Test
    void historyIsReturned() throws Exception {
        when(chat.history(TRIP, "guest-1")).thenReturn(List.of(
                new ChatMessage(1, ChatMessage.Role.USER, "Ngày 2 mưa", null, null, OffsetDateTime.now()),
                new ChatMessage(2, ChatMessage.Role.ASSISTANT, "Đã cập nhật", Intent.MODIFY_PLAN, 2,
                        OffsetDateTime.now())));
        mvc.perform(get("/api/v1/trips/" + TRIP + "/messages").with(as("guest-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].intent").value("MODIFY_PLAN"))
                .andExpect(jsonPath("$[1].tripVersion").value(2));
    }
}
