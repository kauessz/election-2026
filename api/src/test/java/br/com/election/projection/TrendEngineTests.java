package br.com.election.projection;
import br.com.election.tse.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.projection.ProjectionFixtures.*;
import static br.com.election.tse.OfficialElectionResult.*;
class TrendEngineTests {
 final TrendEngine engine=new TrendEngine();
 AnalyticsInputStore.Recorded row(long minutes,long a,long b){return recorded(result(Scope.national(),2,100000,10000,a,b,TIME.minusSeconds(minutes*60),false));}
 @Test void irregularSnapshotsUseActualWindowsAndDeltaComposition(){
  var r=engine.calculate(List.of(row(30,0,0),row(20,100,200),row(14,200,400),row(4,400,500),row(1,460,540)),TIME);
  assertThat(r.windows()).extracting(TrendResponse.Window::minutes).containsExactly(5,15,30);
  var five=r.windows().getFirst();assertThat(five.available()).isTrue();assertThat(five.newValidVotes()).isEqualTo(100);assertThat(five.candidateShares().getFirst().percentage()).isEqualByComparingTo("60");assertThat(five.from()).isEqualTo(TIME.minusSeconds(240));assertThat(five.to()).isEqualTo(TIME.minusSeconds(60));
  assertThat(r.windows().get(1).newValidVotes()).isEqualTo(400);assertThat(r.windows().get(2).newValidVotes()).isEqualTo(1000);
 }
 @Test void absentOrSingleSnapshotDoesNotInventZero(){for(var rows:List.of(List.<AnalyticsInputStore.Recorded>of(),List.of(row(1,10,20))))assertThat(engine.calculate(rows,TIME).windows()).allSatisfy(w->{assertThat(w.available()).isFalse();assertThat(w.newValidVotes()).isNull();assertThat(w.candidateShares()).isEmpty();});}
 @Test void zeroAndNegativeDeltasAreDiscardedWithoutBridging(){var r=engine.calculate(List.of(row(4,100,200),row(3,100,200),row(2,90,200),row(1,100,220)),TIME).windows().getFirst();assertThat(r.newValidVotes()).isEqualTo(30);assertThat(r.usedIntervals()).isEqualTo(1);assertThat(r.excludedIntervals()).isEqualTo(2);}
 @Test void candidateRegressionEvenWithIncreasingValidVotesIsRejected(){var r=engine.calculate(List.of(row(4,100,200),row(1,90,230)),TIME).windows().getFirst();assertThat(r.available()).isFalse();}
 @Test void crossingWindowBoundaryIsNotInterpolated(){assertThat(engine.calculate(List.of(row(6,100,200),row(1,200,300)),TIME).windows().getFirst().available()).isFalse();}
 @Test void incompatibleElectionNeverGeneratesDelta(){var a=row(4,100,200);var b=result(Scope.national(),1,100000,10000,200,300,TIME.minusSeconds(60),true);assertThat(engine.calculate(List.of(a,recorded(b)),TIME).windows().getFirst().available()).isFalse();}
 @Test void futureReceiptIsExcluded(){var future=row(1,200,300);future=new AnalyticsInputStore.Recorded(future.id(),future.result(),TIME.plusSeconds(1));assertThat(engine.calculate(List.of(row(4,100,200),future),TIME).windows().getFirst().available()).isFalse();}
 @Test void duplicateGenerationCorrectionIsNotAnInterval(){var first=row(1,100,200);var second=row(1,200,300);assertThat(engine.calculate(List.of(first,second),TIME).windows().getFirst().available()).isFalse();}
 @Test void remainingUsesElectorateInsteadOfSectionCountAndKeepsMissingUnknown(){var r=new RemainingCalculator().calculate(frame(200,27),TIME);assertThat(r.statesAvailable()).isEqualTo(27);assertThat(r.states().getFirst().remainingElectorate()).isEqualTo(800);assertThat(r.states().getFirst().representedPercentage()).isEqualByComparingTo("20");var missing=new RemainingCalculator().calculate(List.of(),TIME);assertThat(missing.statesAvailable()).isZero();assertThat(missing.states().getFirst().remainingElectorate()).isNull();}
}
