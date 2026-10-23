package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class SimpleAggregateCalculator implements AggregateCalculator {
    public boolean supports(AggregateMethod method) { return method == AggregateMethod.SIMPLE; }
    public Factors calculate(PollResponse poll, LocalDate referenceDate, BigDecimal history) {
        return new Factors(BigDecimal.ONE, null, null, null, null);
    }
}
