package br.com.election.projection;
import br.com.election.tse.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.projection.ProjectionFixtures.*;
import static br.com.election.tse.OfficialElectionResult.*;
import static br.com.election.projection.ProjectionMath.*;
class ProjectionEngineTests {
 final ProjectionEngine engine=new ProjectionEngine(properties());
 @ParameterizedTest @CsvSource({"0,0","50,.5","200,.8","500,.9090909090909090909090909090909091","1000,1"})
 void observationWeightAndExactEndpoints(long represented,String expected){assertThat(weight(represented,1000,new BigDecimal(".05"))).isEqualByComparingTo(expected);}
 @Test void weightStrictlyIncreases(){BigDecimal last=ZERO;for(int progress=1;progress<=1000;progress++){var next=weight(progress,1000,new BigDecimal(".05"));assertThat(next).isGreaterThan(last);last=next;}}
 @Test void zeroProgressEqualsBaselineExactly(){
  var b=baseline(BrazilState.AC,1000);var s=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,0,0,0,TIME,false),b);
  assertThat(s.projectedValidVotes()).isEqualByComparingTo("500");assertThat(s.candidates().getFirst().projectedShare()).isEqualByComparingTo(b.pairwiseShares().get("13"));assertThat(s.candidates().getFirst().projectedCandidateVotes()).isEqualByComparingTo("300");assertThat(s.observationWeight()).isZero();
 }
 @Test void partialShareAndTurnoutBlendUseSameWeight(){
  var s=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,50,10,10,TIME,false),baseline(BrazilState.AC,1000));
  assertThat(s.observationWeight()).isEqualByComparingTo(".5");assertThat(s.candidates().getFirst().observedShare()).isEqualByComparingTo(".5");assertThat(s.candidates().getFirst().projectedShare()).isEqualByComparingTo(".55");assertThat(s.projectedValidRate()).isEqualByComparingTo(".45");assertThat(s.projectedValidVotes()).isEqualByComparingTo("450");assertThat(s.candidates().getFirst().projectedCandidateVotes()).isEqualByComparingTo("247.5");
 }
 @ParameterizedTest @CsvSource({"0,0,0","50,10,10","200,60,80","500,220,130","999,400,10","1000,200,600"})
 void estimatesStayInsideMathematicalDomains(long represented,long a,long b){
  var s=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,represented,a,b,TIME,false),baseline(BrazilState.AC,1000));
  assertThat(s.available()).isTrue();assertThat(s.projectedValidVotes()).isBetween(BigDecimal.valueOf(a+b),BigDecimal.valueOf(a+b+1000-represented));
  assertThat(s.candidates().getFirst().projectedCandidateVotes()).isGreaterThanOrEqualTo(BigDecimal.valueOf(a));assertThat(s.candidates().getLast().projectedCandidateVotes()).isGreaterThanOrEqualTo(BigDecimal.valueOf(b));
  assertThat(s.candidates().stream().map(ProjectionResponse.StateCandidate::projectedCandidateVotes).reduce(ZERO,BigDecimal::add)).isEqualByComparingTo(s.projectedValidVotes());
 }
 @Test void fullStateExactlyUsesObservedCountsAndShare(){
  var s=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,1000,200,600,TIME,false),baseline(BrazilState.AC,1000));
  assertThat(s.observationWeight()).isEqualByComparingTo(ONE);assertThat(s.projectedValidVotes()).isEqualByComparingTo("800");assertThat(s.candidates().getFirst().projectedShare()).isEqualByComparingTo(".25");assertThat(s.candidates().getFirst().projectedCandidateVotes()).isEqualByComparingTo("200");assertThat(s.candidates().getLast().projectedCandidateVotes()).isEqualByComparingTo("600");
 }
 @Test void impossibleProgressIsUnavailableInsteadOfClampedIntoFalseOfficialData(){
  assertThat(engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,1001,400,200,TIME,false),baseline(BrazilState.AC,1000)).available()).isFalse();
 }
 @Test void noBaselineNeedsSufficientObservation(){
  assertThat(engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,100,40,20,TIME,false),null).available()).isFalse();
  var s=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,800,200,200,TIME,false),null);
  assertThat(s.available()).isTrue();assertThat(s.observationWeight()).isEqualByComparingTo(ONE);assertThat(s.projectedValidVotes()).isEqualByComparingTo("500");assertThat(s.warnings()).isNotEmpty();
 }
 @Test void missingBaselineIsDisclosedAndBlocksPublicationBelowFallbackThreshold(){
  var b=new ArrayList<>(baselines());b.removeFirst();var r=engine.calculate(frame(200,27),b,TIME);assertThat(r.status()).isEqualTo(ProjectionStatus.INSUFFICIENT_DATA);assertThat(r.missingBaselineStates()).contains("AC");assertThat(r.projectionIncomplete()).isTrue();assertThat(r.publicationView(true).results()).isEmpty();
 }
 @Test void nationalIsSumOfStatesAndNotPartialNationalShare(){
  var records=frame(200,27);var r=engine.calculate(records,baselines(),TIME);
  assertThat(r.status()).isEqualTo(ProjectionStatus.ACTIVE);
  for(var c:r.results())assertThat(c.projectedVotes()).isEqualByComparingTo(r.states().stream().flatMap(s->s.candidates().stream()).filter(s->s.candidateNumber().equals(c.candidateNumber())).map(ProjectionResponse.StateCandidate::projectedCandidateVotes).reduce(ZERO,BigDecimal::add));
  var changed=new ArrayList<>(records);changed.removeLast();changed.add(recorded(result(Scope.national(),2,27000,5400,100,4000,TIME,false)));
  assertThat(engine.calculate(changed,baselines(),TIME).results()).isEqualTo(r.results());
 }
 @Test void differentlySizedStatesContributeDifferentVoteVolumes(){
  var small=engine.state("AC",result(Scope.state(BrazilState.AC),2,1000,0,0,0,TIME,false),baseline(BrazilState.AC,1000));
  var large=engine.state("SP",result(Scope.state(BrazilState.SP),2,10000,0,0,0,TIME,false),baseline(BrazilState.SP,10000));
  assertThat(large.projectedValidVotes()).isEqualByComparingTo(small.projectedValidVotes().multiply(BigDecimal.TEN));
 }
 @Test void missingOfficialUfIsNotSilentlyIgnored(){var rows=new ArrayList<>(frame(200,27));rows.removeFirst();var r=engine.calculate(rows,baselines(),TIME);assertThat(r.missingOfficialStates()).contains("AC");assertThat(r.coverageComplete()).isFalse();assertThat(r.publishable()).isFalse();}
 @ParameterizedTest @CsvSource({"49,27,INSUFFICIENT_DATA","50,27,ACTIVE","51,27,ACTIVE","500,9,INSUFFICIENT_DATA","500,10,INSUFFICIENT_DATA","800,27,HIGH_COVERAGE"})
 void publicationThresholds(long progress,int states,String status){assertThat(engine.calculate(frame(progress,states),baselines(),TIME).status()).isEqualTo(ProjectionStatus.valueOf(status));}
 @Test void nationalFinalUsesExactOfficialIncludingExteriorWithoutWinnerCall(){
  var rows=new ArrayList<>(frame(1000,27));rows.removeLast();var official=result(Scope.national(),2,28000,28000,12000,8000,TIME,true);rows.add(recorded(official));var r=engine.calculate(rows,baselines(),TIME);
  assertThat(r.status()).isEqualTo(ProjectionStatus.FINAL);assertThat(r.coverage()).isEqualTo(ProjectionCoverage.FINAL);assertThat(r.results().getFirst().projectedVotes()).isEqualByComparingTo("12000");assertThat(r.results().getFirst().projectedPercentage()).isEqualByComparingTo(official.candidates().getFirst().percentage());assertThat(r.spread().leader()).isEqualTo("13");
 }
 @Test void highCoverageAloneNeverMeansFinal(){assertThat(engine.calculate(frame(1000,27),baselines(),TIME).status()).isEqualTo(ProjectionStatus.HIGH_COVERAGE);}
 @Test void disabledAndStaleDoNotPublish(){
  var p=properties();var disabled=new ProjectionEngine(new ProjectionProperties(false,p.priorEquivalentProgress(),p.minimumNationalElectorateProgress(),p.minimumStatesWithData(),p.highCoverageProgress(),p.minimumObservedProgressWithoutBaseline(),p.minimumRegionsWithData(),p.minimumKeyStatesWithData())).calculate(frame(500,27),baselines(),TIME).publicationView(true);
  assertThat(disabled.enabled()).isFalse();assertThat(disabled.results()).isEmpty();assertThat(engine.calculate(frame(500,27),baselines(),TIME).publicationView(false).publishable()).isFalse();
 }
 @Test void replayNeverUsesFutureOrLateReceivedSnapshots(){
  var now=frame(50,27);var future=frame(500,27).stream().map(r->new AnalyticsInputStore.Recorded(UUID.randomUUID(),r.result(),TIME.plusSeconds(10))).toList();var all=new ArrayList<>(now);all.addAll(future);
  var first=new ProjectionReplay(engine).replay(all,baselines(),List.of(TIME,TIME.plusSeconds(10)));
  assertThat(first.getFirst().results()).isEqualTo(engine.calculate(now,baselines(),TIME).results());assertThat(first.getLast().nationalElectorateProgress()).isGreaterThan(first.getFirst().nationalElectorateProgress());
 }
 @Test void replayDoesNotUseBaselineCapturedInTheFuture(){var late=baselines().stream().map(b->new BaselineData(b.electionId(),b.round(),b.scope(),b.electorate(),b.totalVotes(),b.validVotes(),b.blankVotes(),b.nullVotes(),b.validVotesPerElector(),b.pairwiseShares(),b.officialResult(),b.sourceGeneratedAt(),TIME.plusSeconds(1),b.sourceUrl())).toList();assertThat(new ProjectionReplay(engine).replay(frame(50,27),late,List.of(TIME)).getFirst().missingBaselineStates()).hasSize(27);}
}
