package br.com.election.tse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.slf4j.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
@Component
public class TseResultsClient {
 private static final Logger log=LoggerFactory.getLogger(TseResultsClient.class);
 private final RestClient http; private final Clock clock; private final TseResultsProperties p;
 private final Map<String,Resource> resources=new HashMap<>();
 private Instant nextRequest=Instant.EPOCH, blockedUntil=Instant.EPOCH;
 private long requests,ok,notModified,errors;
 private Instant lastRequestAt,lastSuccess; private Integer lastHttpStatus;
 private static class Resource { String etag,modified; Instant retryAt=Instant.EPOCH; int failures; }
 public record Fetch(int status,String body,String etag,String lastModified,boolean attempted,Instant requestedAt) {}
 public record Metrics(long requests,long responses200,long responses304,long errors,Instant lastRequestAt,Instant lastSuccessfulUpdate,Integer lastHttpStatus) {}
 public TseResultsClient(TseResultsProperties p,Clock clock) {
  this.p=p;this.clock=clock;
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(p.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build());
  factory.setReadTimeout(p.readTimeout());
  http=RestClient.builder().baseUrl(p.baseUrl()).requestFactory(factory).defaultHeader("User-Agent","Election2026/0.3 (official-results consumer)").build();
 }
 // Serializes outbound requests across all scopes and enforces one shared rate budget.
 public synchronized Fetch fetch(String path) {
  Resource r=resources.computeIfAbsent(path,k->new Resource()); Instant now=clock.instant();
  if(now.isBefore(r.retryAt)||now.isBefore(blockedUntil)) return new Fetch(0,null,null,null,false,null);
  long pause=Duration.between(now,nextRequest).toMillis();
  if(pause>0) try { Thread.sleep(pause); } catch(InterruptedException e){Thread.currentThread().interrupt();return new Fetch(0,null,null,null,false,null);}
  lastRequestAt=clock.instant(); nextRequest=lastRequestAt.plus(p.minimumRequestInterval()); requests++;
  try {
   Fetch response=http.get().uri(path).headers(h->{if(r.etag!=null)h.setIfNoneMatch(r.etag);if(r.modified!=null)h.set("If-Modified-Since",r.modified);})
    .exchange((request,res)->{
     int status=res.getStatusCode().value(); lastHttpStatus=status;
     if(status==429) {blockedUntil=clock.instant().plus(retryAfter(res.getHeaders().getFirst("Retry-After")));log.warn("tse rate_limited blockedUntil={}",blockedUntil);}
     return new Fetch(status,status==200?new String(res.getBody().readAllBytes(),StandardCharsets.UTF_8):null,res.getHeaders().getETag(),res.getHeaders().getFirst("Last-Modified"),true,lastRequestAt);
    });
   if(response.status()==200) {ok++;r.failures=0;lastSuccess=clock.instant();log.debug("tse status=200 path={}",path);}
   else if(response.status()==304) {notModified++;r.failures=0;lastSuccess=clock.instant();log.debug("tse status=304 path={}",path);}
   else failure(path,r,response.status());
   return response;
  } catch(Exception e) {lastHttpStatus=null;failure(path,r,0);log.warn("tse transport_failure path={} reason={}",path,e.getClass().getSimpleName());return new Fetch(0,null,null,null,true,lastRequestAt);}
 }
 // Commit validators only after parsing and durable storage succeeded.
 public synchronized void accept(String path,Fetch response) {
  Resource r=resources.computeIfAbsent(path,k->new Resource()); r.etag=response.etag();r.modified=response.lastModified();
 }
 public synchronized void invalid(String path) {failure(path,resources.computeIfAbsent(path,k->new Resource()),200);}
 private void failure(String path,Resource r,int status) {
  errors++;r.failures++; long seconds=status==404?600:Math.min(300,5L*(1L<<Math.min(r.failures-1,6)));
  r.retryAt=clock.instant().plusSeconds(seconds);
  log.warn("tse request_failed path={} status={} failures={} retryAt={}",path,status,r.failures,r.retryAt);
 }
 private Duration retryAfter(String value) {
  long seconds=600;
  if(value!=null)try {seconds=Math.max(seconds,Long.parseLong(value));} catch(NumberFormatException e){try{seconds=Math.max(seconds,Duration.between(clock.instant(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds());}catch(Exception ignored){}}
  return Duration.ofSeconds(seconds);
 }
 public synchronized Metrics metrics(){return new Metrics(requests,ok,notModified,errors,lastRequestAt,lastSuccess,lastHttpStatus);}
}
