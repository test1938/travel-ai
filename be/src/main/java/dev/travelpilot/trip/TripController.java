package dev.travelpilot.trip;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.common.web.RequestIdFilter;
import dev.travelpilot.security.Guest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Trips API. The owner is the guest of the bearer token; another guest's trip is 404 (not 403). */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private static final Logger log = LoggerFactory.getLogger(TripController.class);

    /** Longer than the planner's worst case (3 model calls with retries). */
    static final long STREAM_TIMEOUT_MS = 180_000;

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TripPlan> create(
            Guest guest,
            @Valid @RequestBody TripRequest request) {
        TripPlan plan = service.create(guest.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/trips/" + plan.tripId())).body(plan);
    }

    /**
     * Same as {@link #create} but as Server-Sent Events (T9.01): one {@code step} event per planning step as it
     * starts ({@code {"step":"llm"}}), then {@code result} with the saved plan, or {@code error} with
     * {@code {code, message, requestId}}. Invalid requests still get a normal 400 before the stream starts.
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createWithProgress(Guest guest, @Valid @RequestBody TripRequest request) {
        service.validate(request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Thread.ofVirtual().name("trip-stream").start(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            try {
                TripPlan plan = service.create(guest.id(), request,
                        step -> send(emitter, "step", Map.of("step", step)));
                send(emitter, "result", plan);
                emitter.complete();
            } catch (ApiException e) {
                sendError(emitter, e.code(), e.getMessage());
            } catch (RuntimeException e) {
                log.error("Planning stream failed", e);
                sendError(emitter, ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage());
            } finally {
                MDC.clear();
            }
        });
        return emitter;
    }

    @GetMapping("/{id}")
    public TripPlan get(@PathVariable UUID id,
            Guest guest) {
        return service.get(id, guest.id());
    }

    @GetMapping("/{id}/versions/{version}")
    public TripPlan version(@PathVariable UUID id, @PathVariable @Min(1) int version,
            Guest guest) {
        return service.getVersion(id, version, guest.id());
    }

    private static void sendError(SseEmitter emitter, ErrorCode code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code.name());
        body.put("message", message);
        body.put("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        send(emitter, "error", body);
        emitter.complete();
    }

    /** A client that went away must not stop planning: the trip is saved and can be opened later. */
    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("Could not send {} event: {}", event, e.getMessage());
        }
    }
}
