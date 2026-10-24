package br.com.election.pollhistory;

import br.com.election.poll.PollNormalizationService;
import br.com.election.poll.VoteBasis;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import static br.com.election.pollhistory.HistoricalPollModels.*;

@Service
public class HistoricalPollValidation {
    private final Validator validator;
    private final PollNormalizationService math;
    public HistoricalPollValidation(Validator validator, PollNormalizationService math) {
        this.validator = validator; this.math = math;
    }
    static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public Prepared prepare(Request raw) {
        if (raw == null) throw new IllegalArgumentException("Registro histórico obrigatório.");
        var violations = validator.validate(raw);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.stream()
            .map(v -> v.getPropertyPath()+": "+v.getMessage()).sorted().collect(Collectors.joining("; ")));
        var inputs = raw.candidateResults().stream().map(c -> new CandidateInput(c.candidateKey(), c.candidateName().trim(),
            optional(c.displayName()), optional(c.party()), c.ballotNumber(), c.originalPercentage().setScale(4)))
            .sorted(Comparator.comparing(CandidateInput::candidateKey)).toList();
        var expected = raw.expectedCandidateKeys() == null ? List.<String>of() : raw.expectedCandidateKeys().stream().sorted().toList();
        var mode = raw.normalization() == null ? Normalization.NONE : raw.normalization();
        var r = new Request(raw.electionKey(), raw.round(), raw.pollsterSlug().trim(), optional(raw.registrationNumber()),
            raw.fieldStart(), raw.fieldEnd(), raw.publishedAt().truncatedTo(ChronoUnit.MICROS), raw.sampleSize(),
            raw.marginOfError() == null ? null : raw.marginOfError().setScale(4), optional(raw.methodology()), raw.sourceUrl().trim(),
            raw.voteBasis(), mode, Boolean.TRUE.equals(raw.completeCandidateSet()), expected, optional(raw.notes()), inputs);
        if (r.fieldEnd().isBefore(r.fieldStart())) throw new IllegalArgumentException("Fim do campo anterior ao início.");
        if (r.fieldEnd().isAfter(r.publishedAt().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()))
            throw new IllegalArgumentException("Publicação anterior ao fim do campo.");
        Set<String> keys = new HashSet<>(); Set<Integer> ballots = new HashSet<>();
        for (var c : inputs) {
            if (!keys.add(c.candidateKey())) throw new IllegalArgumentException("candidateKey repetida.");
            if (c.ballotNumber() != null && !ballots.add(c.ballotNumber())) throw new IllegalArgumentException("ballotNumber repetido.");
        }
        BigDecimal sum = inputs.stream().map(CandidateInput::originalPercentage).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() <= 0 || sum.compareTo(new BigDecimal(r.voteBasis() == VoteBasis.TOTAL ? "100" : "100.5")) > 0)
            throw new IllegalArgumentException("Soma dos percentuais de candidatos inválida.");
        if (r.completeCandidateSet() || mode == Normalization.VALID_VOTES) {
            if (!r.completeCandidateSet()) throw new IllegalArgumentException("Normalização exige completeCandidateSet=true.");
            if (expected.size() != new HashSet<>(expected).size() || !keys.equals(new HashSet<>(expected)))
                throw new IllegalArgumentException("Informe expectedCandidateKeys sem duplicatas e todos os resultados desse conjunto.");
            if (keys.size() < (r.round() == 1 ? 3 : 2))
                throw new IllegalArgumentException("Conjunto insuficiente: não normalize apenas um par do primeiro turno.");
            if (r.voteBasis() == VoteBasis.VALID && sum.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.5")) > 0)
                throw new IllegalArgumentException("Conjunto completo em votos válidos deve somar aproximadamente 100% (tolerância 0.5 p.p.).");
        } else if (!expected.isEmpty()) throw new IllegalArgumentException("expectedCandidateKeys exige declaração de conjunto completo.");
        // Reuse only the existing pure arithmetic, after the archive-specific completeness gate.
        // No PollService, active candidates, eligibility, weights, performance or aggregate writes.
        List<BigDecimal> normalized = mode == Normalization.VALID_VOTES
            ? math.normalize(r.voteBasis(), inputs.stream().map(CandidateInput::originalPercentage).toList()) : null;
        List<Result> results = new ArrayList<>();
        for (int i=0; i<inputs.size(); i++) {
            var c = inputs.get(i);
            results.add(new Result(c.candidateKey(), c.candidateName(), c.displayName(), c.party(), c.ballotNumber(),
                c.originalPercentage(), normalized == null ? null : normalized.get(i)));
        }
        return new Prepared(r, List.copyOf(results));
    }
}
