package br.com.election;
import br.com.election.projection.*;
import br.com.election.tse.*;
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
import static org.assertj.core.api.Assertions.*;
import static br.com.election.projection.ProjectionFixtures.*;
import static br.com.election.tse.TseTestSupport.fixture;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)
class ProjectionIntegrationTests {
 @Autowired BaselineStore baseline;@Autowired OfficialSnapshotStore official;@Autowired AnalyticsInputStore input;
 @Autowired ProjectionSnapshotStore projections;@Autowired JsonMapper json;@Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
 @Autowired TseResultsProperties properties;@Autowired TseResultsClient externalClient;
 @BeforeEach void clean(){jdbc.update("DELETE FROM election_baselines");jdbc.update("DELETE FROM projection_snapshots");jdbc.update("DELETE FROM election_result_snapshots");}
 @Test void baselineUpsertAndOfflineReadPreserveOfficialPayload(){
  var b=baseline(BrazilState.AC,1000);baseline.upsert(b);baseline.upsert(b);
  assertThat(baseline.at(TIME)).containsExactly(b);assertThat(jdbc.queryForObject("SELECT count(*) FROM election_baselines",Integer.class)).isEqualTo(1);
  var older=BaselineData.from(ProjectionFixtures.result(OfficialElectionResult.Scope.state(BrazilState.AC),1,1000,1000,300,200,TIME.minusSeconds(90000),true),TIME,"older");
  assertThatThrownBy(()->baseline.upsert(older)).hasMessageContaining("anterior");assertThat(baseline.at(TIME)).containsExactly(b);
 }
 @Test void snapshotsPersistOnlyPublishableAndDeduplicateOfficialGenerationVector(){
  var engine=new ProjectionEngine(ProjectionFixtures.properties());var rows=frame(200,27);var projection=engine.calculate(rows,baselines(),TIME);
  assertThat(projections.save(projection)).isTrue();assertThat(projections.save(engine.calculate(rows,baselines(),TIME.plusSeconds(5)))).isFalse();
  assertThat(projections.save(engine.calculate(frame(1,27),baselines(),TIME))).isFalse();
  var changed=new ArrayList<>(rows);changed.set(0,recorded(ProjectionFixtures.result(OfficialElectionResult.Scope.state(BrazilState.AC),2,1000,300,100,80,TIME.plusSeconds(1),false)));
  assertThat(projections.save(engine.calculate(changed,baselines(),TIME.plusSeconds(2)))).isTrue();
  var history=projections.history(TIME.minusSeconds(1),TIME.plusSeconds(10),100);assertThat(history).hasSize(2);assertThat(history.getFirst().calculatedAt()).isBefore(history.getLast().calculatedAt());
 }
 @Test void batchedLatestReadAndTrendExcludeFutureCaptureAndFutureGeneration(){
  for(var row:frame(200,27))official.save(row.result(),row.capturedAt(),null);
  assertThat(input.latestAt(TIME)).hasSize(28);
  var late=ProjectionFixtures.result(OfficialElectionResult.Scope.state(BrazilState.AC),2,1000,500,200,100,TIME.minusSeconds(1),false);official.save(late,TIME.plusSeconds(5),null);
  assertThat(input.latestAt(TIME).stream().filter(r->r.result().scope().code().equals("AC")).findFirst().orElseThrow().result().electorate().totalized()).isEqualTo(200);
  var future=ProjectionFixtures.result(OfficialElectionResult.Scope.national(),2,27000,27000,8000,9000,TIME.plusSeconds(10),false);official.save(future,TIME,null);
  assertThat(input.trendAt(TIME)).hasSize(1);
 }
 @Test void baselineFutureCaptureIsNotUsedByReplayRead(){var b=baseline(BrazilState.AC,1000);baseline.upsert(b);assertThat(baseline.at(b.capturedAt().minusSeconds(1))).isEmpty();}
 @Test void analyticalGetsNeverSyncOrCallTseAndDoNotPublishBeforeThreshold() throws Exception {
  long before=externalClient.metrics().requests();
  mvc.perform(get("/api/election/president/projection")).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("PROJECTION")).andExpect(jsonPath("$.publishable").value(false)).andExpect(jsonPath("$.results").isEmpty());
  mvc.perform(get("/api/election/president/trend")).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("TREND")).andExpect(jsonPath("$.windows[0].available").value(false));
  mvc.perform(get("/api/election/president/remaining")).andExpect(status().isOk()).andExpect(jsonPath("$.states.length()").value(27));
  mvc.perform(get("/api/election/president/projection/history")).andExpect(status().isOk());
  mvc.perform(get("/api/election/president/projection/history?limit=3000")).andExpect(status().isBadRequest());
  assertThat(externalClient.metrics().requests()).isEqualTo(before);
 }
 @Test void manualSyncUsesOnly28KnownScopesAndIsolatesInvalidUf() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);AtomicInteger requests=new AtomicInteger();
  server.createContext("/",e->{requests.incrementAndGet();String path=e.getRequestURI().getPath();String payload;
   if(path.contains("/br/"))payload=fixture("ea20-first-round-national-official.json");
   else {String uf=path.substring(path.lastIndexOf('/')+1,path.lastIndexOf('/')+3);payload=uf.equals("sp")?"{":fixture("ea20-first-round-state-ac-official.json").replace("\"cdabr\" : \"ac\"","\"cdabr\" : \""+uf+"\"");}
   byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);e.close();});server.start();
  try{
   var p=br.com.election.tse.TseTestSupport.properties("http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofSeconds(2));var c=new TseResultsClient(p,Clock.fixed(TIME,ZoneOffset.UTC));
   var sync=new BaselineSyncService(c,new TseResultParser(json,p),new TseUrlBuilder(p),baseline,p,Clock.fixed(TIME,ZoneOffset.UTC));
   var summary=sync.sync();assertThat(summary.successCount()).isEqualTo(27);assertThat(summary.failureCount()).isEqualTo(1);assertThat(summary.errors().getFirst().scope()).isEqualTo("SP");assertThat(requests).hasValue(28);assertThat(baseline.at(TIME)).hasSize(27);
  }finally{server.stop(0);}
 }
}
