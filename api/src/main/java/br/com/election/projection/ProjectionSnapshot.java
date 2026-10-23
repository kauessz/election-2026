package br.com.election.projection;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="projection_snapshots")
public class ProjectionSnapshot {
 @Id UUID id;
 String modelVersion;
 Instant calculatedAt,officialGeneratedAt;
 @Column(precision=38,scale=24) BigDecimal nationalElectorateProgress;
 String officialGenerationKey;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") String projection;
 protected ProjectionSnapshot() {}
}
