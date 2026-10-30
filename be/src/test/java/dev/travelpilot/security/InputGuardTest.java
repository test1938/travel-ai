package dev.travelpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.conversation.ChatService;
import dev.travelpilot.conversation.MessageController;
import dev.travelpilot.support.Guests;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** T6.03: length limits, control/invisible characters and the 32 KB body limit. */
@WebMvcTest(MessageController.class)
@Import(GlobalExceptionHandler.class)
class InputGuardTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GuestTokenService tokens;

    @MockitoBean
    ChatService chat;

    @Test
    void cleansControlAndInvisibleCharacters() {
        assertThat(InputGuard.clean("  a\u0000b​c‮d\r\ne\tf﻿  ")).isEqualTo("abcd\ne\tf");
        assertThat(InputGuard.clean("Đà Nẵng")).isEqualTo("Đà Nẵng");
        // Decomposed "ă" + combining marks become the composed form (NFC).
        assertThat(InputGuard.clean("Nẵng")).isEqualTo("Nẵng");
        assertThat(InputGuard.clean(null)).isNull();
    }

    @Test
    void tooLongTextIsRejectedNotCut() {
        assertThat(InputGuard.clean("a".repeat(2000), InputGuard.MAX_MESSAGE, "message")).hasSize(2000);
        assertThatThrownBy(() -> InputGuard.clean("a".repeat(2001), InputGuard.MAX_MESSAGE, "message"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INPUT_TOO_LONG));
        // Invisible padding does not count.
        assertThat(InputGuard.clean("a".repeat(2000) + "​".repeat(500), InputGuard.MAX_MESSAGE, "message"))
                .hasSize(2000);
    }

    @Test
    void fiftyThousandCharacterMessageIs400InputTooLong() throws Exception {
        mvc.perform(post("/api/v1/trips/" + UUID.randomUUID() + "/messages").with(Guests.as(tokens, "g1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(50_000) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LONG"));
    }

    @Test
    void messageJustOverTheLimitButUnder32KbIs400InputTooLong() throws Exception {
        mvc.perform(post("/api/v1/trips/" + UUID.randomUUID() + "/messages").with(Guests.as(tokens, "g1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INPUT_TOO_LONG"));
    }

    @Test
    void chunkedBodyIsCountedWhileRead() throws Exception {
        BodySizeLimitFilter filter = new BodySizeLimitFilter(10);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ask") {
            @Override
            public long getContentLengthLong() {
                return -1;   // chunked: size unknown up front
            }
        };
        request.setContent("0123456789ABC".getBytes(StandardCharsets.UTF_8));
        Exception[] seen = new Exception[1];
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try {
                ((HttpServletRequest) req).getInputStream().readAllBytes();
            } catch (BodySizeLimitFilter.BodyTooLargeException e) {
                seen[0] = e;
            }
        });
        assertThat(seen[0]).isNotNull();

        MockHttpServletRequest small = new MockHttpServletRequest("POST", "/api/v1/ask");
        small.setContent("0123".getBytes(StandardCharsets.UTF_8));
        byte[][] read = new byte[1][];
        filter.doFilter(small, new MockHttpServletResponse(),
                (req, res) -> read[0] = ((HttpServletRequest) req).getInputStream().readAllBytes());
        assertThat(new ByteArrayInputStream(read[0]).readAllBytes()).hasSize(4);
    }
}
