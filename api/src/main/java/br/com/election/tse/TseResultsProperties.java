package br.com.election.tse;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import java.time.Duration;
@ConfigurationProperties("tse.results") @Validated
public record TseResultsProperties(
 @NotBlank String baseUrl, @Pattern(regexp="oficial|simulado") String environment,
 @Min(2026) int year, @Pattern(regexp="[0-9]{1,6}") String electionId,
 @Pattern(regexp="[0-9]{4}") String presidentOfficeCode,
 @NotNull Duration pollingInterval, @NotNull Duration connectTimeout, @NotNull Duration readTimeout,
 @NotNull Duration staleThreshold, @NotNull Duration stateInterval,
 @NotNull Duration municipalityInterval, @NotNull Duration minimumRequestInterval,
 boolean schedulingEnabled) {
 public TseResultsProperties {
  for (Duration d : new Duration[]{pollingInterval,connectTimeout,readTimeout,staleThreshold,stateInterval,municipalityInterval,minimumRequestInterval})
   if(d != null && (d.isNegative() || d.isZero())) throw new IllegalArgumentException("TSE durations must be positive");
 }
}
