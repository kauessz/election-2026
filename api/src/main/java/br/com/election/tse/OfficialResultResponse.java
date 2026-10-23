package br.com.election.tse;
import java.time.Instant;
public record OfficialResultResponse(String source, boolean official, OfficialDataStatus status,
 Instant lastSuccessfulUpdate, boolean refreshing, OfficialElectionResult.Scope scope, OfficialElectionResult result) {}
