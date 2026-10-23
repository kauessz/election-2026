package br.com.election.pollster;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PollsterPerformanceResponse(UUID id, PollsterResponse pollster, String electionKey, Integer round,
    BigDecimal candidateError, BigDecimal spreadError, BigDecimal historicalScore,
    String sourceUrl, String sourceDescription, Instant updatedAt) {}
