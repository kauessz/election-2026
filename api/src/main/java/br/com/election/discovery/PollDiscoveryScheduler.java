package br.com.election.discovery;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.slf4j.*;
@Configuration @EnableScheduling
@ConditionalOnProperty(prefix="polling.discovery",name="enabled",havingValue="true")
public class PollDiscoveryScheduler {
    private static final Logger log=LoggerFactory.getLogger(PollDiscoveryScheduler.class);
    private final PollDiscoveryService service;
    public PollDiscoveryScheduler(PollDiscoveryService service){this.service=service;}
    @Scheduled(cron="0 0 8,12,16 * * *",zone="America/Sao_Paulo")
    public void sync(){try{var result=service.sync();log.info("poll discovery sync created={} updated={} unchanged={}",result.created(),result.updated(),result.unchanged());}
        catch(RuntimeException e){log.warn("poll discovery sync failed: {}",e.getMessage());}}
}
