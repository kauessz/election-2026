package br.com.election.tse;
import java.time.*;
import java.nio.charset.StandardCharsets;
public final class TseTestSupport {
 public static String fixture(String name){try(var in=TseTestSupport.class.getResourceAsStream("/tse/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException(e);}}
 public static TseResultsProperties properties(String base,Duration timeout){return new TseResultsProperties(base,"oficial",2026,"6258","0001",Duration.ofSeconds(5),Duration.ofSeconds(1),timeout,Duration.ofMinutes(2),Duration.ofSeconds(1),Duration.ofSeconds(30),Duration.ofMillis(1),false);}
 public static class MutableClock extends Clock {
  private Instant now;public MutableClock(Instant now){this.now=now;}
  public void advance(Duration by){now=now.plus(by);}
  @Override public ZoneId getZone(){return ZoneOffset.UTC;}
  @Override public Clock withZone(ZoneId zone){return this;}
  @Override public Instant instant(){return now;}
 }
}
