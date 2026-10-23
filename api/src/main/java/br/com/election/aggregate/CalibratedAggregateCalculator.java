package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.LocalDate;

@Component
public class CalibratedAggregateCalculator implements AggregateCalculator {
    private final WeightFactors factors;
    public CalibratedAggregateCalculator(WeightFactors factors) { this.factors = factors; }
    public boolean supports(AggregateMethod method) { return method == AggregateMethod.CALIBRATED; }
    public Factors calculate(PollResponse poll, LocalDate referenceDate, BigDecimal history) {
        BigDecimal recency = factors.recency(poll, referenceDate);
        BigDecimal precision = factors.precision(poll);
        BigDecimal score = history == null ? BigDecimal.ONE : history;
        return new Factors(recency.multiply(precision, MathContext.DECIMAL128).multiply(score, MathContext.DECIMAL128),
            recency, precision, score, factors.precisionSource(poll));
    }
}
