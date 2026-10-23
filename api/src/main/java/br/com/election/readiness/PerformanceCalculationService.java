package br.com.election.readiness;
import br.com.election.poll.*;
import br.com.election.candidate.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static java.math.MathContext.DECIMAL128;
@Service
public class PerformanceCalculationService {
 public record Request(UUID pollId,LocalDate electionDate,Integer candidateA,Integer candidateB,boolean officialFinal,List<ImportModels.CandidateResult> officialResults){}
 public record CandidateError(int ballotNumber,BigDecimal predicted,BigDecimal actual,BigDecimal absoluteError){}
 public record Calculation(UUID pollId,List<CandidateError> candidateErrors,BigDecimal candidateError,BigDecimal predictedSpread,BigDecimal actualSpread,BigDecimal spreadError,boolean persisted){}
 private final PollService polls;private final CandidateRepository candidates;
 public PerformanceCalculationService(PollService polls,CandidateRepository candidates){this.polls=polls;this.candidates=candidates;}
 public Calculation calculate(Request r){
  if(r==null||r.pollId()==null||r.electionDate()==null||r.candidateA()==null||r.candidateB()==null||r.candidateA().equals(r.candidateB())||!r.officialFinal()||r.officialResults()==null)throw new IllegalArgumentException("Informe pesquisa, data do 1º turno, par distinto e resultado final oficial em votos válidos.");
  var poll=polls.get(r.pollId());var cutoff=r.electionDate().atStartOfDay(br.com.election.aggregate.AggregateService.REFERENCE_ZONE).toInstant();
  var latest=polls.list().stream().filter(p->p.pollster().id().equals(poll.pollster().id())&&p.publishedAt().isBefore(cutoff)&&p.fieldEnd().isBefore(r.electionDate())).findFirst().orElseThrow(()->new IllegalArgumentException("Nenhuma pesquisa anterior ao primeiro turno."));
  if(!latest.id().equals(poll.id()))throw new IllegalArgumentException("Selecione a última pesquisa do instituto anterior ao primeiro turno.");
  Map<UUID,Integer> numbers=new HashMap<>();candidates.findAll().forEach(c->numbers.put(c.getId(),c.getBallotNumber()));
  Map<Integer,BigDecimal> actual=new HashMap<>();for(var c:r.officialResults()){if(c==null||c.ballotNumber()==null||c.percentage()==null||c.percentage().signum()<0||c.percentage().compareTo(new BigDecimal("100"))>0||actual.put(c.ballotNumber(),c.percentage())!=null)throw new IllegalArgumentException("Resultado oficial inválido ou duplicado.");}
  if(actual.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.1"))>0)throw new IllegalArgumentException("Resultado oficial deve somar 100% de votos válidos (tolerância 0,1 p.p.).");
  Map<Integer,BigDecimal> predicted=new HashMap<>();poll.results().forEach(c->predicted.put(numbers.get(c.candidateId()),c.normalizedPercentage()));
  if(!actual.keySet().equals(predicted.keySet())||!actual.containsKey(r.candidateA())||!actual.containsKey(r.candidateB()))throw new IllegalArgumentException("Pesquisa e resultado devem conter os mesmos candidatos, incluindo A e B.");
  var errors=predicted.keySet().stream().sorted().map(n->new CandidateError(n,predicted.get(n),actual.get(n),predicted.get(n).subtract(actual.get(n)).abs())).toList();
  var ps=predicted.get(r.candidateA()).subtract(predicted.get(r.candidateB()));var as=actual.get(r.candidateA()).subtract(actual.get(r.candidateB()));
  return new Calculation(poll.id(),errors,predicted.get(r.candidateA()).subtract(actual.get(r.candidateA())).abs(),ps,as,ps.subtract(as).abs(),false);
 }
}
