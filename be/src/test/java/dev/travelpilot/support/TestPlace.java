package dev.travelpilot.support;

import dev.travelpilot.catalog.Place;
import dev.travelpilot.common.domain.BestTime;
import dev.travelpilot.common.domain.Destination;
import dev.travelpilot.common.domain.PlaceTag;
import dev.travelpilot.common.domain.PlaceType;
import dev.travelpilot.common.domain.PriceLevel;
import java.util.List;
import java.util.Map;

/** Builder for synthetic places in unit tests; defaults: Da Nang center, activity, free, 60 min, any time. */
public final class TestPlace {

    private String id = "test-place";
    private Destination destination = Destination.DANANG;
    private PlaceType type = PlaceType.ACTIVITY;
    private List<PlaceTag> tags = List.of(PlaceTag.SIGHTSEEING);
    private long cost;
    private long childCost;
    private int durationMin = 60;
    private int childMinAge;
    private boolean indoor;
    private BestTime bestTime = BestTime.ANY;
    private String openingHours = "24/7";
    private double lat = Destination.DANANG.centerLat();
    private double lon = Destination.DANANG.centerLon();
    private double popularity = 0.5;
    private boolean dayTrip;
    private List<String> estimated = List.of();

    public static TestPlace activity(String id) {
        TestPlace p = new TestPlace();
        p.id = id;
        return p;
    }

    public static TestPlace food(String id) {
        TestPlace p = activity(id);
        p.type = PlaceType.FOOD;
        p.tags = List.of(PlaceTag.FOOD, PlaceTag.LOCAL);
        p.cost = 80_000;
        p.childCost = 48_000;
        p.durationMin = 45;
        p.indoor = true;
        return p;
    }

    public TestPlace tags(PlaceTag... t) { this.tags = List.of(t); return this; }
    public TestPlace cost(long adult, long child) { this.cost = adult; this.childCost = child; return this; }
    public TestPlace duration(int min) { this.durationMin = min; return this; }
    public TestPlace childMinAge(int age) { this.childMinAge = age; return this; }
    public TestPlace indoor() { this.indoor = true; return this; }
    public TestPlace bestTime(BestTime t) { this.bestTime = t; return this; }
    public TestPlace hours(String h) { this.openingHours = h; return this; }
    public TestPlace at(double la, double lo) { this.lat = la; this.lon = lo; return this; }
    public TestPlace popularity(double p) { this.popularity = p; return this; }
    public TestPlace dayTrip() { this.dayTrip = true; return this; }
    public TestPlace destination(Destination d) { this.destination = d; this.lat = d.centerLat(); this.lon = d.centerLon(); return this; }
    public TestPlace estimatedPrice() { this.estimated = List.of("estCostVnd", "childCostVnd"); return this; }

    public Place build() {
        return new Place(id, destination, type, "Name " + id, null, "Mô tả " + id, true, null, tags, cost,
                childCost, PriceLevel.LOW, "Giá test", null, null, durationMin, childMinAge, indoor, bestTime,
                openingHours, lat, lon, popularity, dayTrip, null, estimated, List.of(), Map.of());
    }
}
