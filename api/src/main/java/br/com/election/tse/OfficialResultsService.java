package br.com.election.tse;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import br.com.election.candidate.CandidateRepository;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static br.com.election.tse.OfficialElectionResult.*;
@Service
public class OfficialResultsService {
 private static final Logger log=LoggerFactory.getLogger(OfficialResultsService.class);
 private final TseResultsClient client;private final TseResultParser parser;private final TseUrlBuilder urls;
 private final OfficialSnapshotStore store;private final CandidateRepository candidates;private final MunicipalityCatalogue catalogue;
 private final TseResultsProperties p;private final Clock clock;
 private Instant nextCatalogueRefresh=Instant.EPOCH;
 private final ConcurrentMap<String,Current> current=new ConcurrentHashMap<>();
 private final ThreadPoolExecutor demand=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"tse-municipality");t.setDaemon(true);return t;});
 private static class Current {
  OfficialElectionResult result; Instant success,request,nextDemand=Instant.EPOCH; Integer http; int failures; boolean refreshing;
 }
 public OfficialResultsService(TseResultsClient client,TseResultParser parser,TseUrlBuilder urls,OfficialSnapshotStore store,CandidateRepository candidates,MunicipalityCatalogue catalogue,TseResultsProperties p,Clock clock){
  this.client=client;this.parser=parser;this.urls=urls;this.store=store;this.candidates=candidates;this.catalogue=catalogue;this.p=p;this.clock=clock;
 }
 private Current entry(Scope scope){return current.computeIfAbsent(scope.type()+":"+scope.code(),key->{
  Current c=new Current();var saved=store.latest(scope);if(saved!=null){c.result=saved.result();c.success=saved.capturedAt();}return c;
 });}
 public OfficialResultResponse getPresidentNationalResult(){return response(Scope.national());}
 public List<OfficialResultResponse> getStates(){return Arrays.stream(BrazilState.values()).map(uf->response(Scope.state(uf))).toList();}
 public OfficialResultResponse getState(BrazilState uf){return response(Scope.state(uf));}
 public OfficialResultResponse getMunicipality(BrazilState uf,String code){
  var m=catalogue.get(uf,code);Current c=entry(m.scope());
  synchronized(c){if(p.schedulingEnabled()&&!c.refreshing&&!clock.instant().isBefore(c.nextDemand)){
   c.refreshing=true;c.nextDemand=clock.instant().plus(p.municipalityInterval());
   try { demand.execute(()->{try{refresh(m.scope(),urls.municipality(m));}finally{synchronized(c){c.refreshing=false;}}}); }
   catch(RejectedExecutionException e){c.refreshing=false;}
  }}
  return response(m.scope());
 }
 public OfficialResultResponse response(Scope scope){Current c=entry(scope);synchronized(c){return new OfficialResultResponse("TSE",true,status(c),c.success,c.refreshing,scope,c.result);}}
 private OfficialDataStatus status(Current c){
  if(c.result==null)return OfficialDataStatus.UNAVAILABLE;
  // Source FINAL remains a factual property in result; availability still reports outages as STALE.
  boolean stale=c.success==null||c.success.plus(p.staleThreshold()).isBefore(clock.instant());
  if(OfficialSnapshotStore.intrinsicStatus(c.result)==OfficialDataStatus.FRESH)
   stale|=c.result.generatedAt().plus(p.staleThreshold()).isBefore(clock.instant());
  return stale?OfficialDataStatus.STALE:OfficialSnapshotStore.intrinsicStatus(c.result);
 }
 public void refreshOverseas(){refresh(Scope.overseas(),urls.overseas());}
 public void refreshNational(){refresh(Scope.national(),urls.national());}
 public void refreshState(BrazilState uf){refresh(Scope.state(uf),urls.state(uf));}
 void refresh(Scope scope,String path){
  Current c=entry(scope);
  synchronized(c){
   var fetched=client.fetch(path);if(!fetched.attempted())return;
   c.request=fetched.requestedAt();c.http=fetched.status()==0?null:fetched.status();
   try {
    if(fetched.status()==304){
     if(c.result==null)throw new IllegalStateException("TSE retornou 304 sem resultado em cache");
     c.success=clock.instant();c.failures=0;return;
    }
    if(fetched.status()!=200){c.failures++;return;}
    OfficialElectionResult parsed=parser.parse(fetched.body(),scope);
    if(parsed.generatedAt().isAfter(clock.instant().plusSeconds(300)))throw new IllegalArgumentException("EA20: geração no futuro");
    if(c.result!=null&&parsed.generatedAt().isBefore(c.result.generatedAt()))throw new IllegalArgumentException("EA20: geração anterior ao último dado válido");
    var mapped=mapCandidates(parsed);
    boolean changed=c.result==null||!store.hash(c.result).equals(store.hash(mapped));
    if(changed){
     store.save(mapped,clock.instant(),fetched.etag());
     log.info("tse result_changed scope={} generatedAt={} sectionsPercentage={} totalVotes={}",scope.code(),mapped.generatedAt(),mapped.sections().percentageTotalized(),mapped.votes().totalVotes());
    }
    c.result=mapped;c.success=clock.instant();c.failures=0;client.accept(path,fetched);
   }catch(Exception e){c.failures++;client.invalid(path);log.warn("tse rejected_result scope={} reason={}",scope.code(),e.getMessage());}
  }
 }
 OfficialElectionResult mapCandidates(OfficialElectionResult r){
  var local=candidates.findAll();List<String> warnings=new ArrayList<>();
  var mapped=r.candidates().stream().map(c->{
   var matches=local.stream().filter(l->String.valueOf(l.getBallotNumber()).equals(c.candidateNumber())).toList();
   UUID id=matches.size()==1?matches.getFirst().getId():null;
   if(id==null){String warning="Candidato oficial "+c.candidateNumber()+" sem correspondência local única por número eleitoral";warnings.add(warning);log.warn("tse candidate_mapping number={} matches={}",c.candidateNumber(),matches.size());}
   return new CandidateResult(c.candidateNumber(),c.candidateSequence(),c.name(),c.displayName(),c.votes(),c.percentage(),c.precisePercentage(),id);
  }).toList();
  return new OfficialElectionResult(r.electionId(),r.round(),r.scope(),r.generatedAt(),r.generationId(),r.finalTotalization(),r.sections(),r.electorate(),r.votes(),mapped,List.copyOf(warnings));
 }
 public synchronized void refreshCatalogue(){
  if(clock.instant().isBefore(nextCatalogueRefresh))return;
  String path=urls.municipalities();var fetched=client.fetch(path);
  if(fetched.status()==304)nextCatalogueRefresh=clock.instant().plus(Duration.ofDays(1));
  if(fetched.status()==200)try{catalogue.accept(fetched.body());client.accept(path,fetched);nextCatalogueRefresh=clock.instant().plus(Duration.ofDays(1));}catch(Exception e){client.invalid(path);log.warn("tse catalogue_rejected reason={}",e.getMessage());}
 }
 public record SourceStatus(String source,String baseUrl,Instant lastRequestAt,Instant lastSuccessfulUpdate,Integer lastHttpStatus,OfficialDataStatus currentDataStatus,int consecutiveFailures,TseResultsClient.Metrics metrics){}
 public SourceStatus sourceStatus(){Current c=entry(Scope.national());synchronized(c){return new SourceStatus("TSE",p.baseUrl(),c.request,c.success,c.http,status(c),c.failures,client.metrics());}}
 @PreDestroy public void close(){demand.shutdownNow();}
}
