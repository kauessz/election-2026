package br.com.election.pollster;

import br.com.election.common.HttpUrl;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record PollsterPerformanceRequest(
    @NotNull UUID pollsterId, @NotBlank @Size(max=100) String electionKey, @NotNull @Min(1) @Max(2) Integer round,
    @PositiveOrZero @Digits(integer=8, fraction=4) BigDecimal candidateError,
    @NotNull @PositiveOrZero @Digits(integer=8, fraction=4) BigDecimal spreadError,
    @HttpUrl @Size(max=2000) String sourceUrl, @Size(max=2000) String sourceDescription
) {}
