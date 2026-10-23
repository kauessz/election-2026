package br.com.election.aggregate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AggregateSnapshotService {
    private final AggregateService aggregates;
    private final AggregateSnapshotRepository repository;
    private final JsonMapper json;

    public AggregateSnapshotService(AggregateService aggregates, AggregateSnapshotRepository repository, JsonMapper json) {
        this.aggregates = aggregates; this.repository = repository; this.json = json;
    }

    @Transactional
    public List<SnapshotResponse> create(LocalDate referenceDate) {
        return create(referenceDate, null);
    }

    @Transactional
    public List<SnapshotResponse> create(LocalDate referenceDate, AggregateMethod method) {
        List<AggregateResponse> calculated = method == null
            ? aggregates.all(referenceDate) : List.of(aggregates.one(method, referenceDate));
        if (calculated.getFirst().pollCount() == 0) throw new IllegalArgumentException("Não há pesquisas elegíveis para criar snapshots.");
        List<SnapshotResponse> saved = new ArrayList<>();
        for (AggregateResponse aggregate : calculated) {
            Instant minute = aggregate.calculatedAt().truncatedTo(ChronoUnit.MINUTES);
            repository.insertIfAbsent(UUID.randomUUID(), aggregate.method().name(), aggregate.calculatedAt(), minute,
                aggregate.referenceDate(), aggregate.pollCount(), aggregate.pollsterCount(), aggregate.totalSampleSize(), json.writeValueAsString(aggregate));
            saved.add(response(repository.findByMethodAndReferenceDateAndSnapshotMinute(aggregate.method(), referenceDate, minute).orElseThrow()));
        }
        return List.copyOf(saved);
    }

    @Transactional(readOnly=true)
    public List<SnapshotResponse> history(AggregateMethod method) {
        return (method == null ? repository.findAllByOrderByCalculatedAtAscIdAsc() : repository.findByMethodOrderByCalculatedAtAscIdAsc(method))
            .stream().map(this::response).toList();
    }

    private SnapshotResponse response(AggregateSnapshot snapshot) {
        AggregateResponse payload = json.readValue(snapshot.getPayload(), AggregateResponse.class);
        return new SnapshotResponse(snapshot.getId(), snapshot.getMethod(), snapshot.getCalculatedAt(), snapshot.getReferenceDate(),
            snapshot.getPollCount(), snapshot.getPollsterCount(), snapshot.getTotalSampleSize(), payload.results(), payload.spread());
    }
}
