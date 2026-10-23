package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.LocalDate;

@Component
public class WeightedAggregateCalculator implements AggregateCalculator {
    private final WeightFactors factors;
    public WeightedAggregateCalculator(WeightFactors factors) { this.factors = factors; }
    public boolean supports(AggregateMethod method) { return method == AggregateMethod.WEIGHTED; }
    public Factors calculate(PollResponse poll, LocalDate referenceDate, BigDecimal history) {
        BigDecimal recency = factors.recency(poll, referenceDate);
        BigDecimal precision = factors.precision(poll);
        return new Factors(recency.multiply(precision, MathContext.DECIMAL128), recency, precision, null, factors.precisionSource(poll));
    }
}
