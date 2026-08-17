package edu.illinois.cs.dt.tools.minimizer.ranking;

import edu.illinois.cs.testrunner.data.results.Result;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

// Prints the full per-order, per-candidate calculation trace for a real .dtfixingtools
// fixture, for all 5 HeuristicTypes, and asserts the trace's totals equal the real
// RankFOScorer's totals -- proof the printed trace is a faithful window into what
// production code actually computes (not a diverging shadow implementation).
//
// This is the artifact requested by Wing/Suzsana: "print every calculation stage" for
// one known example, for all heuristics, so the math itself (not just the final ranking)
// can be checked against the paper.
public class RankFOScoreTraceTest {

    private static final Path DT_DIR = Paths.get(
        "/media/iit/01DAF7B03B5CE760/UIUC++/testProjects/http-request/lib/.dtfixingtools"
    );
    private static final String TARGET =
        "com.github.kevinsawicki.http.HttpRequestTest.getUrlEncodedWithPercent";
    // Ground truth: VP_kevinsawicki_http-request.csv, isVictimPolluterPair=1 for this victim.
    private static final String KNOWN_POLLUTER =
        "com.github.kevinsawicki.http.HttpRequestTest.customConnectionFactory";
    private static final int MAX_ORDERS = 20;
    private static final double DELTA = 1e-9;

    @Test
    public void trace_all_five_heuristics_and_verify_against_real_scorer() throws Exception {
        Assume.assumeTrue(
            "Skip: real http-request fixture not present at " + DT_DIR,
            DT_DIR.toFile().exists()
        );

        List<TestOrderRecord> orderings = DetectionResultsLoader.load(DT_DIR, TARGET, MAX_ORDERS);
        assertFalse("expected the real fixture to contain orderings for " + TARGET,
            orderings.isEmpty());

        for (HeuristicType hType : HeuristicType.values()) {
            System.out.println();
            System.out.println("================ " + hType + " ================");

            List<ScoredCandidate> actual =
                new RankFOScorer(hType, MAX_ORDERS).score(TARGET, orderings, OdType.VICTIM_POLLUTER);

            Map<String, Double> tracedPolluter = new HashMap<>();
            Map<String, Double> tracedNonPolluter = new HashMap<>();
            traceAndPrint(hType, orderings, tracedPolluter, tracedNonPolluter);

            assertFalse(hType + ": real scorer produced no candidates", actual.isEmpty());
            for (ScoredCandidate sc : actual) {
                assertEquals(hType + " polluterScore mismatch for " + sc.getTestName(),
                    sc.getPolluterScore(),
                    tracedPolluter.getOrDefault(sc.getTestName(), 0.0), DELTA);
                assertEquals(hType + " nonPolluterScore mismatch for " + sc.getTestName(),
                    sc.getNonPolluterScore(),
                    tracedNonPolluter.getOrDefault(sc.getTestName(), 0.0), DELTA);
            }
        }
    }

    // Independently walks the same orderings, using the same production RankingHeuristic
    // objects (not reimplementations of their formulas), and prints every intermediate
    // stage: order index, subOrder, relevant y/n, per-candidate count/idx/dist/delta,
    // running cumulative rank, and the resulting polluter/nonPolluter delta for that order.
    private void traceAndPrint(
            HeuristicType hType,
            List<TestOrderRecord> orderings,
            Map<String, Double> polluterScoreOut,
            Map<String, Double> nonPolluterScoreOut) {

        RankingHeuristic heuristic = RankingHeuristic.of(hType);
        int limit = Math.min(MAX_ORDERS, orderings.size());

        Set<String> candidates = new LinkedHashSet<>();
        for (int i = 0; i < limit; i++) {
            candidates.addAll(orderings.get(i).testsBeforeTarget(TARGET));
        }

        Map<String, Double> currentRank = new HashMap<>();
        for (String c : candidates) {
            currentRank.put(c, 0.0);
            polluterScoreOut.put(c, 0.0);
            nonPolluterScoreOut.put(c, 0.0);
        }

        // Focused, per-order trace for the KNOWN ground-truth polluter specifically, so its
        // position relative to the victim -- and its exact contribution to the score every
        // single order -- can be read off directly, per Suzsana's request ("for each order,
        // identify where the known polluter appears relative to the victim").
        List<String> polluterTraceRows = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            TestOrderRecord rec = orderings.get(i);
            Result victimResult = rec.getResult(TARGET);
            if (victimResult == null || victimResult == Result.SKIPPED) {
                polluterTraceRows.add(String.format(
                    "  order=%-2d result=%-8s (target not run this order -- skipped)", i, victimResult));
                continue;
            }

            Map<String, Double> previousRank = new HashMap<>(currentRank);
            List<String> subOrder = rec.testsBeforeTarget(TARGET);
            boolean relevant = victimResult == Result.FAILURE || victimResult == Result.ERROR;
            int count = subOrder.size();

            System.out.printf("[order %2d] result=%-8s relevant=%-5s subOrderSize=%d%n",
                i, victimResult, relevant, count);

            // Only print a bounded excerpt (first 3 and last 3 candidates) so the trace
            // stays readable for orders with 100+ candidates before the target.
            for (int idx = 0; idx < count; idx++) {
                String c = subOrder.get(idx);
                if (!currentRank.containsKey(c)) continue;
                int dist = count - idx;
                double delta = heuristic.scoreDelta(relevant, count, dist);
                currentRank.put(c, currentRank.get(c) + delta);

                if (idx < 3 || idx >= count - 3) {
                    System.out.printf("    idx=%-4d candidate=%-70s dist=%-4d delta=%+.6f cumRank=%+.6f%n",
                        idx, shortName(c), dist, delta, currentRank.get(c));
                } else if (idx == 3) {
                    System.out.println("    ...");
                }

                if (c.equals(KNOWN_POLLUTER)) {
                    polluterTraceRows.add(String.format(
                        "  order=%-2d result=%-8s relevant=%-5s present=yes idx=%-4d/%-4d dist=%-4d delta=%+.6f cumRank=%+.6f",
                        i, victimResult, relevant, idx, count, dist, delta, currentRank.get(c)));
                }
            }
            if (!subOrder.contains(KNOWN_POLLUTER)) {
                polluterTraceRows.add(String.format(
                    "  order=%-2d result=%-8s relevant=%-5s present=no  (not before victim this order) cumRank=%+.6f",
                    i, victimResult, relevant, currentRank.getOrDefault(KNOWN_POLLUTER, 0.0)));
            }

            for (String c : candidates) {
                double curr = currentRank.get(c);
                double prev = previousRank.getOrDefault(c, 0.0);
                double diff = Math.abs(curr - prev);
                if (curr > prev) {
                    polluterScoreOut.put(c, polluterScoreOut.get(c) + diff);
                } else if (curr < prev) {
                    nonPolluterScoreOut.put(c, nonPolluterScoreOut.get(c) + diff);
                }
            }
        }

        System.out.println("  -- final scores (top 5 by polluterScore) --");
        polluterScoreOut.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(5)
            .forEach(e -> System.out.printf("    %-70s polluterScore=%.6f nonPolluterScore=%.6f%n",
                shortName(e.getKey()), e.getValue(), nonPolluterScoreOut.get(e.getKey())));

        System.out.println("  -- ground-truth polluter (" + shortName(KNOWN_POLLUTER)
            + ") trace for " + hType + " --");
        polluterTraceRows.forEach(System.out::println);
        System.out.printf("  FINAL for %s: polluterScore=%.6f nonPolluterScore=%.6f%n",
            shortName(KNOWN_POLLUTER),
            polluterScoreOut.getOrDefault(KNOWN_POLLUTER, 0.0),
            nonPolluterScoreOut.getOrDefault(KNOWN_POLLUTER, 0.0));
    }

    private static String shortName(String fqName) {
        int idx = fqName.lastIndexOf('.');
        return idx < 0 ? fqName : fqName.substring(idx + 1);
    }
}
