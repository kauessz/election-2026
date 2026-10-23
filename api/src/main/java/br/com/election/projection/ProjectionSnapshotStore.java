package br.com.election.projection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
@Repository
public class ProjectionSnapshotStore {
 private final JdbcTemplate jdbc;private final JsonMapper json;
 public ProjectionSnapshotStore(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
 @Transactional public boolean save(ProjectionResponse r){
  if(!r.enabled()||!r.publishable()||r.officialGeneratedAt()==null||r.nationalElectorateProgress()==null)return false;
  return jdbc.update("INSERT INTO projection_snapshots(id,model_version,calculated_at,official_generated_at,national_electorate_progress,official_generation_key,projection) VALUES(?,?,?,?,?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING",UUID.randomUUID(),r.model(),Timestamp.from(r.calculatedAt()),Timestamp.from(r.officialGeneratedAt()),r.nationalElectorateProgress(),r.officialGenerationKey(),json.writeValueAsString(r))>0;
 }
 public List<ProjectionResponse> history(Instant from,Instant to,int limit){
  if(limit<1||limit>2000||from.isAfter(to))throw new IllegalArgumentException("Intervalo inválido; limit deve estar entre 1 e 2000");
  var result=jdbc.query("SELECT projection FROM projection_snapshots WHERE model_version=? AND calculated_at>=? AND calculated_at<=? ORDER BY calculated_at DESC,id DESC LIMIT ?",(rs,n)->json.readValue(rs.getString(1),ProjectionResponse.class),ProjectionEngine.MODEL,Timestamp.from(from),Timestamp.from(to),limit);
  Collections.reverse(result);return result;
 }
}
