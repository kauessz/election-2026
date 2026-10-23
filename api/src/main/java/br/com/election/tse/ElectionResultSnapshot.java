package br.com.election.tse;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="election_result_snapshots")
public class ElectionResultSnapshot {
 @Id UUID id;
 @Column(nullable=false) String electionId;
 @Column(name="election_round",nullable=false) int round;
 @Enumerated(EnumType.STRING) @Column(nullable=false) ElectionScopeType scopeType;
 @Column(nullable=false) String scopeCode;
 @Column(nullable=false) Instant sourceGeneratedAt;
 @Column(nullable=false) Instant capturedAt;
 @Enumerated(EnumType.STRING) @Column(nullable=false) OfficialDataStatus status;
 BigDecimal sectionsPercentage;
 Long totalSections,totalizedSections,electorate,totalVotes,validVotes,blankVotes,nullVotes;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") String candidateResults;
 @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") String payload;
 String sourceEtag;
 @Column(nullable=false) String contentHash;
 protected ElectionResultSnapshot() {}
 public Instant getCapturedAt(){return capturedAt;}
 public String getPayload(){return payload;}
}
