package dev.travelpilot.trip;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.error.GlobalExceptionHandler;
import dev.travelpilot.planner.PlannerFixtures;
import dev.travelpilot.planner.PlannerOrchestrator;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@WebMvcTest(TripController.class)
@Import({TripService.class, TripRequestValidator.class, GlobalExceptionHandler.class, TripControllerTest.Config.class})
class TripControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        /** 2026-10-30 in Vietnam. */
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-30T03:00:00Z"), Destination.ZONE);
        }

        /** Runs callbacks without a database. */
        @Bean
        TransactionTemplate transactionTemplate() {
            return new TransactionTemplate(new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {}

                @Override
                protected void doCommit(DefaultTransactionStatus status) {}

                @Override
                protected void doRollback(DefaultTransactionStatus status) {}
            });
        }
    }

    static final String FAMILY_JSON = """
            {"destination":"DANANG","startDate":"2026-11-12","days":3,"adults":2,"childrenAges":[6],
             "budgetVnd":6000000,"preferences":["BEACH","THEME-PARK"],"pace":"RELAXED","notes":[]}""";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PlannerOrchestrator orchestrator;

    @MockitoBean
    TripRepository repository;

    @Test
    void createReturns201WithLocationAndThePlan() throws Exception {
        when(orchestrator.plan(any(TripRequest.class))).thenReturn(TripFixtures.familyResult());

        mvc.perform(post("/api/v1/trips").header("X-Guest-Id", "guest-1")
                        .contentType(MediaType.APPLICATION_JSON).content(FAMILY_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/trips/")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].items[0].start").value("08:30"))
                .andExpect(jsonPath("$.budget.totalVnd").value(5_064_000))
                .andExpect(jsonPath("$.meta.promptVersion").value("planner-v1"));

        verify(repository).insertTrip(any(UUID.class), eq("guest-1"), any(TripRequest.class), eq(1));
        verify(repository).insertVersion(any(UUID.class), eq(1), any(TripPlan.class), any(TripRequest.class), any());
    }

    @Test
    void invalidRequestIs400WithFieldErrorsAndNoPlanning() throws Exception {
        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(FAMILY_JSON.replace("\"days\":3", "\"days\":6")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.days").exists());

        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(FAMILY_JSON.replace("2026-11-12", "2026-10-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startDate").exists());

        mvc.perform(post("/api/v1/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(FAMILY_JSON.replace("\"BEACH\"", "\"ZOO\"")))
                .andExpect(status().isBadRequest());

        verify(orchestrator, never()).plan(any(TripRequest.class));
    }

    @Test
    void ownerCanReadOthersGet404() throws Exception {
        UUID id = UUID.randomUUID();
        TripPlan plan = TripFixtures.familyResult().plan().withIdentity(id, 1);
        when(repository.findTrip(id)).thenReturn(Optional.of(
                new TripRepository.TripRow(id, "guest-1", PlannerFixtures.family(), 1, OffsetDateTime.now())));
        when(repository.findVersion(id, 1)).thenReturn(Optional.of(plan));

        mvc.perform(get("/api/v1/trips/" + id).header("X-Guest-Id", "guest-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(id.toString()));
        mvc.perform(get("/api/v1/trips/" + id + "/versions/1").header("X-Guest-Id", "guest-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips/" + id).header("X-Guest-Id", "someone-else"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/trips/" + id + "/versions/9").header("X-Guest-Id", "guest-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownTripAndBadIdsAre404Or400() throws Exception {
        when(repository.findTrip(any())).thenReturn(Optional.empty());
        when(repository.findVersion(any(), anyInt())).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/trips/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/trips/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/" + UUID.randomUUID()).header("X-Guest-Id", "bad id!"))
                .andExpect(status().isBadRequest());
    }
}
