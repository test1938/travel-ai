package dev.travelpilot.trip;

import dev.travelpilot.common.error.ApiException;
import dev.travelpilot.common.error.ErrorCode;
import dev.travelpilot.planner.PlanProgress;
import dev.travelpilot.planner.PlannerOrchestrator;
import dev.travelpilot.planner.RepairLoop;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates trips (version 1) and serves them only to the guest who owns them (others get 404). */
@Service
public class TripService {

    private final TripRequestValidator validator;
    private final PlannerOrchestrator orchestrator;
    private final TripRepository repository;
    private final TransactionTemplate tx;

    public TripService(TripRequestValidator validator, PlannerOrchestrator orchestrator, TripRepository repository,
            TransactionTemplate tx) {
        this.validator = validator;
        this.orchestrator = orchestrator;
        this.repository = repository;
        this.tx = tx;
    }

    public TripPlan create(String guestId, TripRequest request) {
        return create(guestId, request, PlanProgress.NONE);
    }

    /** Business-rule validation only (400 VALIDATION_FAILED), e.g. before a progress stream starts. */
    public void validate(TripRequest request) {
        validator.validate(request);
    }

    public TripPlan create(String guestId, TripRequest request, PlanProgress progress) {
        validator.validate(request);
        // slow LLM call outside the transaction
        PlannerOrchestrator.Result result = orchestrator.plan(request, progress);
        UUID id = UUID.randomUUID();
        TripPlan plan = result.plan().withIdentity(id, 1);
        RepairLoop.Outcome o = result.outcome();
        TripRepository.VersionFacts facts = new TripRepository.VersionFacts(null, o.promptVersion(), o.model(),
                o.tokensIn(), o.tokensOut(), plan.meta().latencyMs(), o.repaired(), o.fallback());
        tx.executeWithoutResult(status -> {
            repository.insertTrip(id, guestId, request, 1);
            repository.insertVersion(id, 1, plan, request, facts);
        });
        return plan;
    }

    public TripPlan get(UUID tripId, String guestId) {
        TripRepository.TripRow trip = owned(tripId, guestId);
        return version(tripId, trip.currentVersion());
    }

    public TripPlan getVersion(UUID tripId, int version, String guestId) {
        owned(tripId, guestId);
        return version(tripId, version);
    }

    /** The trip header if this guest owns it; another guest's trip is reported as missing (404). */
    public TripRepository.TripRow requireOwned(UUID tripId, String guestId) {
        return owned(tripId, guestId);
    }

    /** Current version of an owned trip. */
    public TripPlan current(TripRepository.TripRow trip) {
        return version(trip.id(), trip.currentVersion());
    }

    /** A trip of another guest is reported as missing, so ids cannot be probed (plan/04-conventions.md). */
    private TripRepository.TripRow owned(UUID tripId, String guestId) {
        return repository.findTrip(tripId)
                .filter(t -> t.guestId().equals(guestId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private TripPlan version(UUID tripId, int version) {
        return repository.findVersion(tripId, version).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
