package br.com.election.aggregate;

import org.springframework.data.jpa.repository.*;
import java.time.*;
import java.util.*;

public interface AggregateSnapshotRepository extends JpaRepository<AggregateSnapshot, UUID> {
    List<AggregateSnapshot> findAllByOrderByCalculatedAtAscIdAsc();
    List<AggregateSnapshot> findByMethodOrderByCalculatedAtAscIdAsc(AggregateMethod method);
    Optional<AggregateSnapshot> findByMethodAndReferenceDateAndSnapshotMinute(AggregateMethod method, LocalDate referenceDate, Instant snapshotMinute);

    @Modifying
    @Query(value = """
        INSERT INTO aggregate_snapshots(id, method, calculated_at, snapshot_minute, reference_date,
            poll_count, pollster_count, total_sample_size, payload)
        VALUES (:id, :method, :calculatedAt, :snapshotMinute, :referenceDate, :pollCount,
            :pollsterCount, :totalSampleSize, CAST(:payload AS jsonb))
        ON CONFLICT (method, reference_date, snapshot_minute) DO NOTHING
        """, nativeQuery=true)
    int insertIfAbsent(UUID id, String method, Instant calculatedAt, Instant snapshotMinute, LocalDate referenceDate,
        int pollCount, int pollsterCount, long totalSampleSize, String payload);
}
