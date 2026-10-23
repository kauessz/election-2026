package br.com.election.projection;
import br.com.election.tse.*;
import br.com.election.projection.AnalyticsInputStore.Recorded;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static br.com.election.projection.ProjectionMath.*;
import static br.com.election.projection.ProjectionResponse.*;
@Component
public class ProjectionEngine {
 public static final String MODEL="STATE_SHRINKAGE_V1";
 private static final BigDecimal MEDIUM_PROGRESS=new BigDecimal("0.20"),HIGH_PROGRESS=new BigDecimal("0.60");
 private final ProjectionProperties p;
 public ProjectionEngine(ProjectionProperties p){this.p=p;}
 public ProjectionResponse calculate(List<Recorded> records,List<BaselineData> baselines,Instant at){
  var selected=ProjectionReplay.latestAt(records,at);
  Map<String,OfficialElectionResult> current=new HashMap<>();
  selected.stream().map(Recorded::result).filter(r->r.round()==2&&r.scope().type()==ElectionScopeType.STATE).forEach(r->current.put(r.scope().code(),r));
  var national=selected.stream().map(Recorded::result).filter(r->r.scope().type()==ElectionScopeType.NATIONAL&&r.round()==2).findFirst().orElse(null);
  Map<String,BaselineData> prior=new HashMap<>();
  baselines.stream().sorted(Comparator.comparing(BaselineData::sourceGeneratedAt).thenComparing(BaselineData::capturedAt)).filter(b->b.scope().type()==ElectionScopeType.STATE&&!b.capturedAt().isAfter(at)&&!b.sourceGeneratedAt().isAfter(at)).forEach(b->prior.put(b.scope().code(),b));
  List<String> missingBaseline=new ArrayList<>(),missingOfficial=new ArrayList<>(),warnings=new ArrayList<>();
  List<StateProjection> states=new ArrayList<>();int withData=0;Set<BrazilState> dataStates=EnumSet.noneOf(BrazilState.class);
  for(var uf:BrazilState.values()){
   var r=current.get(uf.name());var b=prior.get(uf.name());
   if(b==null)missingBaseline.add(uf.name());if(r==null)missingOfficial.add(uf.name());
   if(r!=null&&r.electorate().totalized()!=null&&r.electorate().totalized()>0&&r.votes().validVotes()>0){withData++;dataStates.add(uf);}
   states.add(state(uf.name(),r,b));
  }
  var geography=GeographicGate.evaluate(dataStates,p);
  boolean complete=states.stream().allMatch(StateProjection::available);
  BigDecimal progress=validElectorate(national)?ratio(national.electorate().totalized(),national.electorate().total()):null;
  if(!missingBaseline.isEmpty())warnings.add("UFs sem baseline: "+String.join(", ",missingBaseline));
  if(!missingOfficial.isEmpty())warnings.add("UFs sem snapshot oficial: "+String.join(", ",missingOfficial));
  states.forEach(s->s.warnings().forEach(w->warnings.add(s.uf()+": "+w)));
  if(!geography.thresholdsMet())warnings.add("Gate geográfico insuficiente: "+geography.regionsWithData()+" regiões e "+geography.keyStatesWithData()+" estados-chave com dados.");
  boolean finalOfficial=national!=null&&national.finalTotalization()&&pair(national)!=null&&national.votes().validVotes()>0;
  var overseas=overseas(current.get("ZZ"),national,complete,finalOfficial);
  warnings.add(overseas.note());
  ProjectionStatus status;
  if(finalOfficial)status=ProjectionStatus.FINAL;
  else if(national!=null&&national.sections().totalized()==0&&national.votes().totalVotes()==0&&withData==0)status=ProjectionStatus.NOT_STARTED;
  else if(progress==null||progress.compareTo(p.minimumNationalElectorateProgress())<0||withData<p.minimumStatesWithData()||!geography.thresholdsMet()||!complete)status=ProjectionStatus.INSUFFICIENT_DATA;
  else status=progress.compareTo(p.highCoverageProgress())>=0?ProjectionStatus.HIGH_COVERAGE:ProjectionStatus.ACTIVE;
  boolean publish=p.enabled()&&(status==ProjectionStatus.ACTIVE||status==ProjectionStatus.HIGH_COVERAGE||status==ProjectionStatus.FINAL);
  if(!p.enabled()){warnings.add("Projeção desabilitada por configuração.");status=ProjectionStatus.INSUFFICIENT_DATA;}
  List<CandidateProjection> results=new ArrayList<>();
  if(finalOfficial){for(var c:pair(national))results.add(new CandidateProjection(c.candidateNumber(),c.displayName(),BigDecimal.valueOf(c.votes()),c.percentage()));}
  else if(complete){
   Map<String,BigDecimal> sums=new LinkedHashMap<>();PAIR.forEach(n->sums.put(n,ZERO));
   for(var state:states)for(var c:state.candidates())sums.merge(c.candidateNumber(),c.projectedCandidateVotes(),BigDecimal::add);
   if(overseas.incorporated())for(var c:overseas.observedCandidates())sums.merge(c.candidateNumber(),BigDecimal.valueOf(c.votes()),BigDecimal::add);
   BigDecimal total=sums.values().stream().reduce(ZERO,BigDecimal::add);
   for(String number:PAIR){String name=states.getFirst().candidates().stream().filter(c->number.equals(c.candidateNumber())).findFirst().orElseThrow().name();results.add(new CandidateProjection(number,name,sums.get(number),total.signum()==0?null:sums.get(number).divide(total,MC).multiply(HUNDRED,MC)));}
  }
  Spread spread=null;
  if(results.size()==2&&results.stream().allMatch(c->c.projectedPercentage()!=null)){
   int order=results.get(0).projectedPercentage().compareTo(results.get(1).projectedPercentage());
   spread=new Spread(order==0?null:results.get(order>0?0:1).candidateNumber(),results.get(0).projectedPercentage().subtract(results.get(1).projectedPercentage()).abs());
  }
  ProjectionCoverage coverage=finalOfficial?ProjectionCoverage.FINAL:progress==null||progress.compareTo(MEDIUM_PROGRESS)<0?ProjectionCoverage.LOW:progress.compareTo(HIGH_PROGRESS)<0?ProjectionCoverage.MEDIUM:ProjectionCoverage.HIGH;
  return new ProjectionResponse("PROJECTION",MODEL,p.enabled(),status,publish,at,national==null?null:national.generatedAt(),generationKey(selected),progress,withData,27,complete,!complete,coverage,List.copyOf(missingBaseline),List.copyOf(missingOfficial),List.copyOf(results),spread,List.copyOf(states),List.copyOf(warnings),p,geography,overseas);
 }
 private static OverseasContribution overseas(OfficialElectionResult r,OfficialElectionResult national,boolean complete,boolean finalOfficial){
  if(r==null)return new OverseasContribution(false,false,null,List.of(),"Exterior sem arquivo observado: contribuição zero, sem extrapolação e sem afetar a cobertura das 27 UFs.");
  var pair=pair(r);
  if(national==null||!r.electionId().equals(national.electionId())||r.round()!=2||pair==null||pair.stream().anyMatch(c->c.votes()<0)||
   r.votes().totalVotes()<0||pair.stream().map(c->BigDecimal.valueOf(c.votes())).reduce(ZERO,BigDecimal::add).compareTo(BigDecimal.valueOf(r.votes().totalVotes()))>0)
   return new OverseasContribution(false,false,r.generatedAt(),List.of(),"Exterior incompatível: contribuição zero, sem alterar a cobertura das 27 UFs.");
  var observed=pair.stream().map(c->new ObservedCandidate(c.candidateNumber(),c.displayName(),c.votes())).toList();
  boolean hasVotes=observed.stream().anyMatch(c->c.votes()>0);
  boolean incorporated=hasVotes&&(complete||finalOfficial);
  String note=finalOfficial?"Resultado nacional final já inclui exterior: nenhuma adição em duplicidade.":
   incorporated?"Exterior: somente votos oficialmente observados incorporados; nenhum voto restante é projetado.":
   !hasVotes?"Exterior ainda sem votos observados: contribuição zero, sem extrapolação.":"Exterior com votos observados, aguardando cobertura das UFs para agregação; sem extrapolação.";
  return new OverseasContribution(true,incorporated,r.generatedAt(),observed,note);
 }
 public StateProjection state(String uf,OfficialElectionResult r,BaselineData baseline){
  List<String> warnings=new ArrayList<>();
  if(!validElectorate(r))return unavailable(uf,r,baseline,"Eleitorado totalizado ausente ou fora do domínio [0,total]");
  long total=r.electorate().total(),represented=r.electorate().totalized(),valid=r.votes().validVotes();
  var pair=pair(r);
  if(pair==null)return unavailable(uf,r,baseline,"Modelo exige exatamente os candidatos oficiais 13 e 22");
  long sum=Math.addExact(pair.get(0).votes(),pair.get(1).votes());
  if(valid<0||valid>represented||sum<0||sum>represented||(represented==0&&r.sections().totalized()>0))return unavailable(uf,r,baseline,"Contagens incompatíveis com o eleitorado representado");
  boolean completed=represented==total;
  if(!completed&&sum!=valid)return unavailable(uf,r,baseline,"Votos dos dois candidatos diferem dos válidos; não redistribuímos votos anulados/sub judice");
  BigDecimal progress=ratio(represented,total),weight=weight(represented,total,p.priorEquivalentProgress());
  BigDecimal observedShare=sum>0?ratio(pair.getFirst().votes(),sum):null;
  BigDecimal observedRate=represented>0?ratio(valid,represented):null;
  if(baseline==null&&!completed&&(progress.compareTo(p.minimumObservedProgressWithoutBaseline())<0||observedShare==null))return unavailable(uf,r,null,"Sem baseline e cobertura estadual insuficiente para fallback observado");
  if(baseline==null){weight=ONE;warnings.add("Baseline ausente: extrapolação apenas observada, sem shrinkage.");}
  BigDecimal priorShare=baseline==null?null:baseline.pairwiseShares().get(PAIR.getFirst());
  BigDecimal priorRate=baseline==null?null:baseline.validVotesPerElector();
  BigDecimal projectedRate,projectedValid,share;
  if(completed){projectedValid=BigDecimal.valueOf(valid);projectedRate=ratio(valid,total);share=observedShare;}
  else {
   projectedRate=baseline==null?observedRate:mix(observedRate==null?priorRate:observedRate,priorRate,weight);
   projectedValid=clamp(projectedRate.multiply(BigDecimal.valueOf(total),MC),BigDecimal.valueOf(valid),BigDecimal.valueOf(Math.addExact(valid,total-represented)));
   projectedRate=projectedValid.divide(BigDecimal.valueOf(total),MC);
   share=observedShare==null?priorShare:baseline==null?observedShare:mix(observedShare,priorShare,weight);
   if(projectedValid.signum()>0){
    BigDecimal bounded=clamp(share,BigDecimal.valueOf(pair.getFirst().votes()).divide(projectedValid,MC),ONE.subtract(BigDecimal.valueOf(pair.getLast().votes()).divide(projectedValid,MC)));
    if(bounded.compareTo(share)!=0)warnings.add("Share limitado para nunca projetar menos votos que os já computados.");share=bounded;
   }
  }
  if(completed&&sum!=valid)warnings.add("Encerramento estadual: preservados votos oficiais dos candidatos e válidos, mesmo com bases diferentes.");
  List<StateCandidate> candidates=new ArrayList<>();
  BigDecimal firstVotes=completed?BigDecimal.valueOf(pair.getFirst().votes()):projectedValid.multiply(share,MC);
  // Residual allocation keeps both candidate totals exactly equal to projected valid votes in ordinary partial data.
  for(int i=0;i<2;i++){
   var c=pair.get(i);BigDecimal s=share==null?null:i==0?share:ONE.subtract(share);
   candidates.add(new StateCandidate(c.candidateNumber(),c.displayName(),baseline==null?null:baseline.pairwiseShares().get(c.candidateNumber()),observedShare==null?null:i==0?observedShare:ONE.subtract(observedShare),s,completed?BigDecimal.valueOf(c.votes()):i==0?firstVotes:projectedValid.subtract(firstVotes)));
  }
  return new StateProjection(uf,true,r.generatedAt(),baseline==null?null:baseline.sourceGeneratedAt(),baseline==null?null:baseline.sourceUrl(),total,represented,progress,weight,priorRate,observedRate,projectedRate,projectedValid,List.copyOf(candidates),List.copyOf(warnings));
 }
 private static boolean validElectorate(OfficialElectionResult r){return r!=null&&r.electorate().total()!=null&&r.electorate().total()>0&&r.electorate().totalized()!=null&&r.electorate().totalized()>=0&&r.electorate().totalized()<=r.electorate().total();}
 private static List<OfficialElectionResult.CandidateResult> pair(OfficialElectionResult r){
  if(r.candidates().size()!=2)return null;
  var pair=PAIR.stream().map(n->r.candidates().stream().filter(c->n.equals(c.candidateNumber())).findFirst().orElse(null)).toList();return pair.contains(null)?null:pair;
 }
 private static StateProjection unavailable(String uf,OfficialElectionResult r,BaselineData b,String warning){return new StateProjection(uf,false,r==null?null:r.generatedAt(),b==null?null:b.sourceGeneratedAt(),b==null?null:b.sourceUrl(),r==null?null:r.electorate().total(),r==null?null:r.electorate().totalized(),null,null,b==null?null:b.validVotesPerElector(),null,null,null,List.of(),List.of(warning));}
 private static String generationKey(List<Recorded> records){try{String data=records.stream().sorted(Comparator.comparing(r->r.result().scope().type()+":"+r.result().scope().code())).map(r->r.id().toString()).collect(java.util.stream.Collectors.joining("|"));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
