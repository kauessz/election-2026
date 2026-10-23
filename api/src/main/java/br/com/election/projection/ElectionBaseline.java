package br.com.election.projection;
import br.com.election.tse.ElectionScopeType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="election_baselines")
public class ElectionBaseline {
 @Id UUID id;
 String electionId;
 @Column(name="election_round") int round;
 @Enumerated(EnumType.STRING) ElectionScopeType scopeType;
 String scopeCode;
 Long electorate,totalVotes,validVotes,blankVotes,nullVotes;
 @Column(precision=38,scale=24) BigDecimal validVotesPerElector;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") String candidateResults;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") String payload;
 Instant sourceGeneratedAt,capturedAt;
 String sourceUrl;
 protected ElectionBaseline() {}
}
