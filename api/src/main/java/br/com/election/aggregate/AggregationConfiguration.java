package br.com.election.aggregate;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AggregationProperties.class)
public class AggregationConfiguration {
    @Bean Clock aggregationClock() { return Clock.systemUTC(); }
}
