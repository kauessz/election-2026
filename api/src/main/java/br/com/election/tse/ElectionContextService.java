package br.com.election.tse;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;

/** Phase is historical evidence; availability is a separate, current source condition. */
@Service
public class ElectionContextService {
    public enum Phase { UNKNOWN, NOT_STARTED, COUNTING, FINISHED }
    public record Evidence(boolean nationalObserved,Instant countingFirstObservedAt,Instant finishedFirstObservedAt,Instant latestPersistedAt){}
    public record Context(Phase phase,String source,OfficialDataStatus sourceStatus,boolean dataAvailable,boolean stale,
        boolean sourceFresh,Instant evaluatedAt,Instant officialGeneratedAt,Instant lastSuccessfulUpdate,
        Instant latestPersistedAt,Instant countingFirstObservedAt,Instant finishedFirstObservedAt,List<String> warnings){}
    private final JdbcTemplate jdbc;private final TseResultsProperties properties;private final OfficialResultsService official;private final Clock clock;
    private Evidence cached;private Instant expires=Instant.MIN;
    public ElectionContextService(JdbcTemplate jdbc,TseResultsProperties properties,OfficialResultsService official,Clock clock){
        this.jdbc=jdbc;this.properties=properties;this.official=official;this.clock=clock;
    }
    // One shared read per five seconds, rather than a history scan for every home visitor.
    private synchronized Evidence evidence(Instant at){
        if(cached==null||!at.isBefore(expires)){
            cached=jdbc.queryForObject("""
                SELECT coalesce(bool_or(scope_type='NATIONAL' AND scope_code='BR'),false) AS national,
                min(captured_at) FILTER(WHERE totalized_sections>0 OR total_votes>0) AS counting,
                min(captured_at) FILTER(WHERE scope_type='NATIONAL' AND scope_code='BR' AND status='FINAL') AS finished,
                max(captured_at) AS latest FROM election_result_snapshots
                WHERE election_id=? AND election_round=2 AND scope_type IN ('NATIONAL','STATE') AND captured_at<=? AND source_generated_at<=?
                """,(rs,n)->new Evidence(rs.getBoolean("national"),instant(rs.getTimestamp("counting")),instant(rs.getTimestamp("finished")),instant(rs.getTimestamp("latest"))),
                properties.electionId(),java.sql.Timestamp.from(at),java.sql.Timestamp.from(at));
            expires=at.plusSeconds(5);
        }
        return cached;
    }
    private static Instant instant(java.sql.Timestamp value){return value==null?null:value.toInstant();}
    public Context current(){Instant at=clock.instant();return derive(evidence(at),official.getPresidentNationalResult(),at);}
    public static Context derive(Evidence evidence,OfficialResultResponse response,Instant at){
        Phase phase=evidence.finishedFirstObservedAt()!=null?Phase.FINISHED:evidence.countingFirstObservedAt()!=null?Phase.COUNTING:
            evidence.nationalObserved()?Phase.NOT_STARTED:Phase.UNKNOWN;
        boolean fresh=response.status()==OfficialDataStatus.FRESH||response.status()==OfficialDataStatus.FINAL||response.status()==OfficialDataStatus.NOT_STARTED;
        var warnings=!fresh?List.of("Fonte oficial desatualizada ou indisponível. A fase preserva a evidência persistida; consulte os horários."):List.<String>of();
        return new Context(phase,"TSE",response.status(),response.result()!=null,response.status()==OfficialDataStatus.STALE,fresh,at,
            response.result()==null?null:response.result().generatedAt(),response.lastSuccessfulUpdate(),evidence.latestPersistedAt(),
            evidence.countingFirstObservedAt(),evidence.finishedFirstObservedAt(),warnings);
    }
}
