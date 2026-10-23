package br.com.election;

import br.com.election.aggregate.*;
import br.com.election.poll.*;
import br.com.election.pollster.PollsterResponse;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AggregateAlgorithmTests {
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.000000000001");
    private static final BigDecimal FACTOR_TOLERANCE = new BigDecimal("0.000000000001");
    private static final BigDecimal PRECISION_RELATIVE_TOLERANCE = new BigDecimal("0.005");
    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 22);
    private static final Instant CALCULATED = Instant.parse("2026-10-22T15:00:00Z");
    private static final UUID C1 = new UUID(0, 1), C2 = new UUID(0, 2);
    private final AggregationProperties properties = properties("0.15");
    private final WeightFactors factors = new WeightFactors(properties);
    private final WeightAllocator allocator = new WeightAllocator();
    private final HistoricalScoreService history = new HistoricalScoreService(properties);
    private final AggregateEngine engine = engine(properties);

    static AggregationProperties properties(String cap) {
        return new AggregationProperties(new BigDecimal("3"), new BigDecimal(cap), new BigDecimal("0.85"),
            new BigDecimal("1.15"), new BigDecimal("3"), "BR-PRESIDENT-2026", 1);
    }
    static AggregateEngine engine(AggregationProperties p) {
        WeightFactors f = new WeightFactors(p);
        return new AggregateEngine(List.of(new SimpleAggregateCalculator(), new RecencyAggregateCalculator(f),
            new WeightedAggregateCalculator(f), new CalibratedAggregateCalculator(f)), new WeightAllocator(), p);
    }
    private PollResponse poll(int id, int age, String margin, Integer sample, String first) {
        LocalDate field = REFERENCE.minusDays(age);
        return new PollResponse(new UUID(1, id), new PollsterResponse(new UUID(2, id), "Instituto " + id, "i-" + id, null, true),
            null, field, field, CALCULATED, sample, margin == null ? null : new BigDecimal(margin), null,
            "https://example.org", VoteBasis.TOTAL, List.of(
                new PollResponse.Result(C1, "A", new BigDecimal("10"), new BigDecimal(first)),
                new PollResponse.Result(C2, "B", new BigDecimal("90"), new BigDecimal("100").subtract(new BigDecimal(first)))));
    }
    private AggregateResponse calculate(AggregateMethod method, List<PollResponse> polls) {
        return engine.calculate(method, polls, Map.of(), REFERENCE, CALCULATED, List.of());
    }
    private void sumIsOne(List<BigDecimal> weights) {
        assertThat(weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isCloseTo(BigDecimal.ONE, within(WEIGHT_TOLERANCE));
    }
    private List<BigDecimal> values(String... values) { return Arrays.stream(values).map(BigDecimal::new).toList(); }

    @Test void simpleHasEqualWeightsAndUsesNormalizedPercentages() {
        var result = calculate(AggregateMethod.SIMPLE, List.of(poll(1, 0, "1", 1000, "60"), poll(2, 6, "4", 2000, "40")));
        assertThat(result.contributions()).allSatisfy(c -> assertThat(c.normalizedWeight()).isEqualByComparingTo("0.5"));
        assertThat(result.results().getFirst().percentage()).isEqualByComparingTo("50");
        assertThat(result.totalSampleSize()).isEqualTo(3000);
    }
    @Test void recencyRespectsThreeAndSixDayHalfLife() {
        assertThat(factors.recency(poll(1, 0, "2", 1000, "50"), REFERENCE)).isEqualByComparingTo("1");
        assertThat(factors.recency(poll(1, 3, "2", 1000, "50"), REFERENCE)).isCloseTo(new BigDecimal("0.5"), within(FACTOR_TOLERANCE));
        assertThat(factors.recency(poll(1, 6, "2", 1000, "50"), REFERENCE)).isCloseTo(new BigDecimal("0.25"), within(FACTOR_TOLERANCE));
    }
    @Test void recencyNormalizesWeightsAndFavorsToday() {
        var result = calculate(AggregateMethod.RECENCY, List.of(poll(1, 0, "2", 1000, "60"), poll(2, 3, "2", 1000, "40")));
        sumIsOne(result.contributions().stream().map(AggregateResponse.Contribution::normalizedWeight).toList());
        assertThat(result.contributions().getFirst().normalizedWeight()).isGreaterThan(result.contributions().getLast().normalizedWeight());
    }
    @Test void midpointIncludesHalfDays() {
        var p = poll(1, 4, "2", 1000, "50");
        var range = new PollResponse(p.id(), p.pollster(), null, REFERENCE.minusDays(4), REFERENCE.minusDays(1), p.publishedAt(), 1000, p.marginOfError(), null, p.sourceUrl(), p.voteBasis(), p.results());
        assertThat(factors.ageInDays(range, REFERENCE)).isEqualByComparingTo("2.5");
        var result = calculate(AggregateMethod.RECENCY, List.of(range));
        assertThat(result.contributions().getFirst().fieldMidpoint()).isEqualTo(LocalDateTime.of(2026, 10, 19, 12, 0));
    }
    @Test void publicationDateDoesNotDetermineRecency() {
        var first = poll(1, 0, "2", 1000, "50");
        var olderField = poll(2, 6, "2", 1000, "50");
        assertThat(first.publishedAt()).isEqualTo(olderField.publishedAt());
        assertThat(factors.recency(first, REFERENCE)).isGreaterThan(factors.recency(olderField, REFERENCE));
    }
    @Test void recencyAvoidsUnderflowForOldPolls() {
        assertThat(factors.recency(poll(1, 6000, "2", 1000, "50"), REFERENCE)).isPositive();
        var weights = allocator.allocate(List.of(factors.recency(poll(1, 6000, "2", 1000, "50"), REFERENCE),
            factors.recency(poll(2, 6003, "2", 1000, "50"), REFERENCE)), null).weights();
        assertThat(weights.getFirst()).isCloseTo(new BigDecimal("0.6666666666666667"), within(WEIGHT_TOLERANCE));
    }
    @Test void futureMidpointDoesNotIncreaseWeightAboveOne() {
        assertThat(factors.recency(poll(1, -3, "2", 1000, "50"), REFERENCE)).isEqualByComparingTo("1");
    }
    @Test void lowerMarginHasHigherPrecision() {
        assertThat(factors.precision(poll(1, 0, "1", 1000, "50"))).isEqualByComparingTo("1");
        assertThat(factors.precision(poll(2, 0, "2", 1000, "50"))).isEqualByComparingTo("0.25");
        var weighted = new WeightedAggregateCalculator(factors);
        assertThat(weighted.calculate(poll(1, 0, "1", 1000, "50"), REFERENCE, null).rawWeight())
            .isGreaterThan(weighted.calculate(poll(2, 0, "2", 1000, "50"), REFERENCE, null).rawWeight());
    }
    @Test void sampleFallbackUsesEstimatedMargin() {
        assertThat(factors.precision(poll(1, 0, null, 1600, "50")))
            .isCloseTo(new BigDecimal("1600").divide(new BigDecimal("9604"), MathContext.DECIMAL128), within(FACTOR_TOLERANCE));
    }
    @Test void sampleFallbackEstimatesExpectedMargins() {
        Map<Integer, String> expected = Map.of(1500, "2.53", 2000, "2.19", 4000, "1.55");
        expected.forEach((sample, margin) -> {
            assertThat(factors.estimatedMarginOfError(sample).setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo(margin);
            BigDecimal declared = factors.precision(poll(1, 0, margin, sample, "50"));
            BigDecimal estimated = factors.precision(poll(2, 0, null, sample, "50"));
            assertThat(estimated.divide(declared, MathContext.DECIMAL128))
                .isCloseTo(BigDecimal.ONE, within(PRECISION_RELATIVE_TOLERANCE));
        });
    }
    @Test void sampleFallbackMatchesDeclaredMarginScale() {
        // n=2401 gives estimated MOE exactly 2 p.p.; both institutes must receive equal weight.
        var input = List.of(poll(1, 0, "2", 2401, "60"), poll(2, 0, null, 2401, "40"));
        var result = engine(properties("1")).calculate(AggregateMethod.WEIGHTED, input, Map.of(), REFERENCE, CALCULATED, List.of());
        assertThat(result.contributions()).allSatisfy(c -> {
            assertThat(c.precisionWeight()).isEqualByComparingTo("0.25");
            assertThat(c.normalizedWeight()).isEqualByComparingTo("0.5");
        });
    }
    @Test void invalidSampleFallbackIsNeutral() {
        for (Integer sample : Arrays.asList(null, 0, -1)) {
            var p = poll(1, 0, null, sample, "50");
            assertThat(factors.estimatedMarginOfError(sample)).isNull();
            assertThat(factors.precision(p)).isEqualByComparingTo("1");
            assertThat(factors.precisionSource(p)).isEqualTo("NEUTRAL");
        }
    }
    @Test void sampleFallbackPrecisionScalesWithSampleSize() {
        BigDecimal first = factors.precision(poll(1, 0, null, 2000, "50"));
        BigDecimal second = factors.precision(poll(2, 0, null, 4000, "50"));
        assertThat(second).isCloseTo(first.multiply(new BigDecimal("2")), within(FACTOR_TOLERANCE));
    }
    @Test void marginTakesPrecedenceOverSample() { assertThat(factors.precision(poll(1, 0, "2", 1600, "50"))).isEqualByComparingTo("0.25"); }
    @Test void missingPrecisionUsesNeutralFactorAndWarns() {
        var result = calculate(AggregateMethod.WEIGHTED, List.of(poll(1, 0, null, null, "50")));
        assertThat(result.contributions().getFirst().precisionWeight()).isEqualByComparingTo("1");
        assertThat(result.contributions().getFirst().precisionSource()).isEqualTo("NEUTRAL");
        assertThat(result.warnings()).anyMatch(w -> w.contains("sem margem e amostra"));
        assertThat(result.pollsWithoutSampleSize()).isEqualTo(1);
    }
    @Test void capRedistributesExcessProportionally() {
        var allocation = allocator.allocate(values("100", "1", "1", "1", "1", "1", "1", "1"), new BigDecimal("0.15"));
        assertThat(allocation.weights().getFirst()).isEqualByComparingTo("0.15");
        assertThat(allocation.capped()).containsExactly(0);
        assertThat(allocation.weights()).allSatisfy(w -> assertThat(w).isLessThanOrEqualTo(new BigDecimal("0.15")));
        assertThat(allocation.weights().get(1)).isCloseTo(new BigDecimal("0.85").divide(new BigDecimal("7"), MathContext.DECIMAL128), within(WEIGHT_TOLERANCE));
        sumIsOne(allocation.weights());
    }
    @Test void capRedistributesThroughMultipleRounds() {
        var allocation = allocator.allocate(values("1000", "100", "10", "1", "1", "1", "1", "1"), new BigDecimal("0.15"));
        assertThat(allocation.capped()).contains(0, 1, 2);
        sumIsOne(allocation.weights());
        assertThat(allocation.weights()).allSatisfy(w -> assertThat(w).isLessThanOrEqualTo(new BigDecimal("0.15")));
    }
    @Test void impossibleCapUsesEqualWeightsAndDocumentsIt() {
        var result = calculate(AggregateMethod.WEIGHTED, List.of(poll(1, 0, "1", 1000, "70"), poll(2, 6, "4", 1000, "40")));
        assertThat(result.cap().feasible()).isFalse();
        assertThat(result.cap().effective()).isEqualByComparingTo("0.5");
        assertThat(result.contributions()).allSatisfy(c -> assertThat(c.normalizedWeight()).isEqualByComparingTo("0.5"));
        assertThat(result.warnings()).anyMatch(w -> w.contains("inviável"));
    }
    @Test void exactFeasibilityBoundaryWorks() {
        var allocation = allocator.allocate(values("9", "1", "1", "1"), new BigDecimal("0.25"));
        assertThat(allocation.capFeasible()).isTrue();
        assertThat(allocation.weights()).allSatisfy(w -> assertThat(w).isEqualByComparingTo("0.25"));
    }
    @Test void impossibleCapReportsEffectiveRoundedLimit() {
        var allocation = allocator.allocate(values("100", "1", "1"), new BigDecimal("0.15"));
        assertThat(allocation.capFeasible()).isFalse();
        assertThat(allocation.weights()).allSatisfy(w -> assertThat(w).isLessThanOrEqualTo(allocation.effectiveCap()));
        assertThat(allocation.weights().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
    }
    @Test void documentedEightInstituteExampleMatchesAllModels() {
        List<PollResponse> input = new ArrayList<>();
        input.add(poll(1, 0, "1", 1000, "60")); input.add(poll(2, 3, "2", 1000, "40"));
        for (int i = 3; i <= 8; i++) input.add(poll(i, 0, "2", 1000, "50"));
        Map<UUID, BigDecimal> scores = Map.of(input.get(0).pollster().id(), new BigDecimal("1.15"), input.get(1).pollster().id(), new BigDecimal("0.85"));
        Map<AggregateMethod, String> expected = Map.of(AggregateMethod.SIMPLE, "50.0000", AggregateMethod.RECENCY, "50.6667",
            AggregateMethod.WEIGHTED, "50.8462", AggregateMethod.CALIBRATED, "50.9377");
        for (var method : AggregateMethod.values()) {
            var result = engine.calculate(method, input, scores, REFERENCE, CALCULATED, List.of());
            assertThat(result.results().getFirst().percentage()).isEqualByComparingTo(expected.get(method));
            assertThat(result.totalSampleSize()).isEqualTo(8000);
        }
    }
    @Test void roundedWeightsSumExactlyOne() {
        var allocation = allocator.allocate(values("1", "1", "1"), null);
        assertThat(allocation.weights().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1");
    }
    @Test void historicalScoresRespectNeutralAndLimits() {
        assertThat(history.factor(null)).isEqualByComparingTo("1");
        assertThat(history.factor(BigDecimal.ZERO)).isEqualByComparingTo("1.15");
        assertThat(history.factor(new BigDecimal("3"))).isEqualByComparingTo("1");
        assertThat(history.factor(new BigDecimal("6"))).isEqualByComparingTo("0.85");
        assertThat(history.factor(new BigDecimal("100"))).isEqualByComparingTo("0.85");
    }
    @Test void historicalFactorChangesOnlyWeight() {
        var p = properties("1");
        var input = List.of(poll(1, 0, "2", 1000, "60"), poll(2, 0, "2", 1000, "40"));
        var result = engine(p).calculate(AggregateMethod.CALIBRATED, input, Map.of(input.getFirst().pollster().id(), new BigDecimal("1.15"), input.getLast().pollster().id(), new BigDecimal("0.85")), REFERENCE, CALCULATED, List.of());
        assertThat(result.contributions().getFirst().normalizedWeight()).isEqualByComparingTo("0.575");
        assertThat(result.contributions().getLast().normalizedWeight()).isEqualByComparingTo("0.425");
        assertThat(result.contributions().getFirst().candidateResults()).isEqualTo(input.getFirst().results());
        assertThat(result.contributions().getLast().candidateResults()).isEqualTo(input.getLast().results());
        assertThat(result.results().getFirst().percentage()).isEqualByComparingTo("51.5");
    }
    @Test void historicalFactorRemainsConservativeBetweenAnchors() {
        assertThat(history.factor(new BigDecimal("1.5"))).isEqualByComparingTo("1.075");
        assertThat(history.factor(new BigDecimal("4.5"))).isEqualByComparingTo("0.925");
    }
    @Test void historicalFactorHonorsConfiguredBounds() {
        var configured = new AggregationProperties(new BigDecimal("3"), new BigDecimal("0.15"),
            new BigDecimal("0.9"), new BigDecimal("1.1"), new BigDecimal("2"), "BR-PRESIDENT-2026", 1);
        var scorer = new HistoricalScoreService(configured);
        assertThat(scorer.factor(BigDecimal.ZERO)).isEqualByComparingTo("1.1");
        assertThat(scorer.factor(new BigDecimal("2"))).isEqualByComparingTo("1");
        assertThat(scorer.factor(new BigDecimal("4"))).isEqualByComparingTo("0.9");
        assertThat(scorer.factor(new BigDecimal("100"))).isEqualByComparingTo("0.9");
    }
    @Test void calibratedWithoutHistoryMatchesWeighted() {
        var input = List.of(poll(1, 0, "2", 1000, "60"), poll(2, 3, "1", 1000, "40"));
        assertThat(calculate(AggregateMethod.CALIBRATED, input).results()).isEqualTo(calculate(AggregateMethod.WEIGHTED, input).results());
        assertThat(calculate(AggregateMethod.CALIBRATED, input).contributions()).allSatisfy(c -> assertThat(c.historicalWeightFactor()).isEqualByComparingTo("1"));
    }
    @Test void allModelsSumExactlyHundred() {
        var input = List.of(poll(1, 0, "2", 1000, "51.0870"), poll(2, 3, "1", 1000, "48.1234"), poll(3, 6, "3", 1000, "55.5555"));
        for (var method : AggregateMethod.values()) {
            var result = calculate(method, input);
            assertThat(result.results().stream().map(AggregateResponse.Result::percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
            sumIsOne(result.contributions().stream().map(AggregateResponse.Contribution::normalizedWeight).toList());
        }
    }
    @Test void finalNormalizationHandlesValidPublicationRounding() {
        var p = poll(1, 0, "2", 1000, "50");
        var rounded = new PollResponse(p.id(), p.pollster(), null, p.fieldStart(), p.fieldEnd(), p.publishedAt(), 1000, p.marginOfError(), null, p.sourceUrl(), VoteBasis.VALID,
            List.of(new PollResponse.Result(C1, "A", new BigDecimal("50.1"), new BigDecimal("50.1")), new PollResponse.Result(C2, "B", new BigDecimal("50"), new BigDecimal("50"))));
        var result = calculate(AggregateMethod.SIMPLE, List.of(rounded));
        assertThat(result.results().getFirst().percentage()).isEqualByComparingTo("50.0500");
        assertThat(result.contributions().getFirst().candidateResults()).isEqualTo(rounded.results());
    }
    @Test void deterministicAtFixedReference() {
        var input = List.of(poll(1, 0, "2", 1000, "51.0870"), poll(2, 3, "1", 1000, "48"));
        for (var method : AggregateMethod.values()) assertThat(calculate(method, input)).isEqualTo(calculate(method, input));
    }
    @Test void emptyInputReturnsNoInventedPercentages() {
        for (var method : AggregateMethod.values()) {
            var result = calculate(method, List.of());
            assertThat(result.results()).isEmpty(); assertThat(result.spread()).isNull(); assertThat(result.pollCount()).isZero();
        }
    }
    @Test void tieHasNoInventedLeader() {
        var result = calculate(AggregateMethod.SIMPLE, List.of(poll(1, 0, "2", 1000, "50")));
        assertThat(result.spread().points()).isEqualByComparingTo("0"); assertThat(result.spread().leaderCandidateId()).isNull();
    }
}
