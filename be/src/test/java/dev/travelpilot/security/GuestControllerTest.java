package dev.travelpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GuestController.class)
@Import(GlobalExceptionHandler.class)
class GuestControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GuestTokenService tokens;

    private String issue(String authorization) throws Exception {
        var request = post("/api/v1/guest");
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        String body = mvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void newGuestsGetDifferentTokens() throws Exception {
        String a = issue(null);
        String b = issue(null);
        assertThat(tokens.verify(a)).isPresent();
        assertThat(tokens.verify(a)).isNotEqualTo(tokens.verify(b));
    }

    @Test
    void validTokenIsRenewedForTheSameGuest() throws Exception {
        String first = issue(null);
        String renewed = issue("Bearer " + first);
        assertThat(tokens.verify(renewed)).isEqualTo(tokens.verify(first));
    }

    @Test
    void invalidTokenIs401NotASilentNewGuest() throws Exception {
        mvc.perform(post("/api/v1/guest").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
