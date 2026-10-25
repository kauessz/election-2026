package br.com.election.projection;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.JsonNode;
@Repository
public class ProjectionSnapshotStore {
 private final JdbcTemplate jdbc;private final JsonMapper json;
 public ProjectionSnapshotStore(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
 public enum Origin { LIVE, REPLAY, LEGACY_UNKNOWN }
 public record Publication(UUID snapshotId,Origin origin,Instant publishedAt,boolean inputManifestAvailable){}
 public record Inputs(UUID snapshotId,String modelVersion,String officialGenerationKey,Origin origin,Instant publishedAt,ProjectionInputManifest inputManifest){}
 private record Stored(ProjectionResponse projection,Publication publication){}
 @Transactional public boolean save(ProjectionResponse r){
  return insert(r,Origin.LEGACY_UNKNOWN,null,null);
 }
 @Transactional public boolean saveLive(ProjectionResponse r,ProjectionInputManifest manifest,Instant publishedAt){
  if(manifest==null||publishedAt==null||!manifest.asOf().equals(r.calculatedAt()))throw new IllegalArgumentException("Publicação exige manifesto do mesmo cálculo e horário explícito.");
  return insert(r,Origin.LIVE,manifest,publishedAt);
 }
 private boolean insert(ProjectionResponse r,Origin origin,ProjectionInputManifest manifest,Instant publishedAt){
  if(!r.enabled()||!r.publishable()||r.officialGeneratedAt()==null||r.nationalElectorateProgress()==null)return false;
  return jdbc.update("INSERT INTO projection_snapshots(id,model_version,calculated_at,official_generated_at,national_electorate_progress,official_generation_key,projection,origin,published_at,input_manifest) VALUES(?,?,?,?,?,?,CAST(? AS jsonb),?,?,CAST(? AS jsonb)) ON CONFLICT DO NOTHING",UUID.randomUUID(),r.model(),Timestamp.from(r.calculatedAt()),Timestamp.from(r.officialGeneratedAt()),r.nationalElectorateProgress(),r.officialGenerationKey(),json.writeValueAsString(r),origin.name(),publishedAt==null?null:Timestamp.from(publishedAt),manifest==null?null:json.writeValueAsString(manifest))>0;
 }
 public List<ProjectionResponse> history(Instant from,Instant to,int limit){
  return rows(from,to,limit,null,null).stream().map(Stored::projection).toList();
 }
 public List<JsonNode> historyView(Instant from,Instant to,int limit,String modelVersion,Origin origin){
  return rows(from,to,limit,modelVersion,origin).stream().map(r->{
   var payload=(tools.jackson.databind.node.ObjectNode)json.valueToTree(r.projection());
   payload.set("publication",json.valueToTree(r.publication()));return (JsonNode)payload;
  }).toList();
 }
 public Inputs inputs(UUID id){
  return jdbc.query("SELECT id,model_version,official_generation_key,origin,published_at,input_manifest FROM projection_snapshots WHERE id=?",(rs,n)->new Inputs(
   rs.getObject("id",UUID.class),rs.getString("model_version"),rs.getString("official_generation_key"),Origin.valueOf(rs.getString("origin")),
   rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),
   rs.getString("input_manifest")==null?null:json.readValue(rs.getString("input_manifest"),ProjectionInputManifest.class)),id).stream().findFirst()
   .orElseThrow(()->new br.com.election.common.NotFoundException("Snapshot de projeção não encontrado."));
 }
 private List<Stored> rows(Instant from,Instant to,int limit,String modelVersion,Origin origin){
  if(limit<1||limit>2000||from.isAfter(to))throw new IllegalArgumentException("Intervalo inválido; limit deve estar entre 1 e 2000");
  if(modelVersion!=null&&(modelVersion.isBlank()||modelVersion.length()>50))throw new IllegalArgumentException("Versão do modelo inválida.");
  var result=jdbc.query("""
   SELECT id,projection,origin,published_at,input_manifest IS NOT NULL AS has_manifest FROM projection_snapshots
   WHERE calculated_at>=? AND calculated_at<=? AND (CAST(? AS varchar) IS NULL OR model_version=?)
   AND ((CAST(? AS varchar) IS NULL AND origin<>'REPLAY') OR origin=?)
   ORDER BY calculated_at DESC,id DESC LIMIT ?
   """,(rs,n)->new Stored(json.readValue(rs.getString("projection"),ProjectionResponse.class),new Publication(rs.getObject("id",UUID.class),Origin.valueOf(rs.getString("origin")),
     rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant(),rs.getBoolean("has_manifest"))),
   Timestamp.from(from),Timestamp.from(to),modelVersion,modelVersion,origin==null?null:origin.name(),origin==null?null:origin.name(),limit);
  Collections.reverse(result);return result;
 }
}
