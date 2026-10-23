package br.com.election.aggregate;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@Validated
@ConfigurationProperties("polling.aggregation")
public record AggregationProperties(
    @DefaultValue("3") @NotNull @Positive BigDecimal halfLifeDays,
    @DefaultValue("0.15") @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("1") @Digits(integer=1, fraction=8) BigDecimal maxInstituteWeight,
    @DefaultValue("0.85") @NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("1") BigDecimal historicalMinFactor,
    @DefaultValue("1.15") @NotNull @DecimalMin("1") @DecimalMax("2") BigDecimal historicalMaxFactor,
    @DefaultValue("3") @NotNull @Positive BigDecimal historicalNeutralSpreadError,
    @DefaultValue("BR-PRESIDENT-2026") @NotBlank String historicalElectionKey,
    @DefaultValue("1") @Min(1) @Max(2) int historicalRound
) {}
