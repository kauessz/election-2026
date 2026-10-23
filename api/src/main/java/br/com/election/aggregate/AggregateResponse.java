package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record AggregateResponse(
    AggregateMethod method, Instant calculatedAt, LocalDate referenceDate,
    int pollCount, int pollsterCount, long totalSampleSize, int pollsWithoutSampleSize,
    List<Result> results, Spread spread, List<Contribution> contributions,
    Cap cap, AggregationProperties configuration, List<String> warnings, List<ExcludedPoll> excludedPolls
) {
    public record Result(UUID candidateId, String candidateName, BigDecimal percentage) {}
    // A tie has zero points and no designated leader.
    public record Spread(UUID leaderCandidateId, String leaderCandidateName, BigDecimal points) {}
    public record Cap(BigDecimal requested, BigDecimal effective, boolean feasible) {}
    public record ExcludedPoll(UUID pollId, String pollsterName, String reason) {}
    public record Contribution(UUID pollId, UUID pollsterId, String pollsterName,
        LocalDate fieldStart, LocalDate fieldEnd, LocalDateTime fieldMidpoint, Instant publishedAt,
        Integer sampleSize, BigDecimal marginOfError, BigDecimal rawWeight, BigDecimal normalizedWeight,
        BigDecimal recencyWeight, BigDecimal precisionWeight, String precisionSource,
        BigDecimal historicalWeightFactor, boolean capped, List<PollResponse.Result> candidateResults) {}
}
