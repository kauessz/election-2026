package br.com.election.readiness;
import br.com.election.poll.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public final class ImportModels {
 private ImportModels(){}
 public record CandidateResult(Integer ballotNumber,BigDecimal percentage){}
 public record PollRow(String pollsterSlug,String tseRegistrationNumber,LocalDate fieldStart,LocalDate fieldEnd,
  Instant publishedAt,Integer sampleSize,BigDecimal marginOfError,String methodology,String sourceUrl,VoteBasis voteBasis,List<CandidateResult> candidateResults){}
 public record PerformanceRow(@NotBlank String pollsterSlug,@NotBlank @Size(max=100) String electionKey,@NotNull @Min(1) @Max(2) Integer round,
  @NotNull @PositiveOrZero @Digits(integer=8,fraction=4) BigDecimal candidateError,@NotNull @PositiveOrZero @Digits(integer=8,fraction=4) BigDecimal spreadError,
  @NotBlank @br.com.election.common.HttpUrl @Size(max=2000) String sourceUrl,@Size(max=2000) String sourceDescription,@Size(max=5000) String notes){}
 public record Detail(int index,String status,UUID id,String key,Object normalized,String message){}
 public record Report(boolean dryRun,long created,long updated,long unchanged,long failed,List<Detail> details){}
}
