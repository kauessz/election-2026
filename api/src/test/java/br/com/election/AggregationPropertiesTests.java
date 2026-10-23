package br.com.election;

import br.com.election.aggregate.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class AggregationPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AggregationConfiguration.class);
    @Test void bindsDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var p = context.getBean(AggregationProperties.class);
            assertThat(p.halfLifeDays()).isEqualByComparingTo("3");
            assertThat(p.maxInstituteWeight()).isEqualByComparingTo("0.15");
            assertThat(p.historicalMinFactor()).isEqualByComparingTo("0.85");
            assertThat(p.historicalMaxFactor()).isEqualByComparingTo("1.15");
        });
    }
    @Test void bindsOverrides() {
        runner.withPropertyValues("polling.aggregation.half-life-days=6", "polling.aggregation.max-institute-weight=0.2",
            "polling.aggregation.historical-min-factor=0.9", "polling.aggregation.historical-max-factor=1.1",
            "polling.aggregation.historical-election-key=BR-2022", "polling.aggregation.historical-round=2").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AggregationProperties.class).halfLifeDays()).isEqualByComparingTo("6");
            assertThat(context.getBean(AggregationProperties.class).historicalMinFactor()).isEqualByComparingTo("0.9");
            assertThat(context.getBean(AggregationProperties.class).historicalMaxFactor()).isEqualByComparingTo("1.1");
            assertThat(context.getBean(AggregationProperties.class).historicalElectionKey()).isEqualTo("BR-2022");
            assertThat(context.getBean(AggregationProperties.class).historicalRound()).isEqualTo(2);
        });
    }
    @Test void rejectsInvalidConfiguration() {
        for (String property : new String[]{"half-life-days=0", "max-institute-weight=0", "max-institute-weight=1.1",
            "historical-min-factor=0", "historical-min-factor=1.1", "historical-max-factor=0.9",
            "historical-max-factor=3", "historical-neutral-spread-error=0", "historical-round=3", "historical-election-key="}) {
            runner.withPropertyValues("polling.aggregation." + property).run(context -> assertThat(context).hasFailed());
        }
    }
}
