package br.com.election.readiness;

import br.com.election.poll.*;

import br.com.election.pollster.*;

import br.com.election.candidate.*;

import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.*;

import org.springframework.transaction.support.TransactionTemplate;

import jakarta.validation.Validator;

import tools.jackson.databind.JsonNode;

import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import java.util.*;

import java.math.BigDecimal;

import static br.com.election.readiness.ImportModels.*;

@Service

public class BatchImportService {

 private final PollService polls;private final PollsterRepository pollsters;private final CandidateRepository candidates;

 private final PollsterPerformanceService performances;private final Validator validator;private final JsonMapper json;

 private final TransactionTemplate tx;private final JdbcTemplate jdbc;private final Clock clock;

 public BatchImportService(PollService polls,PollsterRepository pollsters,CandidateRepository candidates,PollsterPerformanceService performances,

  Validator validator,JsonMapper json,PlatformTransactionManager manager,JdbcTemplate jdbc,Clock clock){

  this.polls=polls;this.pollsters=pollsters;this.candidates=candidates;this.performances=performances;this.validator=validator;this.json=json;this.jdbc=jdbc;this.clock=clock;

  tx=new TransactionTemplate(manager);tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

 }

 public Report run(JsonNode body,boolean dryRun,boolean performance){

  String field=performance?"performances":"polls";JsonNode rows=body.isArray()?body:body.get(field);

  if(rows==null||!rows.isArray()||rows.size()==0||rows.size()>1000)throw new IllegalArgumentException("Informe "+field+" com 1 a 1000 registros.");

  List<Detail> details=new ArrayList<>();Map<String,Object> simulated=new HashMap<>();

  for(int i=0;i<rows.size();i++){final int index=i;final JsonNode row=rows.get(i);

   try{Detail detail=tx.execute(status->{

    // Same lock for all imports serializes logical lookup + write across concurrent files.

    jdbc.queryForObject("SELECT pg_advisory_xact_lock(20260501) IS NULL",Boolean.class);

    Detail d=performance?performance(row,index,simulated,dryRun):poll(row,index,simulated,dryRun);

    if(dryRun)status.setRollbackOnly();return d;

   });details.add(detail);

   }catch(RuntimeException e){details.add(new Detail(index,"FAILED",null,null,null,message(e)));}

  }

  return new Report(dryRun,count(details,"CREATED"),count(details,"UPDATED"),count(details,"UNCHANGED"),count(details,"FAILED"),List.copyOf(details));

 }

 private long count(List<Detail> d,String s){return d.stream().filter(v->s.equals(v.status())).count();}

 private static String message(RuntimeException e){return e instanceof org.springframework.dao.DataAccessException?"Conflito de dados; nenhuma alteração neste registro.":Objects.toString(e.getMessage(),"Registro inválido");}

 private Pollster pollster(String slug){

  if(slug==null||slug.isBlank())throw new IllegalArgumentException("pollsterSlug obrigatório.");

  var found=pollsters.findAll().stream().filter(p->slug.trim().equals(p.getSlug())).toList();

  if(found.size()!=1)throw new IllegalArgumentException("Instituto inexistente ou slug ambíguo: "+slug);return found.getFirst();

 }

 private Detail poll(JsonNode raw,int index,Map<String,Object> simulated,boolean dryRun){

  PollRow r=json.treeToValue(raw,PollRow.class);var institute=pollster(r.pollsterSlug());

  if(r.candidateResults()==null)throw new IllegalArgumentException("candidateResults obrigatório.");

  var known=candidates.findAll();List<PollRequest.Result> results=new ArrayList<>();

  for(var result:r.candidateResults()){

   if(result==null)throw new IllegalArgumentException("Candidato inválido.");

   var matches=known.stream().filter(c->Objects.equals(c.getBallotNumber(),result.ballotNumber())).toList();

   if(matches.size()!=1)throw new IllegalArgumentException("ballotNumber desconhecido ou ambíguo: "+result.ballotNumber());

   results.add(new PollRequest.Result(matches.getFirst().getId(),result.percentage()));

  }

  String reg=r.tseRegistrationNumber()==null||r.tseRegistrationNumber().isBlank()?null:r.tseRegistrationNumber().trim();

  var request=new PollRequest(institute.getId(),reg,r.fieldStart(),r.fieldEnd(),r.publishedAt(),r.sampleSize(),r.marginOfError(),r.methodology(),r.sourceUrl(),r.voteBasis(),results);

  var normalized=polls.validateAndNormalize(request);

  String key=institute.getId()+"|"+(reg!=null?"REG:"+reg:"DATE:"+r.fieldEnd()+"|"+r.publishedAt());

  var matchingIds=reg!=null
   ?jdbc.queryForList("SELECT id FROM polls WHERE pollster_id=? AND btrim(tse_registration_number)=?",UUID.class,institute.getId(),reg)
   :jdbc.queryForList("SELECT id FROM polls WHERE pollster_id=? AND nullif(btrim(tse_registration_number),'') IS NULL AND field_end=? AND published_at=?",UUID.class,institute.getId(),r.fieldEnd(),java.sql.Timestamp.from(r.publishedAt()));
  var matches=matchingIds.stream().map(polls::get).toList();
  if(matches.size()>1)throw new IllegalArgumentException("Chave lógica ambígua; corrija duplicatas antes de importar.");

  PollResponse existing=matches.isEmpty()?null:matches.getFirst();

  PollRequest previous=dryRun&&simulated.containsKey(key)?(PollRequest)simulated.get(key):existing==null?null:asRequest(existing);

  String action=previous==null?"CREATED":equivalent(previous,request)?"UNCHANGED":"UPDATED";

  UUID id=existing==null?null:existing.id();

  if(!dryRun&&!action.equals("UNCHANGED"))id=(existing==null?polls.create(request):polls.update(id,request)).id();

  if(dryRun)simulated.put(key,request);

  return new Detail(index,action,id,key,normalized,null);

 }

 private PollRequest asRequest(PollResponse p){return new PollRequest(p.pollster().id(),p.registrationNumber(),p.fieldStart(),p.fieldEnd(),p.publishedAt(),p.sampleSize(),p.marginOfError(),p.methodology(),p.sourceUrl(),p.voteBasis(),p.results().stream().map(r->new PollRequest.Result(r.candidateId(),r.originalPercentage())).toList());}

 private boolean equivalent(PollRequest a,PollRequest b){

  return Objects.equals(a.registrationNumber(),b.registrationNumber())&&a.fieldStart().equals(b.fieldStart())&&a.fieldEnd().equals(b.fieldEnd())&&a.publishedAt().equals(b.publishedAt())&&Objects.equals(a.sampleSize(),b.sampleSize())&&decimal(a.marginOfError(),b.marginOfError())&&Objects.equals(a.methodology(),b.methodology())&&Objects.equals(a.sourceUrl(),b.sourceUrl())&&a.voteBasis()==b.voteBasis()&&a.results().size()==b.results().size()&&a.results().stream().allMatch(r->b.results().stream().anyMatch(s->r.candidateId().equals(s.candidateId())&&decimal(r.originalPercentage(),s.originalPercentage())));

 }

 private boolean decimal(BigDecimal a,BigDecimal b){return a==null?b==null:b!=null&&a.compareTo(b)==0;}

 private Detail performance(JsonNode raw,int index,Map<String,Object> simulated,boolean dryRun){

  if(raw.has("historicalScore")||raw.has("historicalFactor")||raw.has("historicalWeightFactor"))throw new IllegalArgumentException("O fator histórico é derivado pelo backend.");

  var r=json.treeToValue(raw,PerformanceRow.class);var errors=validator.validate(r);if(!errors.isEmpty())throw new IllegalArgumentException(errors.stream().map(e->e.getPropertyPath()+": "+e.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));

  var institute=pollster(r.pollsterSlug());String key=institute.getId()+"|"+r.electionKey().trim()+"|"+r.round();

  var matches=performances.list().stream().filter(p->p.pollster().id().equals(institute.getId())&&p.electionKey().equals(r.electionKey().trim())&&p.round().equals(r.round())).toList();

  if(matches.size()>1)throw new IllegalArgumentException("Performance duplicada.");var existing=matches.isEmpty()?null:matches.getFirst();

  var canonical=new PerformanceRow(r.pollsterSlug().trim(),r.electionKey().trim(),r.round(),r.candidateError().stripTrailingZeros(),r.spreadError().stripTrailingZeros(),r.sourceUrl(),r.sourceDescription(),r.notes());

  PerformanceRow previous=existing==null?null:new PerformanceRow(existing.pollster().slug(),existing.electionKey(),existing.round(),existing.candidateError()==null?null:existing.candidateError().stripTrailingZeros(),existing.spreadError().stripTrailingZeros(),existing.sourceUrl(),existing.sourceDescription(),existing.notes());

  if(dryRun&&simulated.containsKey(key))previous=(PerformanceRow)simulated.get(key);

  String action=previous==null?"CREATED":canonical.equals(previous)&& (dryRun&&simulated.containsKey(key)||existing==null||existing.verifiedAt()!=null)?"UNCHANGED":"UPDATED";

  UUID id=existing==null?null:existing.id();

  if(!dryRun&&!action.equals("UNCHANGED"))id=performances.importAudited(id,new PollsterPerformanceRequest(institute.getId(),r.electionKey().trim(),r.round(),r.candidateError(),r.spreadError(),r.sourceUrl(),r.sourceDescription()),r.notes(),clock.instant()).id();

  if(dryRun)simulated.put(key,canonical);return new Detail(index,action,id,key,null,null);

 }

}
