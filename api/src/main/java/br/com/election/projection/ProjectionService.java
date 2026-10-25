package br.com.election.projection;
import org.springframework.stereotype.Service;
import br.com.election.tse.*;
import org.slf4j.*;
import java.time.Clock;
import java.time.Instant;
@Service
public class ProjectionService {
 private static final Logger log=LoggerFactory.getLogger(ProjectionService.class);
 private final AnalyticsInputStore input;private final BaselineStore baseline;private final ProjectionEngine engine;
 private final ProjectionSnapshotStore snapshots;private final OfficialResultsService official;private final Clock clock;
 public ProjectionService(AnalyticsInputStore input,BaselineStore baseline,ProjectionEngine engine,ProjectionSnapshotStore snapshots,OfficialResultsService official,Clock clock){this.input=input;this.baseline=baseline;this.engine=engine;this.snapshots=snapshots;this.official=official;this.clock=clock;}
 public ProjectionResponse current(){
  return prepare().response();
 }
 private record Prepared(ProjectionResponse response,ProjectionInputManifest manifest){}
 private Prepared prepare(){
  Instant at=clock.instant();var status=official.getPresidentNationalResult().status();
  var records=input.latestAt(at);var baselines=baseline.at(at);
  var result=engine.calculate(records,baselines,at).publicationView(status==OfficialDataStatus.FRESH||status==OfficialDataStatus.FINAL||status==OfficialDataStatus.NOT_STARTED);
  return new Prepared(result,ProjectionInputManifest.capture(at,records,baselines,result.configuration()));
 }
 public void recordLatest(){var prepared=prepare();var result=prepared.response();if(snapshots.saveLive(result,prepared.manifest(),clock.instant()))log.info("projection changed modelVersion={} status={} coverage={} spread={}",result.model(),result.status(),result.nationalElectorateProgress(),result.spread()==null?null:result.spread().points());
  else log.debug("projection snapshot_skipped status={}",result.status());}
}
