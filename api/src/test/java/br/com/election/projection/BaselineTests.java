package br.com.election.projection;
import org.junit.jupiter.api.Test;
import br.com.election.tse.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.projection.ProjectionFixtures.*;
import static br.com.election.tse.TseTestSupport.*;
import static br.com.election.tse.OfficialElectionResult.*;
class BaselineTests {
 @Test void realNationalPairwiseAndValidRateKeepAllOfficialCandidates(){
  var parser=new TseResultParser(JsonMapper.builder().findAndAddModules().build(),properties("http://localhost",Duration.ofSeconds(1)));
  var official=parser.parse(fixture("ea20-first-round-national-official.json"),Scope.national(),"6257",1);
  var baseline=BaselineData.from(official,TIME,"https://resultados.tse.jus.br/test-source");
  assertThat(baseline.officialResult().candidates()).hasSize(12);assertThat(baseline.officialResult()).isSameAs(official);
  assertThat(baseline.pairwiseShares().get("22")).isEqualByComparingTo(ProjectionMath.ratio(56104503,109984041));
  assertThat(baseline.pairwiseShares().get("13").add(baseline.pairwiseShares().get("22"))).isEqualByComparingTo(BigDecimal.ONE);
  assertThat(baseline.validVotesPerElector()).isEqualByComparingTo(ProjectionMath.ratio(119300788,158745502));
 }
 @Test void rejectsSecondRoundAndNonfinalBaseline(){
  assertThatThrownBy(()->BaselineData.from(result(Scope.national(),2,1000,1000,200,300,TIME,true),TIME,"source")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->BaselineData.from(result(Scope.national(),1,1000,1000,200,300,TIME,false),TIME,"source")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void rejectsMissingPairAndZeroValidVotes(){
  var r=result(Scope.national(),1,1000,1000,0,0,TIME,true);assertThatThrownBy(()->BaselineData.from(r,TIME,"source")).isInstanceOf(IllegalArgumentException.class);
  var valid=result(Scope.national(),1,1000,1000,200,300,TIME,true);
  var missing=new OfficialElectionResult(valid.electionId(),valid.round(),valid.scope(),valid.generatedAt(),valid.generationId(),true,valid.sections(),valid.electorate(),valid.votes(),valid.candidates().subList(0,1),java.util.List.of());
  assertThatThrownBy(()->BaselineData.from(missing,TIME,"source")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("22");
 }
}
