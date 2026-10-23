package br.com.election.aggregate;

import java.time.*;
import java.util.*;

public record SnapshotResponse(UUID id, AggregateMethod method, Instant calculatedAt, LocalDate referenceDate,
    int pollCount, int pollsterCount, long totalSampleSize, List<AggregateResponse.Result> results,
    AggregateResponse.Spread spread) {}
