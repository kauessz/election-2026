package br.com.election.tse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static br.com.election.tse.TseTestSupport.*;
class TseResultsClientTests {
 HttpServer server;ExecutorService executor;TseResultsClient client;MutableClock clock;
 AtomicInteger status=new AtomicInteger(200),hits=new AtomicInteger();String seenEtag,seenModified,seenAgent;long delay;
 @BeforeEach void start() throws Exception {
  server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
  server.createContext("/",exchange->{
   hits.incrementAndGet();seenEtag=exchange.getRequestHeaders().getFirst("If-None-Match");seenModified=exchange.getRequestHeaders().getFirst("If-Modified-Since");seenAgent=exchange.getRequestHeaders().getFirst("User-Agent");
   if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   try{byte[] bytes=fixture("ea20-national-official.json").getBytes(java.nio.charset.StandardCharsets.UTF_8);
   exchange.getResponseHeaders().add("ETag","\"real-fixture\"");exchange.getResponseHeaders().add("Last-Modified","Thu, 15 Oct 2026 12:38:25 GMT");exchange.getResponseHeaders().add("Retry-After","1200");
   exchange.sendResponseHeaders(status.get(),status.get()==304?-1:bytes.length);if(status.get()!=304)exchange.getResponseBody().write(bytes);
   }finally{exchange.close();}
  });server.start();clock=new MutableClock(Instant.parse("2026-10-23T13:00:01Z"));
  client=new TseResultsClient(properties("http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofSeconds(2)),clock);
 }
 @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
 @Test void conditionalRequestsAnd304DoNotDownloadOrReprocess(){
  var first=client.fetch("/result");assertThat(first.status()).isEqualTo(200);assertThat(first.body()).contains("6258");assertThat(seenAgent).contains("Election2026");
  client.accept("/result",first);status.set(304);clock.advance(Duration.ofSeconds(5));var second=client.fetch("/result");
  assertThat(second.status()).isEqualTo(304);assertThat(second.body()).isNull();assertThat(seenEtag).isEqualTo("\"real-fixture\"");assertThat(seenModified).contains("15 Oct");
  assertThat(client.metrics().responses304()).isEqualTo(1);assertThat(client.metrics().lastSuccessfulUpdate()).isEqualTo(clock.instant());
 }
 @ParameterizedTest @ValueSource(ints={404,429,500}) void backsOffWithoutRepeatedRequests(int code){
  status.set(code);assertThat(client.fetch("/result").status()).isEqualTo(code);assertThat(client.fetch("/result").attempted()).isFalse();assertThat(hits).hasValue(1);assertThat(client.metrics().errors()).isEqualTo(1);
 }
 @Test void rateLimitBlocksOtherScopesAndHonorsRetryAfter(){
  status.set(429);client.fetch("/national");clock.advance(Duration.ofSeconds(601));assertThat(client.fetch("/state").attempted()).isFalse();
  clock.advance(Duration.ofSeconds(600));status.set(200);assertThat(client.fetch("/state").status()).isEqualTo(200);
 }
 @Test void timeoutIsContainedAndCounted(){delay=350;client=new TseResultsClient(properties("http://127.0.0.1:"+server.getAddress().getPort(),Duration.ofMillis(50)),clock);assertThat(client.fetch("/result").status()).isZero();assertThat(client.metrics().errors()).isEqualTo(1);assertThat(client.metrics().lastHttpStatus()).isNull();}
 @Test void doesNotCommitValidatorsBeforeValidation(){client.fetch("/bad");clock.advance(Duration.ofSeconds(1));client.fetch("/bad");assertThat(seenEtag).isNull();assertThat(seenModified).isNull();}
 @Test void resourceBackoffDoesNotBlockHealthyScope(){status.set(404);client.fetch("/missing");status.set(200);clock.advance(Duration.ofSeconds(1));assertThat(client.fetch("/known").status()).isEqualTo(200);}
}
