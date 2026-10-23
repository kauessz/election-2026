package br.com.election.projection;
import br.com.election.tse.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.election.tse.OfficialElectionResult.*;
import static br.com.election.projection.ProjectionMath.*;
public final class ProjectionFixtures {
 public static final Instant TIME=Instant.parse("2026-10-25T22:00:00Z");
 public static ProjectionProperties properties(){return new ProjectionProperties(true,new BigDecimal(".05"),new BigDecimal(".05"),10,new BigDecimal(".8"),new BigDecimal(".8"),4,3);}
 public static OfficialElectionResult result(Scope scope,int round,long electorate,long represented,long a,long b,Instant at,boolean finalFlag){
  long valid=a+b;BigDecimal percent=valid==0?ZERO:ratio(a,valid).multiply(HUNDRED).setScale(2,java.math.RoundingMode.HALF_UP);
  return new OfficialElectionResult(round==1?"6257":"6258",round,scope,at,"synthetic",finalFlag,
   new Sections(100L,electorate==0?0:represented*100/electorate,electorate==0?ZERO:ratio(represented,electorate).multiply(HUNDRED),electorate==0?100:100-represented*100/electorate),
   new Electorate(electorate,represented,electorate==0?ZERO:ratio(represented,electorate).multiply(HUNDRED),0L,ZERO,valid),
   new Votes(valid,valid,0L,0L,valid,valid),List.of(new CandidateResult("13","tse-13","Nome 13","Candidato 13",a,percent,percent,null),new CandidateResult("22","tse-22","Nome 22","Candidato 22",b,valid==0?ZERO:HUNDRED.subtract(percent),valid==0?ZERO:HUNDRED.subtract(percent),null)),List.of());
 }
 public static BaselineData baseline(BrazilState state,long electorate){return BaselineData.from(result(Scope.state(state),1,electorate,electorate,electorate*3/10,electorate*2/10,TIME.minusSeconds(86400),true),TIME.minusSeconds(3600),"https://resultados.tse.jus.br/synthetic-fixture-only");}
 public static AnalyticsInputStore.Recorded recorded(OfficialElectionResult r){return new AnalyticsInputStore.Recorded(UUID.nameUUIDFromBytes((r.scope().toString()+r.generatedAt()+r.votes()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),r,r.generatedAt());}
 public static List<BaselineData> baselines(){return Arrays.stream(BrazilState.values()).map(uf->baseline(uf,1000)).toList();}
 public static List<AnalyticsInputStore.Recorded> frame(long represented,int statesWithVotes){
  List<AnalyticsInputStore.Recorded> result=new ArrayList<>();int i=0;long a=0,b=0,representedTotal=0;
  for(var uf:BrazilState.values()){
   long e=i++<statesWithVotes?represented:0;long va=e*4/10,vb=e*2/10;a+=va;b+=vb;representedTotal+=e;
   result.add(recorded(result(Scope.state(uf),2,1000,e,va,vb,TIME,false)));
  }
  result.add(recorded(result(Scope.national(),2,27000,representedTotal,a,b,TIME,false)));return result;
 }
}
