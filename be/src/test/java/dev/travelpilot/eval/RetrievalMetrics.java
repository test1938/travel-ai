package dev.travelpilot.eval;

import dev.travelpilot.common.domain.Destination;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** hit@k and MRR over golden cases; a case is a hit when any expected doc appears in the top k. */
public final class RetrievalMetrics {

    /** Result of one case: rank is 1-based position of the first expected doc, 0 if not retrieved. */
    public record CaseResult(GoldenCase golden, List<String> retrievedDocIds, int rank, Boolean mentionOk) {

        public static CaseResult of(GoldenCase golden, List<String> retrievedDocIds, Boolean mentionOk) {
            int rank = 0;
            for (int i = 0; i < retrievedDocIds.size(); i++) {
                if (golden.expectedDocIds().contains(retrievedDocIds.get(i))) {
                    rank = i + 1;
                    break;
                }
            }
            return new CaseResult(golden, List.copyOf(retrievedDocIds), rank, mentionOk);
        }

        public boolean hitAt(int k) {
            return rank > 0 && rank <= k;
        }

        public double reciprocalRank() {
            return rank == 0 ? 0 : 1.0 / rank;
        }
    }

    public record Summary(int cases, double hit1, double hit3, double hit5, double mrr, Double mentionRate) {}

    private RetrievalMetrics() {}

    public static Summary summarize(List<CaseResult> results) {
        int n = results.size();
        if (n == 0) {
            return new Summary(0, 0, 0, 0, 0, null);
        }
        List<Boolean> mentions = new ArrayList<>();
        results.stream().map(CaseResult::mentionOk).filter(m -> m != null).forEach(mentions::add);
        return new Summary(n,
                rate(results, 1), rate(results, 3), rate(results, 5),
                results.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0),
                mentions.isEmpty() ? null : (double) mentions.stream().filter(b -> b).count() / mentions.size());
    }

    public static Map<Destination, Summary> byDestination(List<CaseResult> results) {
        Map<Destination, Summary> out = new EnumMap<>(Destination.class);
        for (Destination d : Destination.values()) {
            List<CaseResult> subset = results.stream().filter(r -> r.golden().destination() == d).toList();
            if (!subset.isEmpty()) {
                out.put(d, summarize(subset));
            }
        }
        return out;
    }

    private static double rate(List<CaseResult> results, int k) {
        return (double) results.stream().filter(r -> r.hitAt(k)).count() / results.size();
    }
}
