package br.com.election.pollhistory;

import br.com.election.common.HttpUrl;
import br.com.election.poll.VoteBasis;
import br.com.election.pollster.PollsterResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class HistoricalPollModels {
    private HistoricalPollModels() {}
    public enum Normalization { NONE, VALID_VOTES }
    public record CandidateInput(
        @NotBlank @Size(max=100) @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String candidateKey,
        @NotBlank @Size(max=200) String candidateName,
        @Size(max=200) String displayName, @Size(max=50) String party,
        @Min(1) @Max(99) Integer ballotNumber,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=4) BigDecimal originalPercentage
    ) {}
    public record Request(
        @NotBlank @Size(max=100) @Pattern(regexp="[A-Z0-9]+(-[A-Z0-9]+)*") String electionKey,
        @NotNull @Min(1) @Max(2) Integer round,
        @NotBlank @Size(max=200) String pollsterSlug,
        @Size(max=100) String registrationNumber,
        @NotNull LocalDate fieldStart, @NotNull LocalDate fieldEnd, @NotNull Instant publishedAt,
        @Positive Integer sampleSize,
        @DecimalMin(value="0", inclusive=false) @DecimalMax("100") @Digits(integer=3, fraction=4) BigDecimal marginOfError,
        @Size(max=5000) String methodology, @NotBlank @HttpUrl @Size(max=2000) String sourceUrl,
        @NotNull VoteBasis voteBasis, Normalization normalization, Boolean completeCandidateSet,
        @Size(max=50) List<@NotBlank @Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") @Size(max=100) String> expectedCandidateKeys,
        @Size(max=5000) String notes,
        @NotNull @Size(min=1, max=50) List<@NotNull @Valid CandidateInput> candidateResults
    ) {}
    public record Result(String candidateKey, String candidateName, String displayName, String party,
        Integer ballotNumber, BigDecimal originalPercentage, BigDecimal normalizedPercentage) {}
    public record Prepared(Request request, List<Result> results) {}
    public record Response(UUID id, String electionKey, int round, PollsterResponse pollster,
        String registrationNumber, LocalDate fieldStart, LocalDate fieldEnd, Instant publishedAt,
        Integer sampleSize, BigDecimal marginOfError, String methodology, String sourceUrl, VoteBasis voteBasis,
        Normalization normalization, boolean completeCandidateSet, List<String> expectedCandidateKeys,
        String notes, Instant createdAt, Instant updatedAt, List<Result> results) {}
}
