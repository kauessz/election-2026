package br.com.election.projection;
import br.com.election.tse.OfficialElectionResult;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;

/** Immutable values supplied to the existing engine, not a new statistical model. */
public record ProjectionInputManifest(Instant asOf,List<OfficialInput> officialInputs,List<BaselineInput> baselinesSupplied,
    ProjectionProperties configuration) {
    public record OfficialInput(UUID snapshotId,String electionId,int round,OfficialElectionResult.Scope scope,Instant generatedAt,Instant capturedAt){}
    public record BaselineInput(String electionId,int round,OfficialElectionResult.Scope scope,Instant generatedAt,Instant capturedAt,
        String sourceUrl,long electorate,long validVotes,BigDecimal validVotesPerElector,Map<String,BigDecimal> pairwiseShares){}
    public static ProjectionInputManifest capture(Instant at,List<AnalyticsInputStore.Recorded> records,List<BaselineData> baselines,ProjectionProperties configuration){
        var official=ProjectionReplay.latestAt(records,at).stream().map(r->new OfficialInput(r.id(),r.result().electionId(),r.result().round(),r.result().scope(),r.result().generatedAt(),r.capturedAt())).toList();
        var supplied=baselines.stream().map(b->new BaselineInput(b.electionId(),b.round(),b.scope(),b.sourceGeneratedAt(),b.capturedAt(),b.sourceUrl(),b.electorate(),b.validVotes(),b.validVotesPerElector(),Map.copyOf(b.pairwiseShares()))).toList();
        return new ProjectionInputManifest(at,official,supplied,configuration);
    }
}
