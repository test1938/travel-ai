package dev.travelpilot.conversation;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.travelpilot.common.Vnd;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.trip.TripRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A requested change to a trip, extracted by the model (change-v1) and sanitised by
 * {@link ChangeRequestExtractor}. Constraints are data, not chat: {@link #applyTo} returns a new TripRequest.
 */
public record ChangeRequest(
        @JsonPropertyDescription("false when the message contains no concrete change") boolean understood,
        @JsonPropertyDescription("DAY when about one specific day, otherwise TRIP") Scope scope,
        @JsonPropertyDescription("1-based day number when scope is DAY, otherwise null") Integer dayIndex,
        @JsonPropertyDescription("New total budget in VND, or null") Long newBudgetVnd,
        @JsonPropertyDescription("Preferences to add") List<PlaceTag> addPreferences,
        @JsonPropertyDescription("Preferences to remove") List<PlaceTag> removePreferences,
        @JsonPropertyDescription("true for indoor activities or rain, otherwise null") Boolean preferIndoor,
        @JsonPropertyDescription("Place ids from the current plan to remove") List<String> excludePlaceIds,
        @JsonPropertyDescription("One short Vietnamese sentence restating the change") String instruction) {

    public enum Scope { DAY, TRIP }

    public ChangeRequest {
        addPreferences = addPreferences == null ? List.of() : List.copyOf(addPreferences);
        removePreferences = removePreferences == null ? List.of() : List.copyOf(removePreferences);
        excludePlaceIds = excludePlaceIds == null ? List.of() : List.copyOf(excludePlaceIds);
        scope = scope == null ? Scope.TRIP : scope;
    }

    public boolean isDayScoped() {
        return scope == Scope.DAY && dayIndex != null;
    }

    public boolean wantsIndoor() {
        return Boolean.TRUE.equals(preferIndoor);
    }

    /** True when there is at least one concrete change to apply. */
    public boolean hasChanges() {
        return newBudgetVnd != null || !addPreferences.isEmpty() || !removePreferences.isEmpty() || wantsIndoor()
                || !excludePlaceIds.isEmpty() || isDayScoped();
    }

    /** New request with this change applied; destination, dates and people never change here. */
    public TripRequest applyTo(TripRequest req) {
        Set<PlaceTag> prefs = new LinkedHashSet<>(req.preferences());
        prefs.removeAll(removePreferences);
        for (PlaceTag p : addPreferences) {
            if (prefs.size() < 6) {
                prefs.add(p);
            }
        }
        long budget = newBudgetVnd == null ? req.budgetVnd() : newBudgetVnd;
        return new TripRequest(req.destination(), req.startDate(), req.days(), req.adults(), req.childrenAges(),
                budget, prefs, req.pace(), req.notes());
    }

    /** Deterministic one-line summary stored with the new version (never model text alone). */
    public String summary(TripRequest before, List<String> excludedNames) {
        List<String> parts = new ArrayList<>();
        if (isDayScoped()) {
            parts.add("Ngày " + dayIndex + " được lập lại" + (wantsIndoor() ? " với hoạt động trong nhà" : ""));
        } else if (wantsIndoor()) {
            parts.add("Ưu tiên hoạt động trong nhà");
        }
        if (newBudgetVnd != null && newBudgetVnd != before.budgetVnd()) {
            parts.add("Ngân sách " + Vnd.format(before.budgetVnd()) + " → " + Vnd.format(newBudgetVnd));
        }
        if (!excludedNames.isEmpty()) {
            parts.add("Bỏ " + String.join(", ", excludedNames));
        }
        if (!addPreferences.isEmpty()) {
            parts.add("Thêm sở thích " + addPreferences.stream().map(PlaceTag::code).toList());
        }
        if (!removePreferences.isEmpty()) {
            parts.add("Bỏ sở thích " + removePreferences.stream().map(PlaceTag::code).toList());
        }
        return parts.isEmpty() ? (instruction == null ? "Cập nhật lịch" : instruction) : String.join("; ", parts);
    }
}
