package br.com.election.projection;
import br.com.election.tse.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.projection.ProjectionFixtures.*;
import static br.com.election.tse.OfficialElectionResult.*;
class ConservativeProjectionTests {
 final ProjectionEngine engine=new ProjectionEngine(properties());
 final Set<BrazilState> fourRegionsThreeKeys=Set.of(BrazilState.AC,BrazilState.AM,BrazilState.PA,BrazilState.CE,BrazilState.PE,BrazilState.RN,BrazilState.DF,BrazilState.SP,BrazilState.MG,BrazilState.RJ);
 List<AnalyticsInputStore.Recorded> geographicFrame(Set<BrazilState> active){
  List<AnalyticsInputStore.Recorded> rows=new ArrayList<>();
  for(var uf:BrazilState.values()){boolean has=active.contains(uf);rows.add(recorded(result(Scope.state(uf),2,1000,has?500:0,has?200:0,has?100:0,TIME,false)));}
  rows.add(recorded(result(Scope.national(),2,27000,active.size()*500L,active.size()*200L,active.size()*100L,TIME,false)));return rows;
 }
 ProjectionResponse calculate(List<AnalyticsInputStore.Recorded> rows){return engine.calculate(rows,baselines(),TIME);}
 List<AnalyticsInputStore.Recorded> withOverseas(OfficialElectionResult r){var rows=new ArrayList<>(frame(500,27));rows.add(recorded(r));return rows;}
 @Test void regionMappingCoversAll27StatesExactly(){
  Map<BrazilRegion,Long> sizes=Arrays.stream(BrazilState.values()).collect(java.util.stream.Collectors.groupingBy(BrazilRegion::of,java.util.stream.Collectors.counting()));
  assertThat(sizes).containsEntry(BrazilRegion.NORTH,7L).containsEntry(BrazilRegion.NORTHEAST,9L).containsEntry(BrazilRegion.CENTRAL_WEST,4L).containsEntry(BrazilRegion.SOUTHEAST,4L).containsEntry(BrazilRegion.SOUTH,3L);
  assertThat(BrazilRegion.of(BrazilState.SP)).isEqualTo(BrazilRegion.SOUTHEAST);assertThat(BrazilRegion.of(BrazilState.BA)).isEqualTo(BrazilRegion.NORTHEAST);assertThat(BrazilRegion.of(BrazilState.PR)).isEqualTo(BrazilRegion.SOUTH);assertThat(BrazilRegion.of(BrazilState.RS)).isEqualTo(BrazilRegion.SOUTH);
 }
 @Test void threeRegionsFailDespiteEnoughStatesProgressAndKeyStates(){
  var active=Set.of(BrazilState.AC,BrazilState.AM,BrazilState.PA,BrazilState.RO,BrazilState.RR,BrazilState.AL,BrazilState.ES,BrazilState.SP,BrazilState.MG,BrazilState.RJ);
  var r=calculate(geographicFrame(active));assertThat(r.statesWithData()).isEqualTo(10);assertThat(r.geography().regionsWithData()).isEqualTo(3);assertThat(r.geography().keyStatesWithData()).isEqualTo(3);assertThat(r.status()).isEqualTo(ProjectionStatus.INSUFFICIENT_DATA);assertThat(r.publicationView(true).results()).isEmpty();
 }
 @Test void twoKeyStatesFailEvenAcrossAllFiveRegions(){
  var active=Set.of(BrazilState.AC,BrazilState.AP,BrazilState.AM,BrazilState.AL,BrazilState.CE,BrazilState.PB,BrazilState.DF,BrazilState.SP,BrazilState.MG,BrazilState.SC);
  var r=calculate(geographicFrame(active));assertThat(r.geography().regionsWithData()).isEqualTo(5);assertThat(r.geography().keyStatesWithData()).isEqualTo(2);assertThat(r.status()).isEqualTo(ProjectionStatus.INSUFFICIENT_DATA);
 }
 @Test void exactGeographicThresholdsAllowActive(){var r=calculate(geographicFrame(fourRegionsThreeKeys));assertThat(r.geography().regionsWithData()).isEqualTo(4);assertThat(r.geography().keyStatesWithData()).isEqualTo(3);assertThat(r.geography().thresholdsMet()).isTrue();assertThat(r.status()).isEqualTo(ProjectionStatus.ACTIVE);}
 @Test void highNationalCoverageDoesNotBypassGeographicGate(){
  var active=Set.of(BrazilState.AC,BrazilState.AP,BrazilState.AM,BrazilState.AL,BrazilState.CE,BrazilState.PB,BrazilState.DF,BrazilState.SP,BrazilState.MG,BrazilState.SC);
  var rows=geographicFrame(active);rows.removeLast();rows.add(recorded(result(Scope.national(),2,27000,24000,10000,6000,TIME,false)));assertThat(calculate(rows).status()).isEqualTo(ProjectionStatus.INSUFFICIENT_DATA);
 }
 @Test void geographicMinimumsAreConfigurable(){
  var p=properties();var relaxed=new ProjectionProperties(true,p.priorEquivalentProgress(),p.minimumNationalElectorateProgress(),p.minimumStatesWithData(),p.highCoverageProgress(),p.minimumObservedProgressWithoutBaseline(),3,2);
  var active=Set.of(BrazilState.AC,BrazilState.AP,BrazilState.AM,BrazilState.AL,BrazilState.CE,BrazilState.PB,BrazilState.DF,BrazilState.SP,BrazilState.MG,BrazilState.SC);
  assertThat(new ProjectionEngine(relaxed).calculate(geographicFrame(active),baselines(),TIME).status()).isEqualTo(ProjectionStatus.ACTIVE);
 }
 @Test void overseasWithoutObservedVotesContributesZeroAndDoesNotChangeCoverage(){
  var base=calculate(frame(500,27));var r=calculate(withOverseas(result(Scope.overseas(),2,1000,0,0,0,TIME,false)));
  assertThat(r.results()).isEqualTo(base.results());assertThat(r.overseas().available()).isTrue();assertThat(r.overseas().incorporated()).isFalse();assertThat(r.coverageComplete()).isTrue();assertThat(r.missingBaselineStates()).isEmpty();assertThat(r.statesWithData()).isEqualTo(27);assertThat(r.geography()).isEqualTo(base.geography());
 }
 @Test void absenceOfOverseasNeverReducesCoverageOf27States(){var r=calculate(frame(500,27));assertThat(r.overseas().available()).isFalse();assertThat(r.overseas().incorporated()).isFalse();assertThat(r.coverageComplete()).isTrue();assertThat(r.projectionIncomplete()).isFalse();assertThat(r.status()).isEqualTo(ProjectionStatus.ACTIVE);}
 @Test void onlyObservedOverseasVotesAreAddedWithoutExtrapolation(){
  var base=calculate(frame(500,27));var r=calculate(withOverseas(result(Scope.overseas(),2,10000,100,20,30,TIME,false)));
  assertThat(r.overseas().incorporated()).isTrue();assertThat(r.results().getFirst().projectedVotes()).isEqualByComparingTo(base.results().getFirst().projectedVotes().add(BigDecimal.valueOf(20)));assertThat(r.results().getLast().projectedVotes()).isEqualByComparingTo(base.results().getLast().projectedVotes().add(BigDecimal.valueOf(30)));
  var total=r.results().stream().map(ProjectionResponse.CandidateProjection::projectedVotes).reduce(BigDecimal.ZERO,BigDecimal::add);assertThat(r.results().getFirst().projectedPercentage()).isEqualByComparingTo(r.results().getFirst().projectedVotes().divide(total,ProjectionMath.MC).multiply(BigDecimal.valueOf(100),ProjectionMath.MC));assertThat(r.statesTotal()).isEqualTo(27);assertThat(r.states()).hasSize(27);
 }
 @Test void nationalFinalDoesNotDoubleAddOverseas(){
  var rows=withOverseas(result(Scope.overseas(),2,1000,100,20,30,TIME,false));rows.removeIf(r->r.result().scope().type()==ElectionScopeType.NATIONAL);rows.add(recorded(result(Scope.national(),2,28000,28000,12000,8000,TIME,true)));
  var r=calculate(rows);assertThat(r.status()).isEqualTo(ProjectionStatus.FINAL);assertThat(r.overseas().incorporated()).isTrue();assertThat(r.results().getFirst().projectedVotes()).isEqualByComparingTo("12000");assertThat(r.results().getLast().projectedVotes()).isEqualByComparingTo("8000");
 }
 @Test void overseasDoesNotCountAsRegionOrKeyState(){var rows=geographicFrame(Set.of(BrazilState.SP,BrazilState.MG,BrazilState.RJ));rows.add(recorded(result(Scope.overseas(),2,1000,100,20,30,TIME,false)));var r=calculate(rows);assertThat(r.statesWithData()).isEqualTo(3);assertThat(r.geography().regionsWithData()).isEqualTo(1);assertThat(r.geography().keyStatesWithData()).isEqualTo(3);assertThat(r.status()).isEqualTo(ProjectionStatus.INSUFFICIENT_DATA);}
 @Test void replayExcludesLateReceivedOverseasVotes(){
  var rows=new ArrayList<>(frame(500,27));var r=result(Scope.overseas(),2,1000,100,20,30,TIME.minusSeconds(1),false);rows.add(new AnalyticsInputStore.Recorded(UUID.randomUUID(),r,TIME.plusSeconds(1)));
  assertThat(calculate(rows).overseas().incorporated()).isFalse();
 }
 @Test void verifiedOverseasFixtureUsesExistingEa20ContractAndCentralUrl(){
  var p=br.com.election.tse.TseTestSupport.properties("http://localhost",Duration.ofSeconds(1));
  var parsed=new TseResultParser(JsonMapper.builder().findAndAddModules().build(),p).parse(br.com.election.tse.TseTestSupport.fixture("ea20-overseas-official.json"),Scope.overseas());
  assertThat(parsed.scope().code()).isEqualTo("ZZ");assertThat(parsed.votes().totalVotes()).isZero();assertThat(parsed.electorate().total()).isEqualTo(916683);assertThat(parsed.candidates()).extracting(CandidateResult::candidateNumber).containsExactly("13","22");
  assertThat(new TseUrlBuilder(p).overseas()).isEqualTo("/oficial/ele2026/6258/dados/zz/zz-c0001-e006258-u.json");
 }
}
