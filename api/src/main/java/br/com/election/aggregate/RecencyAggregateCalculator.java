package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class RecencyAggregateCalculator implements AggregateCalculator {
    private final WeightFactors factors;
    public RecencyAggregateCalculator(WeightFactors factors) { this.factors = factors; }
    public boolean supports(AggregateMethod method) { return method == AggregateMethod.RECENCY; }
    public Factors calculate(PollResponse poll, LocalDate referenceDate, BigDecimal history) {
        BigDecimal recency = factors.recency(poll, referenceDate);
        return new Factors(recency, recency, null, null, null);
    }
}
