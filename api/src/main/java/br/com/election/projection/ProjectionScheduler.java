package br.com.election.projection;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
@Component @ConditionalOnProperty(prefix="tse.results",name="scheduling-enabled",havingValue="true")
public class ProjectionScheduler {
 private final ProjectionService service;private final ProjectionProperties p;
 public ProjectionScheduler(ProjectionService service,ProjectionProperties p){this.service=service;this.p=p;}
 @Scheduled(fixedDelayString="${tse.results.polling-interval:5s}",initialDelayString="5s")
 public void snapshot(){if(p.enabled())service.recordLatest();}
}
