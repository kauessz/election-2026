package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

public interface AggregateCalculator {
    boolean supports(AggregateMethod method);
    Factors calculate(PollResponse poll, LocalDate referenceDate, BigDecimal historicalFactor);

    record Factors(BigDecimal rawWeight, BigDecimal recencyWeight, BigDecimal precisionWeight,
                   BigDecimal historicalWeightFactor, String precisionSource) {}
}
