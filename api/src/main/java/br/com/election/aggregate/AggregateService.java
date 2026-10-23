package br.com.election.aggregate;

import br.com.election.candidate.*;
import br.com.election.poll.*;
import br.com.election.pollster.PollsterPerformanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AggregateService {
    public static final ZoneId REFERENCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private final PollService polls;
    private final CandidateRepository candidates;
    private final PollsterPerformanceRepository history;
    private final HistoricalScoreService scoring;
    private final AggregateEngine engine;
    private final AggregationProperties properties;
    private final Clock clock;

    public AggregateService(PollService polls, CandidateRepository candidates, PollsterPerformanceRepository history,
        HistoricalScoreService scoring, AggregateEngine engine, AggregationProperties properties, Clock clock) {
        this.polls = polls; this.candidates = candidates; this.history = history; this.scoring = scoring;
        this.engine = engine; this.properties = properties; this.clock = clock;
    }

    public LocalDate today() { return LocalDate.now(clock.withZone(REFERENCE_ZONE)); }

    public List<AggregateResponse> all(LocalDate referenceDate) { return calculate(referenceDate, List.of(AggregateMethod.values())); }
    public AggregateResponse one(AggregateMethod method, LocalDate referenceDate) { return calculate(referenceDate, List.of(method)).getFirst(); }

    private List<AggregateResponse> calculate(LocalDate referenceDate, List<AggregateMethod> methods) {
        Objects.requireNonNull(referenceDate, "Data de referência obrigatória.");
        Instant calculatedAt = clock.instant();
        var latest = polls.latestBefore(referenceDate.plusDays(1).atStartOfDay(REFERENCE_ZONE).toInstant());
        Set<UUID> candidateIds = candidates.findAll().stream().filter(Candidate::getActive).map(Candidate::getId).collect(Collectors.toSet());
        List<PollResponse> eligible = new ArrayList<>();
        List<AggregateResponse.ExcludedPoll> excluded = new ArrayList<>();
        for (PollResponse poll : latest) {
            String reason = null;
            if (!poll.pollster().active()) reason = "Instituto inativo.";
            else if (poll.fieldEnd().isAfter(referenceDate)) reason = "Campo termina após a data de referência.";
            else if (candidateIds.size() < 2 || !poll.results().stream().map(PollResponse.Result::candidateId).collect(Collectors.toSet()).equals(candidateIds))
                reason = "Resultados não correspondem ao conjunto completo de candidatos ativos da disputa.";
            if (reason == null) eligible.add(poll);
            else excluded.add(new AggregateResponse.ExcludedPoll(poll.id(), poll.pollster().name(), reason));
        }
        Map<UUID, BigDecimal> factors = new HashMap<>();
        if (methods.contains(AggregateMethod.CALIBRATED)) {
            history.findByElectionKeyAndRound(properties.historicalElectionKey(), properties.historicalRound()).forEach(p -> {
                if (p.getSpreadError() != null) factors.put(p.getPollster().getId(), scoring.factor(p.getSpreadError()));
            });
        }
        return methods.stream().map(method -> engine.calculate(method, eligible, factors, referenceDate, calculatedAt, excluded)).toList();
    }
}
