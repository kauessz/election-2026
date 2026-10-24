package br.com.election.discovery;

import br.com.election.readiness.ImportModels.*;
import br.com.election.poll.VoteBasis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

public final class DiscoveryModels {
    private DiscoveryModels(){}
    public enum Status { DETECTED, AWAITING_RESULTS, READY_FOR_REVIEW, UNMAPPED_POLLSTER, INVALID, IMPORTED, IGNORED }
    public static class Discovery {
        public UUID id; public String registrationNumber,pollsterName,pollsterSlug;
        public LocalDate fieldStart,fieldEnd,expectedPublicationDate; public Instant publishedAt;
        public Integer sampleSize; public BigDecimal marginOfError; public String methodology;
        public Map<String,String> tseMetadata,latestTseMetadata; public String registryHash;
        public String sourceUrl; public VoteBasis voteBasis; public List<CandidateResult> candidateResults;
        public Status status; public Instant detectedAt,updatedAt,reviewedAt; public UUID importedPollId;
        public String notes,dryRunHash; public Instant dryRunAt; public Report dryRunReport;
        public Report importReport; public List<UUID> snapshotIds;
        public boolean secondRoundConfirmed,publicationAllowedConfirmed;
    }
    public record Review(@NotBlank @Size(max=200) String pollsterSlug,
        @NotNull LocalDate fieldStart,@NotNull LocalDate fieldEnd,@NotNull Instant publishedAt,
        @Positive Integer sampleSize,@Positive @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal marginOfError,
        @Size(max=5000) String methodology,
        @NotBlank @br.com.election.common.HttpUrl @Size(max=2000) String sourceUrl,@NotNull VoteBasis voteBasis,
        @NotNull @Size(min=2,max=2) List<@NotNull @Valid Result> candidateResults,
        @Size(max=5000) String notes,boolean secondRoundConfirmed,boolean publicationAllowedConfirmed){}
    public record Result(@NotNull Integer ballotNumber,@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=4) BigDecimal percentage){}
    public record Ignore(@NotBlank @Size(max=5000) String notes){}
    public record Sync(int scanned,int created,int updated,int unchanged,int eligible){}
    public record Inbox(List<Discovery> items,Map<Status,Long> counts,long detectedToday,long expectedPublicationToday){}
    public record Imported(Report report,UUID importedPollId,int snapshots){}
}
