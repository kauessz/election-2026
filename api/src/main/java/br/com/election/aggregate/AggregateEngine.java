package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static java.math.MathContext.DECIMAL128;

/** Pure orchestration of the four calculators; no database access or implicit current date. */
@Component
public class AggregateEngine {
    public static final int PERCENTAGE_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private final List<AggregateCalculator> calculators;
    private final WeightAllocator allocator;
    private final AggregationProperties properties;

    public AggregateEngine(List<AggregateCalculator> calculators, WeightAllocator allocator, AggregationProperties properties) {
        this.calculators = calculators; this.allocator = allocator; this.properties = properties;
    }

    public AggregateResponse calculate(AggregateMethod method, List<PollResponse> polls,
        Map<UUID, BigDecimal> historicalFactors, LocalDate referenceDate, Instant calculatedAt,
        List<AggregateResponse.ExcludedPoll> excluded) {
        boolean usesCap = method == AggregateMethod.WEIGHTED || method == AggregateMethod.CALIBRATED;
        List<String> warnings = new ArrayList<>();
        if (polls.isEmpty()) warnings.add("Nenhuma pesquisa elegível para a data de referência.");
        AggregateCalculator calculator = calculators.stream().filter(c -> c.supports(method)).findFirst().orElseThrow();
        var factors = polls.stream().map(p -> calculator.calculate(p, referenceDate,
            historicalFactors.getOrDefault(p.pollster().id(), BigDecimal.ONE))).toList();
        var allocation = allocator.allocate(factors.stream().map(AggregateCalculator.Factors::rawWeight).toList(), usesCap ? properties.maxInstituteWeight() : null);
        if (usesCap && !allocation.capFeasible()) warnings.add("Cap de influência inviável com " + polls.size()
            + " institutos: foram usados pesos iguais (1/n). São necessários pelo menos "
            + BigDecimal.ONE.divide(properties.maxInstituteWeight(), 0, RoundingMode.CEILING) + " institutos para respeitar o limite configurado.");

        Map<UUID, BigDecimal> scores = new LinkedHashMap<>();
        Map<UUID, String> names = new HashMap<>();
        List<AggregateResponse.Contribution> contributions = new ArrayList<>();
        for (int i = 0; i < polls.size(); i++) {
            PollResponse poll = polls.get(i);
            var factor = factors.get(i);
            BigDecimal weight = allocation.weights().get(i);
            for (var result : poll.results()) {
                scores.merge(result.candidateId(), result.normalizedPercentage().multiply(weight, DECIMAL128), BigDecimal::add);
                names.put(result.candidateId(), result.candidateName());
            }
            if (usesCap && "NEUTRAL".equals(factor.precisionSource())) warnings.add(poll.pollster().name() + ": sem margem e amostra; fator de precisão neutro (1).");
            if (method == AggregateMethod.CALIBRATED && !historicalFactors.containsKey(poll.pollster().id()))
                warnings.add(poll.pollster().name() + ": sem histórico para a eleição/turno configurados; fator histórico neutro (1).");
            LocalDateTime midpoint = poll.fieldStart().atStartOfDay().plusHours(ChronoUnit.DAYS.between(poll.fieldStart(), poll.fieldEnd()) * 12);
            contributions.add(new AggregateResponse.Contribution(poll.id(), poll.pollster().id(), poll.pollster().name(),
                poll.fieldStart(), poll.fieldEnd(), midpoint, poll.publishedAt(), poll.sampleSize(), poll.marginOfError(),
                factor.rawWeight(), weight, factor.recencyWeight(), factor.precisionWeight(), factor.precisionSource(),
                factor.historicalWeightFactor(), allocation.capped().contains(i), poll.results()));
        }
        List<AggregateResponse.Result> results = percentages(scores, names);
        AggregateResponse.Spread spread = spread(results);
        long sample = polls.stream().filter(p -> p.sampleSize() != null).mapToLong(PollResponse::sampleSize).sum();
        int missingSample = (int) polls.stream().filter(p -> p.sampleSize() == null).count();
        if (missingSample > 0) warnings.add("A amostra total soma apenas os tamanhos informados; " + missingSample + " pesquisa(s) sem amostra.");
        if (!excluded.isEmpty()) warnings.add(excluded.size() + " pesquisa(s) mais recente(s) excluída(s); consulte excludedPolls.");
        return new AggregateResponse(method, calculatedAt, referenceDate, polls.size(), polls.size(), sample, missingSample,
            results, spread, List.copyOf(contributions), usesCap ? new AggregateResponse.Cap(properties.maxInstituteWeight(), allocation.effectiveCap(), allocation.capFeasible()) : null,
            properties, List.copyOf(warnings), List.copyOf(excluded));
    }

    private List<AggregateResponse.Result> percentages(Map<UUID, BigDecimal> scores, Map<UUID, String> names) {
        if (scores.isEmpty()) return List.of();
        List<UUID> ids = scores.keySet().stream().sorted(Comparator.comparing(UUID::toString)).toList();
        BigDecimal total = scores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new IllegalArgumentException("A soma dos resultados normalizados deve ser positiva.");
        Map<UUID, BigDecimal> percentages = new LinkedHashMap<>();
        for (UUID id : ids) percentages.put(id, scores.get(id).multiply(HUNDRED).divide(total, PERCENTAGE_SCALE, RoundingMode.HALF_UP));
        BigDecimal residue = HUNDRED.subtract(percentages.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        // Highest score receives only the final rounding residue; UUID resolves equal scores.
        UUID largest = ids.stream().max(Comparator.<UUID, BigDecimal>comparing(scores::get).thenComparing(UUID::toString, Comparator.reverseOrder())).orElseThrow();
        percentages.put(largest, percentages.get(largest).add(residue));
        return ids.stream().map(id -> new AggregateResponse.Result(id, names.get(id), percentages.get(id))).toList();
    }

    private AggregateResponse.Spread spread(List<AggregateResponse.Result> results) {
        if (results.size() < 2) return null;
        var ranked = results.stream().sorted(Comparator.comparing(AggregateResponse.Result::percentage).reversed()
            .thenComparing(r -> r.candidateId().toString())).toList();
        var leader = ranked.getFirst();
        BigDecimal points = leader.percentage().subtract(ranked.get(1).percentage());
        return new AggregateResponse.Spread(points.signum() == 0 ? null : leader.candidateId(), points.signum() == 0 ? null : leader.candidateName(), points);
    }
}
