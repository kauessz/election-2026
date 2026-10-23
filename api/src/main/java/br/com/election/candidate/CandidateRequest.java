package br.com.election.candidate;
import jakarta.validation.constraints.*;
public record CandidateRequest(@NotBlank @Size(max=200) String name, @NotBlank @Size(max=200) String displayName,
    @NotBlank @Size(max=50) String party, @NotNull @Positive Integer ballotNumber, Boolean active) {}
