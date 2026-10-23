package br.com.election.projection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
@Repository
public class BaselineStore {
 private final JdbcTemplate jdbc;private final JsonMapper json;
 public BaselineStore(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
 @Transactional public void upsert(BaselineData b){
  int changed=jdbc.update("""
   INSERT INTO election_baselines(id,election_id,election_round,scope_type,scope_code,electorate,total_votes,valid_votes,blank_votes,null_votes,valid_votes_per_elector,candidate_results,payload,source_generated_at,captured_at,source_url)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?)
   ON CONFLICT(election_id,election_round,scope_type,scope_code) DO UPDATE SET
   electorate=excluded.electorate,total_votes=excluded.total_votes,valid_votes=excluded.valid_votes,blank_votes=excluded.blank_votes,null_votes=excluded.null_votes,valid_votes_per_elector=excluded.valid_votes_per_elector,candidate_results=excluded.candidate_results,payload=excluded.payload,source_generated_at=excluded.source_generated_at,captured_at=excluded.captured_at,source_url=excluded.source_url
   WHERE election_baselines.source_generated_at<=excluded.source_generated_at
   """,UUID.randomUUID(),b.electionId(),b.round(),b.scope().type().name(),b.scope().code(),b.electorate(),b.totalVotes(),b.validVotes(),b.blankVotes(),b.nullVotes(),b.validVotesPerElector(),json.writeValueAsString(b.officialResult().candidates()),json.writeValueAsString(b),Timestamp.from(b.sourceGeneratedAt()),Timestamp.from(b.capturedAt()),b.sourceUrl());
  if(changed==0)throw new IllegalArgumentException("Baseline recebido é anterior ao armazenado");
 }
 public List<BaselineData> at(Instant asOf){return jdbc.query("SELECT payload FROM election_baselines WHERE election_id=? AND election_round=1 AND source_generated_at<=? AND captured_at<=? ORDER BY scope_type,scope_code",(rs,n)->json.readValue(rs.getString(1),BaselineData.class),BaselineData.FIRST_ROUND_ELECTION,Timestamp.from(asOf),Timestamp.from(asOf));}
}
