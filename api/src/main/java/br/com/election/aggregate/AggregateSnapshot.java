package br.com.election.aggregate;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "aggregate_snapshots")
public class AggregateSnapshot {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private AggregateMethod method;
    @Column(nullable=false) private Instant calculatedAt;
    @Column(nullable=false) private Instant snapshotMinute;
    @Column(nullable=false) private LocalDate referenceDate;
    @Column(nullable=false) private Integer pollCount;
    @Column(nullable=false) private Integer pollsterCount;
    @Column(nullable=false) private Long totalSampleSize;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false, columnDefinition="jsonb") private String payload;

    protected AggregateSnapshot() {}
    public UUID getId() { return id; }
    public AggregateMethod getMethod() { return method; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public LocalDate getReferenceDate() { return referenceDate; }
    public Integer getPollCount() { return pollCount; }
    public Integer getPollsterCount() { return pollsterCount; }
    public Long getTotalSampleSize() { return totalSampleSize; }
    public String getPayload() { return payload; }
}
