package br.com.election.projection;
import br.com.election.tse.*;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
@Repository
public class AnalyticsInputStore {
 public record Recorded(UUID id,OfficialElectionResult result,Instant capturedAt) {}
 private final JdbcTemplate jdbc;private final JsonMapper json;private final TseResultsProperties p;
 public AnalyticsInputStore(JdbcTemplate jdbc,JsonMapper json,TseResultsProperties p){this.jdbc=jdbc;this.json=json;this.p=p;}
 public List<Recorded> latestAt(Instant at){return jdbc.query("""
  SELECT DISTINCT ON(scope_type,scope_code) id,payload,captured_at FROM election_result_snapshots
  WHERE election_id=? AND election_round=2 AND scope_type IN('NATIONAL','STATE') AND source_generated_at<=? AND captured_at<=?
  ORDER BY scope_type,scope_code,source_generated_at DESC,captured_at DESC,id DESC
  """,(rs,n)->new Recorded(rs.getObject(1,UUID.class),json.readValue(rs.getString(2),OfficialElectionResult.class),rs.getTimestamp(3).toInstant()),p.electionId(),Timestamp.from(at),Timestamp.from(at));}
 public List<Recorded> trendAt(Instant at){
  Instant start=at.minusSeconds(30*60);
  return jdbc.query("""
   WITH eligible AS (SELECT id,payload,captured_at,source_generated_at FROM election_result_snapshots
    WHERE election_id=? AND election_round=2 AND scope_type='NATIONAL' AND scope_code='BR' AND captured_at<=? AND source_generated_at<=?),
   chosen AS (SELECT * FROM eligible WHERE source_generated_at>=? UNION ALL
    (SELECT * FROM eligible WHERE source_generated_at<? ORDER BY source_generated_at DESC,captured_at DESC,id DESC LIMIT 1))
   SELECT id,payload,captured_at FROM chosen ORDER BY source_generated_at,captured_at,id
   """,(rs,n)->new Recorded(rs.getObject(1,UUID.class),json.readValue(rs.getString(2),OfficialElectionResult.class),rs.getTimestamp(3).toInstant()),p.electionId(),Timestamp.from(at),Timestamp.from(at),Timestamp.from(start),Timestamp.from(start));
 }
}
