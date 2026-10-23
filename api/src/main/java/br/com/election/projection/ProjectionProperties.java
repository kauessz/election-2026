package br.com.election.projection;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
@ConfigurationProperties("election.projection") @Validated
public record ProjectionProperties(boolean enabled,
 @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("1") BigDecimal priorEquivalentProgress,
 @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal minimumNationalElectorateProgress,
 @Min(1) @Max(27) int minimumStatesWithData,
 @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal highCoverageProgress,
 @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal minimumObservedProgressWithoutBaseline,
 @Min(1) @Max(5) int minimumRegionsWithData,
 @Min(1) @Max(6) int minimumKeyStatesWithData) {}
