package br.com.election.readiness;
import br.com.election.aggregate.*;
import br.com.election.poll.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
@Service @Transactional(readOnly=true)
public class AggregateAuditService {
 public record Audit(AggregateResponse aggregate,List<PollResponse> consideredPolls,List<AggregateResponse.ExcludedPoll> discardedPolls,
  BigDecimal sumWeights,BigDecimal sumRawWeights,LocalDateTime oldestFieldMidpoint,LocalDateTime newestFieldMidpoint,List<String> warnings){}
 private final AggregateService aggregates;private final PollService polls;
 public AggregateAuditService(AggregateService aggregates,PollService polls){this.aggregates=aggregates;this.polls=polls;}
 public List<Audit> current(){return at(aggregates.today());}
 public List<Audit> at(LocalDate date){var all=polls.list();return aggregates.all(date).stream().map(a->{
  Set<UUID> ids=new HashSet<>();a.contributions().forEach(c->ids.add(c.pollId()));
  var included=all.stream().filter(p->ids.contains(p.id())).toList();
  Map<UUID,String> reasons=new HashMap<>();a.excludedPolls().forEach(p->reasons.put(p.pollId(),p.reason()));
  var discarded=all.stream().filter(p->!ids.contains(p.id())).map(p->new AggregateResponse.ExcludedPoll(p.id(),p.pollster().name(),reasons.getOrDefault(p.id(),p.publishedAt().isBefore(date.plusDays(1).atStartOfDay(AggregateService.REFERENCE_ZONE).toInstant())?"Pesquisa substituída pela mais recente do instituto.":"Publicação posterior à data de referência."))).toList();
  List<String> warnings=new ArrayList<>(a.warnings());if(included.size()<3)warnings.add("Poucas pesquisas: menos de 3 institutos.");
  for(var p:included){if(p.marginOfError()==null)warnings.add(p.pollster().name()+": margem ausente.");if(p.sourceUrl()==null||p.sourceUrl().isBlank())warnings.add(p.pollster().name()+": fonte ausente.");if(p.fieldEnd().isBefore(date.minusDays(14)))warnings.add(p.pollster().name()+": pesquisa antiga (campo terminou há mais de 14 dias).");}
  return new Audit(a,included,discarded,a.contributions().stream().map(AggregateResponse.Contribution::normalizedWeight).reduce(BigDecimal.ZERO,BigDecimal::add),a.contributions().stream().map(AggregateResponse.Contribution::rawWeight).reduce(BigDecimal.ZERO,BigDecimal::add),a.contributions().stream().map(AggregateResponse.Contribution::fieldMidpoint).min(Comparator.naturalOrder()).orElse(null),a.contributions().stream().map(AggregateResponse.Contribution::fieldMidpoint).max(Comparator.naturalOrder()).orElse(null),List.copyOf(warnings));
 }).toList();}
}
