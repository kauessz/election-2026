package br.com.election.readiness;
import br.com.election.aggregate.*;
import br.com.election.tse.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;
@Service
public class PreElectionComparisonService {
 public record Difference(UUID candidateId,String name,BigDecimal frozenPercentage,BigDecimal officialPercentage,BigDecimal currentDifference,BigDecimal finalError){}
 public record ModelDifference(AggregateMethod method,List<Difference> candidates,BigDecimal currentDifference,BigDecimal finalError){}
 public record Comparison(String status,PreElectionFreeze freeze,OfficialResultResponse officialCurrent,List<ModelDifference> models,List<String> warnings){}
 private final FreezeService freezes;private final OfficialResultsService official;
 public PreElectionComparisonService(FreezeService freezes,OfficialResultsService official){this.freezes=freezes;this.official=official;}
 public Comparison current(){return compare(freezes.active(),official.getPresidentNationalResult());}
 public Comparison compare(PreElectionFreeze freeze,OfficialResultResponse response){
  if(freeze==null)return new Comparison("NO_FREEZE",null,response,List.of(),List.of("Nenhum modelo pré-eleição congelado."));
  var result=response.result();if(result==null||result.votes().validVotes()<=0)return new Comparison("NOT_STARTED",freeze,response,List.of(),List.of("Resultado oficial ainda indisponível para comparação."));
  boolean completed=result.finalTotalization();List<ModelDifference> models=new ArrayList<>();List<String> warnings=new ArrayList<>();
  if(response.status()==OfficialDataStatus.STALE||response.status()==OfficialDataStatus.UNAVAILABLE)warnings.add("Fonte desatualizada ou indisponível: diferenças usam o último resultado recebido.");
  for(var a:freeze.aggregateResults()){
   List<Difference> differences=new ArrayList<>();
   for(var c:a.results()){
    Integer ballot=freeze.composition().candidateBallotNumbers().get(c.candidateId());
    var actual=result.candidates().stream().filter(x->Objects.equals(x.candidateNumber(),Objects.toString(ballot,""))).findFirst().orElse(null);
    if(actual==null||actual.percentage()==null){warnings.add("Candidato congelado sem correspondência oficial: "+c.candidateId());continue;}
    var error=c.percentage().subtract(actual.percentage()).abs();differences.add(new Difference(c.candidateId(),c.candidateName(),c.percentage(),actual.percentage(),completed?null:error,completed?error:null));
   }
   BigDecimal spreadError=null;if(differences.size()==2){var x=differences.getFirst();var y=differences.getLast();spreadError=x.frozenPercentage().subtract(y.frozenPercentage()).subtract(x.officialPercentage().subtract(y.officialPercentage())).abs();}
   models.add(new ModelDifference(a.method(),List.copyOf(differences),completed?null:spreadError,completed?spreadError:null));
  }
  return new Comparison(completed?"FINAL":"PARTIAL",freeze,response,List.copyOf(models),List.copyOf(warnings));
 }
}
