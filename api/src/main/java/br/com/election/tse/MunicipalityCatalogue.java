package br.com.election.tse;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import br.com.election.tse.dto.raw.Ea12;
import java.util.*;
@Service
public class MunicipalityCatalogue {
 public record Municipality(String code,String name,BrazilState uf,List<String> zones) {
  public OfficialElectionResult.Scope scope(){return new OfficialElectionResult.Scope(ElectionScopeType.MUNICIPALITY,uf.name()+":"+code,name+" / "+uf.name());}
 }
 private final JdbcTemplate jdbc;private final JsonMapper json;private final TseResultsProperties p;
 public MunicipalityCatalogue(JdbcTemplate jdbc,JsonMapper json,TseResultsProperties p){this.jdbc=jdbc;this.json=json;this.p=p;}
 @Transactional
 public void accept(String body){
  Ea12 raw=json.readValue(body,Ea12.class);List<Municipality> all=new ArrayList<>();Set<String> keys=new HashSet<>();
  if(raw.abr()==null)throw new IllegalArgumentException("EA12: abrangências ausentes");
  for(var region:raw.abr()) {
   if("zz".equalsIgnoreCase(region.cd())) continue;
   BrazilState uf=BrazilState.parse(region.cd());
   if(region.mu()==null)throw new IllegalArgumentException("EA12: municípios ausentes");
   for(var m:region.mu()) {
    if(m.cd()==null||!m.cd().matches("[0-9]{5}")||m.nm()==null||m.nm().isBlank()||!keys.add(uf+":"+m.cd()))throw new IllegalArgumentException("EA12: município inválido/duplicado");
    List<String> zones=m.z()==null?List.of():m.z();
    if(zones.stream().anyMatch(z->!z.matches("[0-9]{4}")))throw new IllegalArgumentException("EA12: zona inválida");
    all.add(new Municipality(m.cd(),m.nm(),uf,zones));
   }
  }
  if(all.stream().map(Municipality::uf).distinct().count()!=27)throw new IllegalArgumentException("EA12: catálogo incompleto (27 UFs esperadas)");
  // Whole catalogue replacement is atomic; any parse/SQL failure retains the previous version.
  jdbc.update("DELETE FROM election_municipalities WHERE election_id=?",p.electionId());
  jdbc.batchUpdate("INSERT INTO election_municipalities(election_id,uf,code,name,zones) VALUES(?,?,?,?,CAST(? AS jsonb))",all,500,(ps,m)->{ps.setString(1,p.electionId());ps.setString(2,m.uf().name());ps.setString(3,m.code());ps.setString(4,m.name());ps.setString(5,json.writeValueAsString(m.zones()));});
 }
 public List<Municipality> list(BrazilState uf){
  List<Municipality> result=jdbc.query("SELECT code,name,zones FROM election_municipalities WHERE election_id=? AND uf=? ORDER BY name,code",(rs,n)->new Municipality(rs.getString(1),rs.getString(2),uf,Arrays.asList(json.readValue(rs.getString(3),String[].class))),p.electionId(),uf.name());
  if(result.isEmpty())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo oficial de municípios ainda indisponível");
  return result;
 }
 public List<Municipality> all(){
  var result=jdbc.query("SELECT code,name,uf,zones FROM election_municipalities WHERE election_id=? ORDER BY name,uf",(rs,n)->new Municipality(rs.getString(1),rs.getString(2),BrazilState.parse(rs.getString(3)),Arrays.asList(json.readValue(rs.getString(4),String[].class))),p.electionId());
  if(result.isEmpty())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo oficial de municípios ainda indisponível");
  return result;
 }
 public Municipality get(BrazilState uf,String code){
  if(!code.matches("[0-9]{5}"))throw new IllegalArgumentException("Código municipal TSE deve conter cinco dígitos");
  return list(uf).stream().filter(m->m.code().equals(code)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Município não encontrado no EA12 desta UF"));
 }
}
