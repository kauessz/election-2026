package br.com.election.readiness;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
@Service @Transactional(readOnly=true)
public class DataQualityService {
 public record Issue(String severity,String code,String entityId,String message){}
 public record Report(String status,boolean ready,Instant checkedAt,List<Issue> issues){}
 private final JdbcTemplate jdbc;private final Clock clock;
 public DataQualityService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 private void check(List<Issue> issues,String severity,String code,String message,String query,Object... args){jdbc.query(query,rs->{issues.add(new Issue(severity,code,rs.getString(1),message));},args);}
 public Report check(){List<Issue> issues=new ArrayList<>();
  check(issues,"ERROR","DUPLICATE_SLUG","Slug de instituto duplicado.","SELECT slug FROM pollsters GROUP BY slug HAVING count(*)>1");
  check(issues,"ERROR","MISSING_RESULTS","Pesquisa sem resultados.","SELECT p.id FROM polls p LEFT JOIN poll_results r ON r.poll_id=p.id GROUP BY p.id HAVING count(r.id)=0");
  check(issues,"ERROR","VALID_SUM","Soma VALID fora de 100 ±0,1 ponto percentual.","SELECT p.id FROM polls p JOIN poll_results r ON r.poll_id=p.id WHERE p.vote_basis='VALID' GROUP BY p.id HAVING abs(sum(r.original_percentage)-100)>0.1");
  check(issues,"ERROR","TOTAL_ZERO","Soma TOTAL dos candidatos é zero.","SELECT p.id FROM polls p JOIN poll_results r ON r.poll_id=p.id WHERE p.vote_basis='TOTAL' GROUP BY p.id HAVING sum(r.original_percentage)=0");
  check(issues,"ERROR","MISSING_SOURCE","Pesquisa sem fonte.","SELECT id FROM polls WHERE source_url IS NULL OR btrim(source_url)=''");
  check(issues,"ERROR","DUPLICATE_REGISTRATION","Registro TSE duplicado para o mesmo instituto.","SELECT pollster_id::text||':'||btrim(tse_registration_number) FROM polls WHERE nullif(btrim(tse_registration_number),'') IS NOT NULL GROUP BY pollster_id,btrim(tse_registration_number) HAVING count(*)>1");
  check(issues,"ERROR","DUPLICATE_FALLBACK_KEY","Pesquisa sem registro com chave temporal duplicada.","SELECT pollster_id::text||':'||field_end||':'||published_at FROM polls WHERE nullif(btrim(tse_registration_number),'') IS NULL GROUP BY pollster_id,field_end,published_at HAVING count(*)>1");
  check(issues,"ERROR","PERFORMANCE_SOURCE","Performance sem fonte auditável.","SELECT id FROM pollster_performance WHERE source_url IS NULL OR btrim(source_url)=''");
  check(issues,"WARNING","PERFORMANCE_UNVERIFIED","Performance legada ainda sem verifiedAt; revisar fonte antes do freeze.","SELECT id FROM pollster_performance WHERE verified_at IS NULL");
  check(issues,"ERROR","UNKNOWN_CANDIDATE","Resultado referencia candidato desconhecido.","SELECT r.id FROM poll_results r LEFT JOIN candidates c ON c.id=r.candidate_id WHERE c.id IS NULL");
  check(issues,"ERROR","AMBIGUOUS_BALLOT","Número de candidato ambíguo.","SELECT ballot_number::text FROM candidates GROUP BY ballot_number HAVING count(*)>1");
  check(issues,"ERROR","FUTURE_POLL","Pesquisa com publicação ou campo futuro.","SELECT id FROM polls WHERE published_at>? OR field_end>?",Timestamp.from(clock.instant()),LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo"))));
  check(issues,"ERROR","FIELD_ORDER","Fim de campo anterior ao início.","SELECT id FROM polls WHERE field_end<field_start");
  check(issues,"ERROR","ORPHAN_SNAPSHOT","Snapshot de agregado referencia pesquisa inexistente.","SELECT DISTINCT s.id FROM aggregate_snapshots s CROSS JOIN LATERAL jsonb_array_elements(coalesce(s.payload->'contributions','[]'::jsonb)) c LEFT JOIN polls p ON p.id::text=c->>'pollId' WHERE p.id IS NULL");
  if(jdbc.queryForObject("SELECT count(*) FROM polls",Long.class)==0)issues.add(new Issue("WARNING","NO_POLLS",null,"Nenhuma pesquisa cadastrada; freeze exige agregados calculáveis."));
  boolean ready=issues.stream().noneMatch(i->i.severity().equals("ERROR"));String status=!ready?"ERRORS":issues.isEmpty()?"READY":"WARNINGS";
  return new Report(status,ready,clock.instant(),List.copyOf(issues));
 }
}
