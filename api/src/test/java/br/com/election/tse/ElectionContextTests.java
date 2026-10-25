package br.com.election.tse;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.tse.ElectionContextService.*;
import static br.com.election.projection.ProjectionFixtures.*;

class ElectionContextTests {
    OfficialResultResponse response(OfficialDataStatus status){return new OfficialResultResponse("TSE",true,status,TIME,false,OfficialElectionResult.Scope.national(),null);}
    @Test void noPersistedEvidenceIsUnknownEvenIfUnavailable(){assertThat(derive(new Evidence(false,null,null,null),response(OfficialDataStatus.UNAVAILABLE),TIME).phase()).isEqualTo(Phase.UNKNOWN);}
    @Test void nationalZeroEvidenceMeansNotStarted(){assertThat(derive(new Evidence(true,null,null,TIME),response(OfficialDataStatus.NOT_STARTED),TIME).phase()).isEqualTo(Phase.NOT_STARTED);}
    @Test void countingSurvivesStaleAndUnavailable(){for(var status:new OfficialDataStatus[]{OfficialDataStatus.STALE,OfficialDataStatus.UNAVAILABLE}){
        var context=derive(new Evidence(true,TIME,null,TIME),response(status),TIME.plusSeconds(10));
        assertThat(context.phase()).isEqualTo(Phase.COUNTING);assertThat(context.sourceFresh()).isFalse();assertThat(context.warnings()).isNotEmpty();
        assertThat(context.stale()).isEqualTo(status==OfficialDataStatus.STALE);assertThat(context.countingFirstObservedAt()).isEqualTo(TIME);
    }}
    @Test void onlyPersistedNationalFinalEvidenceFinishesElection(){assertThat(derive(new Evidence(true,TIME,TIME.plusSeconds(10),TIME),response(OfficialDataStatus.UNAVAILABLE),TIME.plusSeconds(20)).phase()).isEqualTo(Phase.FINISHED);}
    @Test void availabilityAndGenerationRemainSeparateFromPhase(){
        var r=result(OfficialElectionResult.Scope.national(),2,1000,1000,400,300,TIME,true);
        var context=derive(new Evidence(true,TIME,TIME,TIME),new OfficialResultResponse("TSE",true,OfficialDataStatus.STALE,TIME,false,r.scope(),r),TIME.plusSeconds(100));
        assertThat(context.phase()).isEqualTo(Phase.FINISHED);assertThat(context.dataAvailable()).isTrue();assertThat(context.stale()).isTrue();
        assertThat(context.officialGeneratedAt()).isEqualTo(TIME);assertThat(context.lastSuccessfulUpdate()).isEqualTo(TIME);assertThat(context.evaluatedAt()).isEqualTo(TIME.plusSeconds(100));
    }
}
