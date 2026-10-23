package br.com.election.tse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.sql.Timestamp;
import java.security.MessageDigest;
import java.util.*;
@Service
public class OfficialSnapshotStore {
 private final JdbcTemplate jdbc;private final JsonMapper json; private final TseResultsProperties p;
 public record Stored(OfficialElectionResult result,Instant capturedAt) {}
 public OfficialSnapshotStore(JdbcTemplate jdbc,JsonMapper json,TseResultsProperties p){this.jdbc=jdbc;this.json=json;this.p=p;}
 // Only official content contributes to the digest; local mapping and receipt time do not.
 public String hash(OfficialElectionResult r) {
  try {
   var facts=List.of(r.finalTotalization(),r.sections(),r.electorate(),r.votes(),r.candidates().stream().map(c->new OfficialElectionResult.CandidateResult(c.candidateNumber(),c.candidateSequence(),c.name(),c.displayName(),c.votes(),c.percentage(),c.precisePercentage(),null)).toList());
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(facts)));
  }catch(Exception e){throw new IllegalStateException("Cannot fingerprint official result",e);}
 }
 @Transactional
 public boolean save(OfficialElectionResult r,Instant captured,String etag) {
  var s=r.sections();var v=r.votes();
  return jdbc.update("""
   INSERT INTO election_result_snapshots(id,election_id,election_round,scope_type,scope_code,source_generated_at,captured_at,status,sections_percentage,total_sections,totalized_sections,electorate,total_votes,valid_votes,blank_votes,null_votes,candidate_results,payload,source_etag,content_hash)
   VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?) ON CONFLICT DO NOTHING
   """,UUID.randomUUID(),r.electionId(),r.round(),r.scope().type().name(),r.scope().code(),Timestamp.from(r.generatedAt()),Timestamp.from(captured),intrinsicStatus(r).name(),s.percentageTotalized(),s.total(),s.totalized(),r.electorate().total(),v.totalVotes(),v.validVotes(),v.blankVotes(),v.nullVotes(),json.writeValueAsString(r.candidates()),json.writeValueAsString(r),etag,hash(r))>0;
 }
 public Stored latest(OfficialElectionResult.Scope scope) {
  return jdbc.query("""
   SELECT payload,captured_at FROM election_result_snapshots WHERE election_id=? AND election_round=2 AND scope_type=? AND scope_code=? ORDER BY source_generated_at DESC,captured_at DESC,id DESC LIMIT 1
   """,(rs,n)->new Stored(json.readValue(rs.getString(1),OfficialElectionResult.class),rs.getTimestamp(2).toInstant()),p.electionId(),scope.type().name(),scope.code()).stream().findFirst().orElse(null);
 }
 public List<OfficialElectionResult> history(Instant from,Instant to,int limit){
  if(limit<1||limit>2000||from.isAfter(to))throw new IllegalArgumentException("Intervalo inválido; limit deve estar entre 1 e 2000");
  var result=jdbc.query("""
   SELECT payload FROM election_result_snapshots WHERE election_id=? AND election_round=2 AND scope_type='NATIONAL' AND scope_code='BR' AND source_generated_at>=? AND source_generated_at<=? ORDER BY source_generated_at DESC,captured_at DESC,id DESC LIMIT ?
   """,(rs,n)->json.readValue(rs.getString(1),OfficialElectionResult.class),p.electionId(),Timestamp.from(from),Timestamp.from(to),limit);
  Collections.reverse(result);return result;
 }
 public static OfficialDataStatus intrinsicStatus(OfficialElectionResult r){
  if(r.finalTotalization())return OfficialDataStatus.FINAL;
  return r.sections().totalized()==0 && r.votes().totalVotes()==0?OfficialDataStatus.NOT_STARTED:OfficialDataStatus.FRESH;
 }
}
