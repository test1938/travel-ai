package dev.travelpilot.eval;

import dev.travelpilot.common.ai.AiPricing;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * T8.04 report: be/eval/results/YYYY-MM-DD-&lt;label&gt;.md plus a CSV with one row per case. Numbers are written
 * as measured; rates are over the cases they apply to and say so.
 */
public final class EvalReport {

    public static final Path DIR = Path.of("eval", "results");

    /** Run facts for the header, so two reports can be compared. */
    public record Header(LocalDate date, String label, String mode, String model, String gitSha,
            Map<String, String> prompts, String config) {}

    /** Paths of the written files. */
    public record Written(Path markdown, Path csv) {}

    private EvalReport() {}

    public static Written write(Header header, List<CaseResult> results, AiPricing pricing) {
        String base = header.date() + "-" + header.label();
        Path md = DIR.resolve(base + ".md");
        Path csv = DIR.resolve(base + ".csv");
        try {
            Files.createDirectories(DIR);
            Files.writeString(md, markdown(header, results, pricing), StandardCharsets.UTF_8);
            Files.writeString(csv, csv(results), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Written(md, csv);
    }

    static String markdown(Header h, List<CaseResult> results, AiPricing pricing) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Eval ").append(h.label()).append(" (").append(h.date()).append(")\n\n");
        sb.append("| | |\n|---|---|\n");
        sb.append("| Mode | ").append(h.mode()).append(" |\n");
        sb.append("| Model | ").append(h.model()).append(" |\n");
        sb.append("| Git | `").append(h.gitSha()).append("` |\n");
        sb.append("| Prompts | ").append(h.prompts().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "-" + e.getValue()).collect(Collectors.joining(", "))).append(" |\n");
        sb.append("| Config | ").append(h.config()).append(" |\n\n");

        List<CaseResult> done = results.stream().filter(CaseResult::done).toList();
        List<CaseResult> plans = done.stream().filter(r -> r.schemaFirstTry() != null).toList();
        List<CaseResult> chats = done.stream().filter(r -> r.evalCase().category().chats()).toList();

        sb.append("## Summary\n\n| Metric | Value |\n|---|---|\n");
        row(sb, "Cases done / error / skipped", done.size() + " / " + count(results, CaseResult.Status.ERROR)
                + " / " + count(results, CaseResult.Status.SKIPPED) + " of " + results.size());
        row(sb, "Hard rules pass (final plan)", rate(done, CaseResult::hardPass));
        row(sb, "Expectations hit", rate(done, CaseResult::expectationsHit));
        row(sb, "Schema valid on first try (plan cases)", rate(plans, CaseResult::schemaFirstTry));
        row(sb, "Repaired (plan cases)", rate(plans, r -> r.repairs() != null && r.repairs() > 0));
        row(sb, "Fallback used (plan cases)", rate(plans, CaseResult::fallback));
        row(sb, "Plan latency p50 / p95", latency(plans));
        row(sb, "Chat latency p50 / p95", latency(chats));
        row(sb, "Tokens per plan (avg in / out)", avg(plans, r -> (int) r.tokens().in()) + " / "
                + avg(plans, r -> (int) r.tokens().out()));
        row(sb, "Estimated cost per plan", cost(plans, h.model(), pricing));
        List<CaseResult> judged = done.stream().filter(r -> r.judge() != null && r.judge().ok()).toList();
        row(sb, "Judge groundedness (avg 1-5)", judged.isEmpty() ? "n/a"
                : avg(judged, r -> r.judge().groundedness()) + " (" + judged.size() + " scored)");
        row(sb, "Judge reason quality / helpfulness (avg 1-5)", judged.isEmpty() ? "n/a"
                : avg(judged, r -> r.judge().reasonQuality()));

        sb.append("\n## By category\n\n| Category | Done | Hard pass | Expectations hit |\n|---|---|---|---|\n");
        for (EvalCase.Category c : EvalCase.Category.values()) {
            List<CaseResult> all = results.stream().filter(r -> r.evalCase().category() == c).toList();
            List<CaseResult> ok = all.stream().filter(CaseResult::done).toList();
            sb.append("| ").append(c).append(" | ").append(ok.size()).append('/').append(all.size()).append(" | ")
                    .append(rate(ok, CaseResult::hardPass)).append(" | ")
                    .append(rate(ok, CaseResult::expectationsHit)).append(" |\n");
        }

        sb.append("\n## Cases\n\n| Case | Status | Hard | Expect | ms | Tokens | Repairs | Fallback | Judge G/R "
                + "| Failed checks / error |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (CaseResult r : results) {
            sb.append("| ").append(r.evalCase().id()).append(" | ").append(r.status())
                    .append(" | ").append(r.done() ? mark(r.hardPass()) : "")
                    .append(" | ").append(r.done() ? mark(r.expectationsHit()) : "")
                    .append(" | ").append(r.done() ? r.latencyMs() : "")
                    .append(" | ").append(r.done() ? r.tokens().total() : "")
                    .append(" | ").append(Objects.toString(r.repairs(), ""))
                    .append(" | ").append(Objects.toString(r.fallback(), ""))
                    .append(" | ").append(judge(r))
                    .append(" | ").append(problems(r).replace("|", "/")).append(" |\n");
        }
        return sb.toString();
    }

    static String csv(List<CaseResult> results) {
        StringBuilder sb = new StringBuilder("id,category,status,hard_pass,expectations_hit,latency_ms,tokens_in,"
                + "tokens_out,repairs,fallback,schema_first_try,judge_groundedness,judge_reason_quality,problems\n");
        for (CaseResult r : results) {
            List<Object> cells = Arrays.asList(r.evalCase().id(), r.evalCase().category(), r.status(),
                    r.done() ? r.hardPass() : "", r.done() ? r.expectationsHit() : "", r.latencyMs(),
                    r.tokens().in(), r.tokens().out(), r.repairs(), r.fallback(), r.schemaFirstTry(),
                    r.judge() == null ? null : r.judge().groundedness(),
                    r.judge() == null ? null : r.judge().reasonQuality(), problems(r));
            sb.append(cells.stream().map(EvalReport::csvCell).collect(Collectors.joining(","))).append('\n');
        }
        return sb.toString();
    }

    /** Nearest-rank percentile of the latencies, in ms. */
    static long percentile(List<Long> values, double p) {
        List<Long> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(p / 100.0 * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private static String latency(List<CaseResult> rs) {
        if (rs.isEmpty()) {
            return "n/a";
        }
        List<Long> ms = rs.stream().map(CaseResult::latencyMs).toList();
        return percentile(ms, 50) + " ms / " + percentile(ms, 95) + " ms";
    }

    private static String cost(List<CaseResult> plans, String model, AiPricing pricing) {
        List<BigDecimal> costs = new ArrayList<>();
        for (CaseResult r : plans) {
            pricing.cost(model, (int) r.tokens().in(), (int) r.tokens().out()).ifPresent(costs::add);
        }
        if (costs.isEmpty()) {
            return "n/a (no priced model calls)";
        }
        BigDecimal sum = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return String.format(Locale.ROOT, "$%.5f (avg of %d plans)", sum.doubleValue() / costs.size(), costs.size());
    }

    private static String rate(List<CaseResult> rs, Predicate<CaseResult> ok) {
        if (rs.isEmpty()) {
            return "n/a";
        }
        long n = rs.stream().filter(ok).count();
        return String.format(Locale.ROOT, "%d/%d (%.0f%%)", n, rs.size(), 100.0 * n / rs.size());
    }

    private static String avg(List<CaseResult> rs, ToIntFunction<CaseResult> f) {
        OptionalDouble avg = rs.stream().mapToInt(f).average();
        return avg.isPresent() ? String.format(Locale.ROOT, "%.1f", avg.getAsDouble()) : "n/a";
    }

    private static long count(List<CaseResult> rs, CaseResult.Status status) {
        return rs.stream().filter(r -> r.status() == status).count();
    }

    private static String judge(CaseResult r) {
        if (r.judge() == null) {
            return "";
        }
        return r.judge().ok() ? r.judge().groundedness() + "/" + r.judge().reasonQuality() : "failed";
    }

    private static String problems(CaseResult r) {
        if (r.error() != null) {
            return r.error();
        }
        return r.failed().stream().map(c -> c.name() + (c.detail().isEmpty() ? "" : ": " + c.detail()))
                .collect(Collectors.joining("; "));
    }

    private static String mark(boolean ok) {
        return ok ? "yes" : "**no**";
    }

    private static void row(StringBuilder sb, String metric, String value) {
        sb.append("| ").append(metric).append(" | ").append(value).append(" |\n");
    }

    private static String csvCell(Object value) {
        String s = value == null ? "" : value.toString();
        return s.contains(",") || s.contains("\"") || s.contains("\n") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    /** Short git commit of the working tree, with "-dirty" when there are uncommitted changes. */
    public static String gitSha() {
        try {
            String sha = run("git", "rev-parse", "--short", "HEAD");
            String status = run("git", "status", "--porcelain");
            return sha.isBlank() ? "unknown" : sha + (status.isBlank() ? "" : "-dirty");
        } catch (IOException e) {
            return "unknown";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    private static String run(String... command) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 ? out : "";
    }
}
