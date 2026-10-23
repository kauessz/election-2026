package br.com.election.projection;
import java.time.Instant;
import java.util.*;
import br.com.election.projection.AnalyticsInputStore.Recorded;
public class ProjectionReplay {
 private final ProjectionEngine engine;
 public ProjectionReplay(ProjectionEngine engine){this.engine=engine;}
 public List<ProjectionResponse> replay(List<Recorded> official,List<BaselineData> baselines,List<Instant> times){
  return times.stream().sorted().map(at->engine.calculate(latestAt(official,at),baselines.stream().filter(b->!b.capturedAt().isAfter(at)&&!b.sourceGeneratedAt().isAfter(at)).toList(),at)).toList();
 }
 public static List<Recorded> latestAt(List<Recorded> official,Instant at){
  Comparator<Recorded> order=Comparator.comparing((Recorded r)->r.result().generatedAt()).thenComparing(Recorded::capturedAt).thenComparing(Recorded::id);
  Map<String,Recorded> latest=new TreeMap<>();
  for(var r:official){if(r.capturedAt().isAfter(at)||r.result().generatedAt().isAfter(at))continue;
   String key=r.result().scope().type()+":"+r.result().scope().code();latest.merge(key,r,(a,b)->order.compare(a,b)>=0?a:b);
  }
  return List.copyOf(latest.values());
 }
}
