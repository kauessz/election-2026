package br.com.election.tse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import tools.jackson.databind.json.JsonMapper;
import br.com.election.tse.exception.TseParsingException;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.tse.TseTestSupport.*;
class TseResultParserTests {
 private final TseResultParser parser=new TseResultParser(JsonMapper.builder().findAndAddModules().build(),properties("http://localhost",Duration.ofSeconds(1)));
 private String compact(){var json=JsonMapper.builder().findAndAddModules().build();return json.writeValueAsString(json.readTree(fixture("ea20-national-official.json")));}
 @Test void parsesRealPreCountingFile(){
  var r=parser.parse(fixture("ea20-national-official.json"),OfficialElectionResult.Scope.national());
  assertThat(r.electionId()).isEqualTo("6258");assertThat(r.round()).isEqualTo(2);
  assertThat(r.generatedAt()).isEqualTo(Instant.parse("2026-10-15T12:38:12Z"));
  assertThat(r.sections().total()).isEqualTo(499248);assertThat(r.sections().totalized()).isZero();
  assertThat(r.electorate().total()).isEqualTo(158745502);assertThat(r.votes().totalVotes()).isZero();
  assertThat(OfficialSnapshotStore.intrinsicStatus(r)).isEqualTo(OfficialDataStatus.NOT_STARTED);
  assertThat(r.candidates()).extracting(OfficialElectionResult.CandidateResult::candidateNumber).containsExactly("13","22");
  assertThat(r.candidates()).extracting(OfficialElectionResult.CandidateResult::displayName).containsExactly("LULA","FLAVIO BOLSONARO");
  assertThat(r.candidates().getFirst().candidateSequence()).isEqualTo("280002542548");
 }
 @Test void parsesSyntheticCountingWithoutChangingPercentages(){
  var r=parser.parse(fixture("ea20-progress-synthetic.json"),OfficialElectionResult.Scope.national());
  assertThat(r.candidates().getFirst().percentage()).isEqualByComparingTo("52.00");
  assertThat(r.candidates().getFirst().precisePercentage()).isEqualByComparingTo("52");
  assertThat(r.sections().percentageTotalized()).isEqualByComparingTo("50");
  assertThat(r.electorate().totalized()).isEqualTo(5000);assertThat(r.electorate().abstentions()).isEqualTo(3965);
  assertThat(r.votes().validVotes()).isEqualTo(1000);assertThat(r.votes().blankVotes()).isEqualTo(10);
  assertThat(r.votes().nullVotes()).isEqualTo(25); // tvn includes technical nulls; vn alone is 20.
  assertThat(r.votes().nominalVotes()).isEqualTo(1000);assertThat(r.votes().competingVotes()).isEqualTo(1000);
 }
 @Test void parsesRealTerritorialFiles(){
  assertThat(parser.parse(fixture("ea20-state-ac-official.json"),OfficialElectionResult.Scope.state(BrazilState.AC)).scope().code()).isEqualTo("AC");
  var scope=new OfficialElectionResult.Scope(ElectionScopeType.MUNICIPALITY,"AC:01120","Acrelândia");
  assertThat(parser.parse(fixture("ea20-municipality-ac01120-official.json"),scope).scope()).isEqualTo(scope);
 }
 @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"0","0,00","51,23","51.23"})
 void acceptsOptionalAndDecimalComma(String value){
  if(value==null||value.isEmpty())assertThat(TseNumbers.percentage(value,"test")).isNull();
  else assertThat(TseNumbers.percentage(value,"test")).isEqualByComparingTo(value.replace(',','.'));
 }
 @ParameterizedTest @ValueSource(strings={"abc","1.234,56","NaN","-1","101","1e2"})
 void rejectsMalformedPercentages(String value){assertThatThrownBy(()->TseNumbers.percentage(value,"cand.pvap")).isInstanceOf(TseParsingException.class).hasMessageContaining("cand.pvap");}
 @Test void rejectsMalformedOrOverflowingCounts(){
  assertThat(TseNumbers.count("", "x")).isNull();
  assertThatThrownBy(()->TseNumbers.count("99999999999999999999999","v.tv")).isInstanceOf(TseParsingException.class);
  assertThatThrownBy(()->TseNumbers.count("1,5","v.tv")).isInstanceOf(TseParsingException.class);
 }
 @Test void optionalFieldsRemainNull(){
  String body=compact().replaceAll("\"pvapn\":\"[^\"]*\",?","").replaceAll("\"snt\":\"[^\"]*\",?","");
  var r=parser.parse(body,OfficialElectionResult.Scope.national());assertThat(r.sections().notTotalized()).isNull();assertThat(r.candidates().getFirst().precisePercentage()).isNull();
 }
 @Test void invalidJsonAndMissingRequiredFieldsFailClearly(){
  assertThatThrownBy(()->parser.parse("{",OfficialElectionResult.Scope.national())).isInstanceOf(TseParsingException.class).hasMessageContaining("EA20");
  assertThatThrownBy(()->parser.parse(compact().replace("\"tv\":\"0\"","\"tv\":\"\""),OfficialElectionResult.Scope.national())).isInstanceOf(TseParsingException.class).hasMessageContaining("v.tv");
 }
 @Test void rejectsWrongElectionScopeAndSimulation(){
  String raw=compact();
  assertThatThrownBy(()->parser.parse(raw.replace("6258","6257"),OfficialElectionResult.Scope.national())).isInstanceOf(TseParsingException.class);
  assertThatThrownBy(()->parser.parse(raw,OfficialElectionResult.Scope.state(BrazilState.SP))).isInstanceOf(TseParsingException.class);
  assertThatThrownBy(()->parser.parse(raw.replace("\"f\":\"o\"","\"f\":\"s\""),OfficialElectionResult.Scope.national())).isInstanceOf(TseParsingException.class);
 }
 @Test void finalRequiresOfficialFlagNotPercentage(){
  String raw=fixture("ea20-progress-synthetic.json");
  assertThat(OfficialSnapshotStore.intrinsicStatus(parser.parse(raw.replace("50,00","100,00"),OfficialElectionResult.Scope.national()))).isEqualTo(OfficialDataStatus.FRESH);
  assertThat(OfficialSnapshotStore.intrinsicStatus(parser.parse(raw.replace("\"tf\": \"n\"","\"tf\": \"s\""),OfficialElectionResult.Scope.national()))).isEqualTo(OfficialDataStatus.FINAL);
 }
 @Test void buildsDocumentedUrlsAndPreservesLeadingZero(){
  var urls=new TseUrlBuilder(properties("http://localhost",Duration.ofSeconds(1)));
  var m=new MunicipalityCatalogue.Municipality("01120","Acrelândia",BrazilState.AC,java.util.List.of("0008"));
  assertThat(urls.national()).isEqualTo("/oficial/ele2026/6258/dados/br/br-c0001-e006258-u.json");
  assertThat(urls.state(BrazilState.AC)).endsWith("/ac/ac-c0001-e006258-u.json");
  assertThat(urls.municipality(m)).endsWith("/ac/ac01120-c0001-e006258-u.json");
  assertThat(urls.zone(m,"0008")).endsWith("/ac/ac01120-z0008-c0001-e006258-u.json");
  assertThatThrownBy(()->urls.zone(m,"9999")).isInstanceOf(IllegalArgumentException.class);
 }
}
