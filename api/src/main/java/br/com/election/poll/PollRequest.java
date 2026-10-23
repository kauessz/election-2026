package br.com.election.poll;
import br.com.election.common.HttpUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record PollRequest(
    @NotNull UUID pollsterId, @Size(max=100) String registrationNumber,
    @NotNull LocalDate fieldStart, @NotNull LocalDate fieldEnd, @NotNull Instant publishedAt,
    @Positive Integer sampleSize, @Positive @DecimalMax("100") @Digits(integer=3, fraction=4) BigDecimal marginOfError,
    @Size(max=5000) String methodology, @NotBlank @HttpUrl @Size(max=2000) String sourceUrl,
    @NotNull VoteBasis voteBasis, @NotEmpty @Size(max=100) List<@NotNull @Valid Result> results
) {
    public record Result(@NotNull UUID candidateId,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3, fraction=4) BigDecimal originalPercentage) {}
}
