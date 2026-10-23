package br.com.election.projection;
import org.springframework.stereotype.Component;
import br.com.election.tse.*;
import br.com.election.projection.AnalyticsInputStore.Recorded;
import java.time.*;
import java.util.*;
import static br.com.election.projection.ProjectionMath.*;
@Component
public class TrendEngine {
 public static final List<Integer> WINDOWS=List.of(5,15,30);
 public TrendResponse calculate(List<Recorded> records,Instant at){
  var sorted=records.stream().filter(r->!r.capturedAt().isAfter(at)&&!r.result().generatedAt().isAfter(at)&&r.result().scope().type()==ElectionScopeType.NATIONAL&&r.result().round()==2)
   .sorted(Comparator.comparing((Recorded r)->r.result().generatedAt()).thenComparing(Recorded::capturedAt).thenComparing(Recorded::id)).toList();
  List<TrendResponse.Window> windows=new ArrayList<>();
  for(int minutes:WINDOWS){
   Instant start=at.minusSeconds(minutes*60L),from=null,to=null;long valid=0;int used=0,excluded=0;
   Map<String,Long> deltas=new TreeMap<>();Map<String,String> names=new HashMap<>();
   for(int i=1;i<sorted.size();i++){
    var previous=sorted.get(i-1).result();var current=sorted.get(i).result();
    if(current.generatedAt().isBefore(start))continue;
    if(previous.generatedAt().isBefore(start)||!compatible(previous,current)){excluded++;continue;}
    long dv=current.votes().validVotes()-previous.votes().validVotes();Map<String,Long> interval=new TreeMap<>();boolean invalid=dv<=0;
    for(var c:current.candidates()){
     var prior=previous.candidates().stream().filter(x->x.candidateNumber().equals(c.candidateNumber())).findFirst().orElse(null);
     if(prior==null){invalid=true;break;}long dc=c.votes()-prior.votes();if(dc<0)invalid=true;interval.put(c.candidateNumber(),dc);
    }
    long candidateTotal=interval.values().stream().reduce(0L,Math::addExact);
    if(invalid||candidateTotal<=0){excluded++;continue;}
    valid=Math.addExact(valid,dv);used++;if(from==null)from=previous.generatedAt();to=current.generatedAt();
    interval.forEach((k,v)->deltas.merge(k,v,Math::addExact));current.candidates().forEach(c->names.put(c.candidateNumber(),c.displayName()));
   }
   long total=deltas.values().stream().reduce(0L,Math::addExact);List<TrendResponse.CandidateShare> shares=new ArrayList<>();
   if(used>0)for(var d:deltas.entrySet())shares.add(new TrendResponse.CandidateShare(d.getKey(),names.get(d.getKey()),d.getValue(),ratio(d.getValue(),total),ratio(d.getValue(),total).multiply(HUNDRED,MC)));
   windows.add(new TrendResponse.Window(minutes,start,at,from,to,used>0?valid:null,List.copyOf(shares),used>0,used,excluded,used>0?null:"Sem intervalos consecutivos válidos inteiramente dentro da janela"));
  }
  return new TrendResponse("TREND",at,sorted.isEmpty()?null:sorted.getLast().result().generatedAt(),List.copyOf(windows),List.of("Composição dos votos dos candidatos usa a soma de seus deltas, que pode diferir do delta de votos válidos. Intervalos cruzando a borda da janela não são rateados nem interpolados."));
 }
 private boolean compatible(OfficialElectionResult a,OfficialElectionResult b){
  return a.electionId().equals(b.electionId())&&a.round()==b.round()&&a.scope().equals(b.scope())&&b.generatedAt().isAfter(a.generatedAt())&&
   new HashSet<>(a.candidates().stream().map(OfficialElectionResult.CandidateResult::candidateNumber).toList()).equals(new HashSet<>(b.candidates().stream().map(OfficialElectionResult.CandidateResult::candidateNumber).toList()))&&
   b.sections().totalized()>=a.sections().totalized();
 }
}
