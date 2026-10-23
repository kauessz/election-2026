package br.com.election.tse;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
@Configuration @EnableScheduling
@ConditionalOnProperty(prefix="tse.results",name="scheduling-enabled",havingValue="true")
public class TseResultsScheduler {
 private final OfficialResultsService service;private int stateIndex;
 public TseResultsScheduler(OfficialResultsService service){this.service=service;}
 @Bean ThreadPoolTaskScheduler taskScheduler(){var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(3);scheduler.setThreadNamePrefix("tse-poll-");return scheduler;}
 @Scheduled(fixedDelayString="${tse.results.polling-interval:5s}")
 public void national(){service.refreshNational();}
 // One known UF per tick: each state refreshed approximately every 27 seconds, never per user GET.
 // TODO EA14 -> EA15 -> EA20: validate live change-file contracts before implementing selective refresh.
 @Scheduled(fixedDelayString="${tse.results.state-interval:1s}",initialDelayString="3s")
 public void states(){service.refreshState(BrazilState.values()[stateIndex++ % BrazilState.values().length]);}
 @Scheduled(fixedDelayString="1m",initialDelayString="1s")
 public void municipalities(){service.refreshCatalogue();}
}
