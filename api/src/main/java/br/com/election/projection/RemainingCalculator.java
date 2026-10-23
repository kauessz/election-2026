package br.com.election.projection;
import org.springframework.stereotype.Component;
import br.com.election.tse.*;
import br.com.election.projection.AnalyticsInputStore.Recorded;
import java.time.Instant;
import java.util.*;
import static br.com.election.projection.ProjectionMath.*;
@Component
public class RemainingCalculator {
 public RemainingResponse calculate(List<Recorded> rows,Instant at){
  Map<String,OfficialElectionResult> states=new HashMap<>();ProjectionReplay.latestAt(rows,at).stream().map(Recorded::result).filter(r->r.scope().type()==ElectionScopeType.STATE).forEach(r->states.put(r.scope().code(),r));
  List<RemainingResponse.State> result=new ArrayList<>();int available=0;
  for(var uf:BrazilState.values()){
   var r=states.get(uf.name());Long total=r==null?null:r.electorate().total(),represented=r==null?null:r.electorate().totalized();
   if(total==null||total<=0||represented==null||represented<0||represented>total)result.add(new RemainingResponse.State(uf.name(),false,total,represented,null,null,null,r,"Eleitorado totalizado indisponível/inválido"));
   else {available++;result.add(new RemainingResponse.State(uf.name(),true,total,represented,total-represented,ratio(represented,total).multiply(HUNDRED,MC),ratio(total-represented,total).multiply(HUNDRED,MC),r,null));}
  }
  result.sort(Comparator.comparing(RemainingResponse.State::remainingElectorate,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(RemainingResponse.State::uf));
  return new RemainingResponse("TREND",at,available,27,List.copyOf(result),List.of("Eleitorado pendente não é número de votos futuros: inclui abstenções, brancos e nulos ainda desconhecidos."));
 }
}
