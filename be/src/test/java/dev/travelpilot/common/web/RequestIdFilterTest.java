package dev.travelpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.travelpilot.common.ai.AiCall;
import dev.travelpilot.common.ai.LlmGateway;
import dev.travelpilot.support.TestProps;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** T7.02: request id in MDC during the request, in the response header, and on the model-call thread. */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String run(String incoming, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        if (incoming != null) {
            request.addHeader(RequestIdFilter.HEADER, incoming);
        }
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });
        return seen.get();
    }

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String id = run(null, response);

        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(id);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();   // cleared after the request
    }

    @Test
    void keepsASafeIncomingId() throws Exception {
        assertThat(run("trace-1234.abc_DEF", new MockHttpServletResponse())).isEqualTo("trace-1234.abc_DEF");
    }

    @Test
    void replacesIdsThatCouldForgeLogLines() {
        for (String bad : List.of("short", "a b c d e f g h", "x".repeat(65), "evil\nINFO fake line",
                "id\"},\"level\":\"ERROR")) {
            assertThat(RequestIdFilter.idFor(bad)).isNotEqualTo(bad);
        }
    }

    @Test
    void modelCallThreadSeesTheRequestId() {
        LlmGateway gateway = new LlmGateway(Executors.newVirtualThreadPerTaskExecutor(), TestProps.defaults());
        AtomicReference<String> onModelThread = new AtomicReference<>();
        MDC.put(RequestIdFilter.MDC_KEY, "req-thread-01");
        try {
            gateway.chat(AiCall.of("TEST", null), () -> {
                onModelThread.set(MDC.get(RequestIdFilter.MDC_KEY));
                return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
            });
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
        assertThat(onModelThread.get()).isEqualTo("req-thread-01");
    }
}
