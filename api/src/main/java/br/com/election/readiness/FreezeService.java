package br.com.election.readiness;

import br.com.election.aggregate.*;

import br.com.election.poll.*;

import br.com.election.pollster.*;

import br.com.election.candidate.*;

import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.annotation.*;

import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;

import java.time.Clock;

import java.util.*;

@Service

public class FreezeService {

 public record Request(@NotBlank @Size(max=200) String label,@Size(max=100) String gitCommit,@Size(max=5000) String notes){}

 private final JdbcTemplate jdbc;private final JsonMapper json;private final DataQualityService quality;private final AggregateService aggregates;

 private final PollService polls;private final PollsterPerformanceService performances;private final CandidateRepository candidates;private final Clock clock;

 public FreezeService(JdbcTemplate jdbc,JsonMapper json,DataQualityService quality,AggregateService aggregates,PollService polls,PollsterPerformanceService performances,CandidateRepository candidates,Clock clock){this.jdbc=jdbc;this.json=json;this.quality=quality;this.aggregates=aggregates;this.polls=polls;this.performances=performances;this.candidates=candidates;this.clock=clock;}

 @Transactional(readOnly=true) public PreElectionFreeze active(){return jdbc.query("SELECT * FROM pre_election_freezes WHERE active",(rs,n)->new PreElectionFreeze(rs.getObject("id",UUID.class),rs.getString("label"),rs.getTimestamp("frozen_at").toInstant(),rs.getString("git_commit"),json.readValue(rs.getString("aggregation_configuration"),AggregationProperties.class),json.readValue(rs.getString("poll_ids"),json.getTypeFactory().constructCollectionType(List.class,UUID.class)),json.readValue(rs.getString("pollster_performance_ids"),json.getTypeFactory().constructCollectionType(List.class,UUID.class)),json.readValue(rs.getString("aggregate_results"),json.getTypeFactory().constructCollectionType(List.class,AggregateResponse.class)),json.readValue(rs.getString("composition"),PreElectionFreeze.Composition.class),rs.getString("notes"),rs.getBoolean("active"))).stream().findFirst().orElse(null);}

 @Transactional(isolation=Isolation.SERIALIZABLE) public PreElectionFreeze create(Request request){

  jdbc.queryForObject("SELECT pg_advisory_xact_lock(20260501) IS NULL",Boolean.class);

  if(active()!=null)throw new IllegalArgumentException("Já existe um freeze ativo e imutável.");

  var report=quality.check();if(!report.ready())throw new IllegalArgumentException("Freeze rejeitado: data quality contém ERROR. Consulte /api/admin/data-quality.");

  var results=aggregates.all(aggregates.today());if(results.size()!=4||results.stream().anyMatch(a->a.pollCount()==0))throw new IllegalArgumentException("Os quatro agregados precisam conter pesquisas elegíveis.");

  var ids=results.stream().flatMap(a->a.contributions().stream()).map(AggregateResponse.Contribution::pollId).distinct().sorted().toList();

  Set<UUID> institutes=new HashSet<>();results.forEach(a->a.contributions().forEach(c->institutes.add(c.pollsterId())));

  var cfg=results.getFirst().configuration();var history=performances.list().stream().filter(p->institutes.contains(p.pollster().id())&&cfg.historicalElectionKey().equals(p.electionKey())&&cfg.historicalRound()==p.round()&&p.spreadError()!=null).toList();

  Map<UUID,Integer> numbers=new HashMap<>();candidates.findAll().forEach(c->numbers.put(c.getId(),c.getBallotNumber()));

  var composition=new PreElectionFreeze.Composition(polls.list().stream().filter(p->ids.contains(p.id())).toList(),history,Map.copyOf(numbers));

  var freeze=new PreElectionFreeze(UUID.randomUUID(),request.label().trim(),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS),request.gitCommit(),cfg,ids,history.stream().map(PollsterPerformanceResponse::id).sorted().toList(),results,composition,request.notes(),true);

  jdbc.update("INSERT INTO pre_election_freezes(id,label,frozen_at,git_commit,aggregation_configuration,poll_ids,pollster_performance_ids,aggregate_results,composition,notes,active) VALUES(?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),CAST(? AS jsonb),?,true)",freeze.id(),freeze.label(),Timestamp.from(freeze.frozenAt()),freeze.gitCommit(),json.writeValueAsString(cfg),json.writeValueAsString(ids),json.writeValueAsString(freeze.pollsterPerformanceIds()),json.writeValueAsString(results),json.writeValueAsString(composition),freeze.notes());return freeze;

 }

}
