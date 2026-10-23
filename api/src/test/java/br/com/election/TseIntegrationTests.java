package br.com.election;
import br.com.election.tse.*;
import br.com.election.candidate.CandidateRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static br.com.election.tse.TseTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class TseIntegrationTests {
 @Autowired JdbcTemplate jdbc;@Autowired JsonMapper json;@Autowired CandidateRepository candidates;
 @Autowired OfficialSnapshotStore snapshots;@Autowired MunicipalityCatalogue catalogue;@Autowired MockMvc mvc;
 @Autowired TseResultsProperties configured;@Autowired TseResultsClient configuredClient;
 HttpServer server;OfficialResultsService service;TseResultsClient client;TseResultsProperties p;MutableClock clock;
 AtomicReference<String> body=new AtomicReference<>();AtomicInteger status=new AtomicInteger(200),hits=new AtomicInteger();
 @BeforeEach void setup() throws Exception {
  jdbc.update("DELETE FROM election_result_snapshots");jdbc.update("DELETE FROM election_municipalities");
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",e->{hits.incrementAndGet();byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().add("ETag","\"fixture\"");e.sendResponseHeaders(status.get(),status.get()==304?-1:bytes.length);if(status.get()!=304)e.getResponseBody().write(bytes);e.close();});server.start();
  p=properties("http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofSeconds(2));clock=new MutableClock(Instant.parse("2026-10-23T13:00:01Z"));client=new TseResultsClient(p,clock);service=newService(client);body.set(fixture("ea20-progress-synthetic.json"));
 }
 private OfficialResultsService newService(TseResultsClient c){return new OfficialResultsService(c,new TseResultParser(json,p),new TseUrlBuilder(p),snapshots,candidates,catalogue,p,clock);}
 @AfterEach void stop(){service.close();server.stop(0);}
 private long count(){return jdbc.queryForObject("SELECT count(*) FROM election_result_snapshots",Long.class);}
 @Test void schedulingIsDisabledAndCachedEndpointsNeverCallExternalSource() throws Exception {
  assertThat(configured.schedulingEnabled()).isFalse();long requests=configuredClient.metrics().requests();
  mvc.perform(get("/api/election/president")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNAVAILABLE")).andExpect(jsonPath("$.result").isEmpty());
  mvc.perform(get("/api/election/president/states")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(27));
  mvc.perform(get("/api/election/source-status")).andExpect(status().isOk()).andExpect(jsonPath("$.source").value("TSE"));
  assertThat(configuredClient.metrics().requests()).isEqualTo(requests);
 }
 @Test void snapshotDeduplicationChangesAndChronologicalHistory(){
  service.refreshNational();assertThat(count()).isEqualTo(1);
  clock.advance(Duration.ofSeconds(5));service.refreshNational();assertThat(count()).isEqualTo(1);
  body.set(body.get().replace("10:00:00","10:00:02"));service.refreshNational();assertThat(count()).isEqualTo(1); // Generation alone is not a factual change.
  body.set(body.get().replace("\"520\"","\"521\"").replace("10:00:02","10:00:03"));service.refreshNational();assertThat(count()).isEqualTo(2);
  var history=snapshots.history(Instant.parse("2026-01-01T00:00:00Z"),clock.instant(),500);
  assertThat(history).hasSize(2);assertThat(history.getFirst().generatedAt()).isBefore(history.getLast().generatedAt());
  assertThat(snapshots.history(Instant.parse("2026-01-01T00:00:00Z"),clock.instant(),1)).hasSize(1);
  assertThat(snapshots.latest(OfficialElectionResult.Scope.national()).result().candidates().getFirst().votes()).isEqualTo(521);
 }
 @Test void databaseConstraintPreventsSameGenerationDuplicate(){
  var r=new TseResultParser(json,p).parse(body.get(),OfficialElectionResult.Scope.national());
  assertThat(snapshots.save(r,clock.instant(),"etag")).isTrue();assertThat(snapshots.save(r,clock.instant(),"etag")).isFalse();assertThat(count()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT jsonb_typeof(candidate_results) FROM election_result_snapshots",String.class)).isEqualTo("array");
 }
 @Test void preservesLastGoodResultAcross500AndRestart(){
  service.refreshNational();var original=service.getPresidentNationalResult();
  status.set(500);clock.advance(Duration.ofMinutes(3));service.refreshNational();
  var stale=service.getPresidentNationalResult();assertThat(stale.status()).isEqualTo(OfficialDataStatus.STALE);assertThat(stale.result()).isEqualTo(original.result());assertThat(stale.lastSuccessfulUpdate()).isEqualTo(original.lastSuccessfulUpdate());
  var recovered=newService(new TseResultsClient(p,clock));
  try{assertThat(recovered.getPresidentNationalResult().status()).isEqualTo(OfficialDataStatus.STALE);assertThat(recovered.getPresidentNationalResult().result()).isEqualTo(original.result());assertThat(count()).isEqualTo(1);}finally{recovered.close();}
 }
 @Test void notModifiedIsSuccessfulWithoutSnapshotOrGenerationChange(){
  service.refreshNational();var before=service.getPresidentNationalResult();status.set(304);clock.advance(Duration.ofSeconds(5));service.refreshNational();var after=service.getPresidentNationalResult();
  assertThat(after.result()).isSameAs(before.result());assertThat(after.lastSuccessfulUpdate()).isAfter(before.lastSuccessfulUpdate());assertThat(count()).isEqualTo(1);assertThat(service.sourceStatus().consecutiveFailures()).isZero();
 }
 @Test void unchanged304CannotDisguiseOldPartialGeneration(){service.refreshNational();status.set(304);clock.advance(Duration.ofMinutes(3));service.refreshNational();assertThat(service.getPresidentNationalResult().status()).isEqualTo(OfficialDataStatus.STALE);}
 @Test void malformedOrOlderFileDoesNotOverwriteGoodData(){
  service.refreshNational();var before=service.getPresidentNationalResult().result();
  body.set("{");service.refreshNational();assertThat(service.getPresidentNationalResult().result()).isSameAs(before);assertThat(count()).isEqualTo(1);
  clock.advance(Duration.ofSeconds(6));body.set(fixture("ea20-progress-synthetic.json").replace("10:00:00","09:59:59"));service.refreshNational();assertThat(service.getPresidentNationalResult().result()).isSameAs(before);assertThat(service.sourceStatus().consecutiveFailures()).isEqualTo(2);
 }
 @Test void catalogueRefreshIsDailyAnd304DoesNotReparse(){
  body.set(fixture("ea12-municipalities-official.json"));service.refreshCatalogue();assertThat(hits).hasValue(1);
  service.refreshCatalogue();assertThat(hits).hasValue(1);
  clock.advance(Duration.ofDays(1));status.set(304);service.refreshCatalogue();assertThat(hits).hasValue(2);
  service.refreshCatalogue();assertThat(hits).hasValue(2);assertThat(catalogue.list(BrazilState.AC)).isNotEmpty();
 }
 @Test void absentDataFailureDoesNotBecomeZeroResult(){status.set(404);service.refreshNational();assertThat(service.getPresidentNationalResult().status()).isEqualTo(OfficialDataStatus.UNAVAILABLE);assertThat(service.getPresidentNationalResult().result()).isNull();assertThat(count()).isZero();}
 @Test void unknownCandidateKeepsOfficialValuesAndWarns(){
  body.set(body.get().replace("\"n\": \"22\"","\"n\": \"99\""));service.refreshNational();var r=service.getPresidentNationalResult().result();
  var unknown=r.candidates().stream().filter(c->c.candidateNumber().equals("99")).findFirst().orElseThrow();assertThat(unknown.localCandidateId()).isNull();assertThat(unknown.displayName()).isEqualTo("FLAVIO BOLSONARO");assertThat(unknown.votes()).isEqualTo(480);assertThat(r.warnings()).hasSize(1);
 }
 @Test void mapsByNumberAndNeverByName(){body.set(body.get().replace("LULA","NOME OFICIAL DIFERENTE"));service.refreshNational();var c=service.getPresidentNationalResult().result().candidates().getFirst();assertThat(c.localCandidateId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));assertThat(c.displayName()).isEqualTo("NOME OFICIAL DIFERENTE");}
 @Test void catalogueUsesRealCodesAndRejectsUnknownWithoutFetching() throws Exception {
  catalogue.accept(fixture("ea12-municipalities-official.json"));var acre=catalogue.list(BrazilState.AC);assertThat(acre).extracting(MunicipalityCatalogue.Municipality::code).contains("01120");assertThat(catalogue.all().stream().map(MunicipalityCatalogue.Municipality::uf).distinct().count()).isEqualTo(27);
  assertThatThrownBy(()->service.getMunicipality(BrazilState.AC,"99999")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(hits).hasValue(0);
  mvc.perform(get("/api/election/president/states/ac/municipalities")).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("01120"));
  mvc.perform(get("/api/election/president/states/ac/municipalities/01120")).andExpect(status().isOk()).andExpect(jsonPath("$.scope.code").value("AC:01120"));
  mvc.perform(get("/api/election/president/states/xx")).andExpect(status().isBadRequest());mvc.perform(get("/api/election/president/history?limit=2001")).andExpect(status().isBadRequest());
 }
 @Test void catalogueRejectsIncompleteFileAtomically(){catalogue.accept(fixture("ea12-municipalities-official.json"));var before=catalogue.list(BrazilState.AC);assertThatThrownBy(()->catalogue.accept("{\"abr\":[]}")).isInstanceOf(IllegalArgumentException.class);assertThat(catalogue.list(BrazilState.AC)).isEqualTo(before);}
 @Test void stateAndMunicipalityHaveSeparateSnapshotScopes(){
  body.set(fixture("ea20-state-ac-official.json"));service.refreshState(BrazilState.AC);assertThat(service.getState(BrazilState.AC).status()).isEqualTo(OfficialDataStatus.NOT_STARTED);
  var m=new MunicipalityCatalogue.Municipality("01120","Acrelândia",BrazilState.AC,List.of("0008"));
  var r=new TseResultParser(json,p).parse(fixture("ea20-municipality-ac01120-official.json"),m.scope());snapshots.save(r,clock.instant(),null);
  assertThat(snapshots.latest(m.scope()).result().scope().code()).isEqualTo("AC:01120");assertThat(count()).isEqualTo(2);assertThat(service.getPresidentNationalResult().result()).isNull();
 }
}
