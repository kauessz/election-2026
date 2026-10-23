package br.com.election.projection;
import br.com.election.tse.*;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import java.time.Clock;
import java.util.*;
@Service
public class BaselineSyncService {
 private static final Logger log=LoggerFactory.getLogger(BaselineSyncService.class);
 private final TseResultsClient client;private final TseResultParser parser;private final TseUrlBuilder urls;
 private final BaselineStore store;private final TseResultsProperties properties;private final Clock clock;
 public record ScopeFailure(String scope,String error) {}
 public record SyncSummary(int successCount,int failureCount,List<String> successfulScopes,List<ScopeFailure> errors) {}
 public BaselineSyncService(TseResultsClient client,TseResultParser parser,TseUrlBuilder urls,BaselineStore store,TseResultsProperties properties,Clock clock){this.client=client;this.parser=parser;this.urls=urls;this.store=store;this.properties=properties;this.clock=clock;}
 public synchronized SyncSummary sync(){
  List<OfficialElectionResult.Scope> scopes=new ArrayList<>();scopes.add(OfficialElectionResult.Scope.national());
  Arrays.stream(BrazilState.values()).map(OfficialElectionResult.Scope::state).forEach(scopes::add);
  Set<String> stored=new HashSet<>();store.at(clock.instant()).forEach(b->stored.add(b.scope().code()));
  List<String> successes=new ArrayList<>();List<ScopeFailure> errors=new ArrayList<>();
  for(var scope:scopes){
   String path=urls.baseline(scope,BaselineData.FIRST_ROUND_ELECTION);var f=client.fetch(path);
   try {
    if(f.status()==304&&stored.contains(scope.code())){successes.add(scope.code());continue;}
    if(f.status()!=200)throw new IllegalStateException(f.attempted()?"TSE HTTP "+f.status():"Consulta adiada por backoff da fonte");
    var result=parser.parse(f.body(),scope,BaselineData.FIRST_ROUND_ELECTION,1);
    if(result.generatedAt().isAfter(clock.instant()))throw new IllegalArgumentException("Baseline com geração futura");
    store.upsert(BaselineData.from(result,clock.instant(),properties.baseUrl()+path));client.accept(path,f);successes.add(scope.code());
   }catch(Exception e){if(f.status()==200)client.invalid(path);errors.add(new ScopeFailure(scope.code(),e.getMessage()));log.warn("baseline sync_failed scope={} reason={}",scope.code(),e.getMessage());}
  }
  log.info("baseline sync_finished successCount={} failureCount={}",successes.size(),errors.size());
  return new SyncSummary(successes.size(),errors.size(),List.copyOf(successes),List.copyOf(errors));
 }
}
