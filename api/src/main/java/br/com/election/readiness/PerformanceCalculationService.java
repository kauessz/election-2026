package br.com.election.readiness;
import br.com.election.poll.*;
import br.com.election.candidate.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service
public class PerformanceCalculationService {
 public record Request(UUID pollId,LocalDate electionDate,Integer candidateA,Integer candidateB,boolean officialFinal,List<ImportModels.CandidateResult> officialResults,
  List<ImportModels.CandidateResult> predictedCandidateResults,List<ImportModels.CandidateResult> actualCandidateResults,Boolean completeCandidateSet){
  public Request(UUID pollId,LocalDate electionDate,Integer candidateA,Integer candidateB,boolean officialFinal,List<ImportModels.CandidateResult> officialResults){
   this(pollId,electionDate,candidateA,candidateB,officialFinal,officialResults,null,null,null);
  }
 }
 public record CandidateError(int ballotNumber,BigDecimal predicted,BigDecimal actual,BigDecimal absoluteError){}
 public record Calculation(UUID pollId,List<CandidateError> candidateErrors,BigDecimal candidateError,BigDecimal predictedSpread,BigDecimal actualSpread,BigDecimal spreadError,boolean persisted){}
 private final PollService polls;private final CandidateRepository candidates;
 public PerformanceCalculationService(PollService polls,CandidateRepository candidates){this.polls=polls;this.candidates=candidates;}
 public Calculation calculate(Request r){
  if(r!=null&&(r.predictedCandidateResults()!=null||r.actualCandidateResults()!=null))return calculateDirect(r);
  if(r==null||r.pollId()==null||r.electionDate()==null||r.candidateA()==null||r.candidateB()==null||r.candidateA().equals(r.candidateB())||!r.officialFinal()||r.officialResults()==null)throw new IllegalArgumentException("Informe pesquisa, data do 1º turno, par distinto e resultado final oficial em votos válidos.");
  var poll=polls.get(r.pollId());var cutoff=r.electionDate().atStartOfDay(br.com.election.aggregate.AggregateService.REFERENCE_ZONE).toInstant();
  var latest=polls.list().stream().filter(p->p.pollster().id().equals(poll.pollster().id())&&p.publishedAt().isBefore(cutoff)&&p.fieldEnd().isBefore(r.electionDate())).findFirst().orElseThrow(()->new IllegalArgumentException("Nenhuma pesquisa anterior ao primeiro turno."));
  if(!latest.id().equals(poll.id()))throw new IllegalArgumentException("Selecione a última pesquisa do instituto anterior ao primeiro turno.");
  Map<UUID,Integer> numbers=new HashMap<>();candidates.findAll().forEach(c->numbers.put(c.getId(),c.getBallotNumber()));
  Map<Integer,BigDecimal> actual=new HashMap<>();for(var c:r.officialResults()){if(c==null||c.ballotNumber()==null||c.percentage()==null||c.percentage().signum()<0||c.percentage().compareTo(new BigDecimal("100"))>0||actual.put(c.ballotNumber(),c.percentage())!=null)throw new IllegalArgumentException("Resultado oficial inválido ou duplicado.");}
  if(actual.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.1"))>0)throw new IllegalArgumentException("Resultado oficial deve somar 100% de votos válidos (tolerância 0,1 p.p.).");
  Map<Integer,BigDecimal> predicted=new HashMap<>();poll.results().forEach(c->predicted.put(numbers.get(c.candidateId()),c.normalizedPercentage()));
  if(!actual.keySet().equals(predicted.keySet())||!actual.containsKey(r.candidateA())||!actual.containsKey(r.candidateB()))throw new IllegalArgumentException("Pesquisa e resultado devem conter os mesmos candidatos, incluindo A e B.");
  return result(poll.id(),r.candidateA(),r.candidateB(),predicted,actual);
 }
 private Calculation calculateDirect(Request r){
  if(r.pollId()!=null||r.officialResults()!=null)throw new IllegalArgumentException("Não misture pollId/officialResults com os pares diretos.");
  if(!r.officialFinal()||r.candidateA()==null||r.candidateB()==null||r.candidateA().equals(r.candidateB()))throw new IllegalArgumentException("Informe par distinto e resultado final oficial em votos válidos.");
  var predicted=directResults(r.predictedCandidateResults());var actual=directResults(r.actualCandidateResults());
  if(!predicted.keySet().equals(actual.keySet())||!actual.keySet().equals(Set.of(r.candidateA(),r.candidateB())))throw new IllegalArgumentException("Informe os mesmos dois candidatos nos pares previsto e oficial.");
  if(!Boolean.FALSE.equals(r.completeCandidateSet())){
   requireComplete(predicted);requireComplete(actual);
  }
  // A first-round pair is a subset of all valid votes. Never renormalize it to 100%.
  return result(null,r.candidateA(),r.candidateB(),predicted,actual);
 }
 private Map<Integer,BigDecimal> directResults(List<ImportModels.CandidateResult> results){
  if(results==null||results.size()!=2)throw new IllegalArgumentException("Informe exatamente dois candidatos em cada par, em votos válidos na mesma base.");
  Map<Integer,BigDecimal> values=new HashMap<>();
  for(var c:results){
   if(c==null||c.ballotNumber()==null||c.ballotNumber()<=0||c.percentage()==null||c.percentage().signum()<0||c.percentage().compareTo(new BigDecimal("100"))>0||values.put(c.ballotNumber(),c.percentage())!=null)throw new IllegalArgumentException("Percentual inválido ou candidato duplicado no par.");
  }
  if(values.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(new BigDecimal("100.1"))>0)throw new IllegalArgumentException("Par em votos válidos não pode exceder 100% (tolerância 0,1 p.p.).");
  return values;
 }
 private void requireComplete(Map<Integer,BigDecimal> values){
  if(values.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.1"))>0)throw new IllegalArgumentException("Conjunto completo deve somar 100% de votos válidos (tolerância 0,1 p.p.).");
 }
 private Calculation result(UUID pollId,int candidateA,int candidateB,Map<Integer,BigDecimal> predicted,Map<Integer,BigDecimal> actual){
  var errors=predicted.keySet().stream().sorted().map(n->new CandidateError(n,predicted.get(n),actual.get(n),predicted.get(n).subtract(actual.get(n)).abs())).toList();
  var ps=predicted.get(candidateA).subtract(predicted.get(candidateB));var as=actual.get(candidateA).subtract(actual.get(candidateB));
  return new Calculation(pollId,errors,predicted.get(candidateA).subtract(actual.get(candidateA)).abs(),ps,as,ps.subtract(as).abs(),false);
 }
}
