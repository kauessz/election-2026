package br.com.election.poll;
import br.com.election.pollster.PollsterResponse;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record PollResponse(UUID id, PollsterResponse pollster, String registrationNumber,
    LocalDate fieldStart, LocalDate fieldEnd, Instant publishedAt, Integer sampleSize, BigDecimal marginOfError,
    String methodology, String sourceUrl, VoteBasis voteBasis, List<Result> results) {
    public record Result(UUID candidateId, String candidateName, BigDecimal originalPercentage, BigDecimal normalizedPercentage) {}
    public static PollResponse from(Poll p, List<PollResult> results) {
        return new PollResponse(p.getId(), PollsterResponse.from(p.getPollster()), p.getTseRegistrationNumber(),
            p.getFieldStart(), p.getFieldEnd(), p.getPublishedAt(), p.getSampleSize(), p.getMarginOfError(),
            p.getMethodology(), p.getSourceUrl(), p.getVoteBasis(), results.stream().map(r -> new Result(
                r.getCandidate().getId(), r.getCandidate().getDisplayName(), r.getOriginalPercentage(), r.getNormalizedPercentage())).toList());
    }
}
