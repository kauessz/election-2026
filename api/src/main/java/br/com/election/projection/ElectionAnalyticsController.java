package br.com.election.projection;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.*;
@RestController
public class ElectionAnalyticsController {
 private final BaselineSyncService sync;private final BaselineStore baseline;private final ProjectionService projection;private final ProjectionSnapshotStore snapshots;
 private final AnalyticsInputStore input;private final TrendEngine trend;private final RemainingCalculator remaining;private final ProjectionProperties p;private final Clock clock;
 public ElectionAnalyticsController(BaselineSyncService sync,BaselineStore baseline,ProjectionService projection,ProjectionSnapshotStore snapshots,AnalyticsInputStore input,TrendEngine trend,RemainingCalculator remaining,ProjectionProperties p,Clock clock){this.sync=sync;this.baseline=baseline;this.projection=projection;this.snapshots=snapshots;this.input=input;this.trend=trend;this.remaining=remaining;this.p=p;this.clock=clock;}
 @PostMapping("/api/admin/election-baselines/first-round/sync") public BaselineSyncService.SyncSummary sync(){return sync.sync();}
 @GetMapping("/api/admin/election-baselines/first-round") public List<BaselineData> baselines(){return baseline.at(clock.instant());}
 @GetMapping("/api/election/president/projection") public ProjectionResponse projection(){return projection.current();}
 @GetMapping("/api/election/president/trend") public TrendResponse trend(){var at=clock.instant();return trend.calculate(input.trendAt(at),at);}
 @GetMapping("/api/election/president/remaining") public RemainingResponse remaining(){var at=clock.instant();return remaining.calculate(input.latestAt(at),at);}
 @GetMapping("/api/election/president/projection/history/{snapshotId}/inputs") public ProjectionSnapshotStore.Inputs inputs(@PathVariable UUID snapshotId){return snapshots.inputs(snapshotId);}
 @GetMapping("/api/election/president/projection/history") public List<tools.jackson.databind.JsonNode> history(
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,@RequestParam(defaultValue="500") int limit,
  @RequestParam(required=false) String modelVersion,@RequestParam(required=false) ProjectionSnapshotStore.Origin origin){
  if(!p.enabled())return List.of();return snapshots.historyView(from==null?Instant.parse("2026-01-01T00:00:00Z"):from,to==null?clock.instant():to,limit,modelVersion,origin);
 }
}
