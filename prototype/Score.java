import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic half of the triage design: pass/fail checks, weighted scoring, ranking.
 *
 * The AI step (fact extraction) is described in PROMPTS.md. This program starts from the
 * extracted facts, so the same facts and the same criteria always give the same ranking.
 *
 * Run (JDK 17 or newer, no dependencies):  java Score.java
 */
public class Score {

    // ---- criteria (an admin would edit these; here they are fixed for the sample category) ----
    static final int BUDGET_CEILING_CAD = 260_000;
    static final int MAX_WEEKS = 16;
    static final int EXPERIENCE_FULL_MARKS_AT = 14;
    static final int WORKMANSHIP_FULL_MARKS_AT_YEARS = 12;
    static final Map<String, Integer> WEIGHTS = new LinkedHashMap<>();
    static {
        WEIGHTS.put("experience", 30);
        WEIGHTS.put("price", 25);
        WEIGHTS.put("timeline", 15);
        WEIGHTS.put("warranty", 15);
        WEIGHTS.put("team", 15);
    }

    /** Facts a verified extraction step would produce. Entered by hand for this prototype. */
    record Facts(String id, boolean licenceHeld, boolean wsibAndInsurance, int installs100kw5y,
                 int priceCad, Integer weeks, int workmanshipYears,
                 boolean namedLead, boolean crew, boolean safetyPlan, boolean monitoring,
                 boolean injectionAttempt) {
    }

    static final List<Facts> PROPOSALS = List.of(
            new Facts("A", true, true, 14, 238_000, 12, 10, true, true, true, true, false),
            new Facts("B", false, true, 6, 189_500, 10, 5, false, false, false, false, false),
            new Facts("C", true, true, 9, 244_000, 14, 10, true, true, false, false, true),
            new Facts("D", true, true, 11, 231_000, null, 10, true, false, true, false, false),
            new Facts("E", true, true, 8, 242_500, 13, 12, true, true, true, true, false));

    record Result(String id, Integer rank, String status, Double score, String reason) {
    }

    static List<String> failures(Facts f) {
        List<String> out = new ArrayList<>();
        if (!f.licenceHeld()) out.add("licence not held by bidder");
        if (!f.wsibAndInsurance()) out.add("WSIB/insurance not confirmed");
        if (f.priceCad() > BUDGET_CEILING_CAD) out.add("over budget");
        if (f.weeks() != null && f.weeks() > MAX_WEEKS) out.add("over schedule limit");
        return out;
    }

    static Map<String, Double> criterionScores(Facts f, int bestPrice, int bestWeeks) {
        Map<String, Double> s = new LinkedHashMap<>();
        s.put("experience", Math.min((double) f.installs100kw5y() / EXPERIENCE_FULL_MARKS_AT, 1) * 100);
        s.put("price", (double) bestPrice / f.priceCad() * 100);
        s.put("timeline", f.weeks() == null ? 0.0 : (double) bestWeeks / f.weeks() * 100);
        s.put("warranty", Math.min((double) f.workmanshipYears() / WORKMANSHIP_FULL_MARKS_AT_YEARS, 1) * 100);
        int teamItems = (f.namedLead() ? 1 : 0) + (f.crew() ? 1 : 0) + (f.safetyPlan() ? 1 : 0) + (f.monitoring() ? 1 : 0);
        s.put("team", teamItems / 4.0 * 100);
        return s;
    }

    static double total(Map<String, Double> scores) {
        double t = 0;
        for (var e : WEIGHTS.entrySet()) t += scores.get(e.getKey()) * e.getValue() / 100;
        return Math.round(t * 10) / 10.0;
    }

    public static void main(String[] args) {
        if (WEIGHTS.values().stream().mapToInt(Integer::intValue).sum() != 100) {
            throw new IllegalStateException("weights must add up to 100");
        }
        // price and timeline are relative to the best eligible bid; the rest use fixed caps
        List<Facts> eligible = PROPOSALS.stream().filter(f -> failures(f).isEmpty()).toList();
        int bestPrice = eligible.stream().mapToInt(Facts::priceCad).min().orElseThrow();
        int bestWeeks = eligible.stream().filter(f -> f.weeks() != null).mapToInt(Facts::weeks).min().orElseThrow();

        record Scored(Facts f, Map<String, Double> parts, double total) {
        }
        List<Scored> ranked = new ArrayList<>();
        List<Result> others = new ArrayList<>();
        Map<String, Double> shadow = new LinkedHashMap<>();

        for (Facts f : PROPOSALS) {
            List<String> failed = failures(f);
            if (!failed.isEmpty()) {
                others.add(new Result(f.id(), null, "ineligible", null, "Fails check: " + String.join(", ", failed) + "."));
                continue;
            }
            Map<String, Double> parts = criterionScores(f, bestPrice, bestWeeks);
            if (f.injectionAttempt()) {
                shadow.put(f.id(), total(parts)); // for the reviewer only, never used for ranking
                others.add(new Result(f.id(), null, "flagged", null,
                        "Contains text aimed at the review system asking for a top rank; held for a human, not auto-ranked."));
                continue;
            }
            ranked.add(new Scored(f, parts, total(parts)));
        }
        ranked.sort(Comparator.comparingDouble(Scored::total).reversed().thenComparing(s -> s.f().id()));

        List<Result> out = new ArrayList<>();
        int rank = 1;
        for (Scored s : ranked) {
            String best = WEIGHTS.keySet().stream()
                    .max(Comparator.comparingDouble(k -> s.parts().get(k) * WEIGHTS.get(k))).orElseThrow();
            String reason = "Total " + s.total() + "; strongest on " + best
                    + (s.f().weeks() == null ? "; no completion time stated, timeline scored 0" : "") + ".";
            out.add(new Result(s.f().id(), rank++, "ranked", s.total(), reason));
        }
        out.addAll(others);

        for (Result r : out) {
            System.out.println(json(r));
        }
        shadow.forEach((id, sc) -> System.out.println("shadow score for " + id + " (reviewer only): " + sc));
    }

    static String json(Result r) {
        return String.format(Locale.ROOT,
                "{ \"proposal\": \"%s\", \"rank\": %s, \"status\": \"%s\", \"score\": %s, \"reason\": \"%s\" }",
                r.id(), r.rank() == null ? "null" : r.rank(), r.status(), r.score() == null ? "null" : r.score(), r.reason());
    }
}
