package br.com.election.aggregate;

import br.com.election.poll.PollResponse;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import static java.math.MathContext.DECIMAL128;

@Component
public class WeightFactors {
    private static final BigDecimal TWO = new BigDecimal("2");
    // 1.96 × sqrt(0.5 × 0.5) × 100: 95% confidence, maximum variance, percentage points.
    private static final BigDecimal ESTIMATED_MARGIN_COEFFICIENT = new BigDecimal("98");
    private static final int MAX_DECAY_EXPONENT = 1_000_000;
    private final AggregationProperties properties;
    public WeightFactors(AggregationProperties properties) { this.properties = properties; }

    public BigDecimal ageInDays(PollResponse poll, LocalDate referenceDate) {
        BigDecimal midpoint = BigDecimal.valueOf(poll.fieldStart().toEpochDay()).add(
            BigDecimal.valueOf(ChronoUnit.DAYS.between(poll.fieldStart(), poll.fieldEnd())).divide(TWO));
        return BigDecimal.valueOf(referenceDate.toEpochDay()).subtract(midpoint).max(BigDecimal.ZERO);
    }

    public BigDecimal recency(PollResponse poll, LocalDate referenceDate) {
        BigDecimal exponent = ageInDays(poll, referenceDate).divide(properties.halfLifeDays(), DECIMAL128);
        if (exponent.compareTo(BigDecimal.valueOf(MAX_DECAY_EXPONENT)) > 0)
            throw new IllegalArgumentException("Intervalo de recência excede o limite numérico suportado.");
        int whole = exponent.intValue();
        double fraction = exponent.subtract(BigDecimal.valueOf(whole)).doubleValue();
        // Only the fractional exponent crosses to double; integer decay remains decimal,
        // avoiding floating-point underflow for old polls. StrictMath is reproducible.
        return BigDecimal.valueOf(StrictMath.pow(2.0, -fraction)).divide(TWO.pow(whole, DECIMAL128), DECIMAL128);
    }

    public BigDecimal precision(PollResponse poll) {
        BigDecimal margin = hasDeclaredMargin(poll) ? poll.marginOfError() : estimatedMarginOfError(poll.sampleSize());
        return margin == null ? BigDecimal.ONE : BigDecimal.ONE.divide(margin.pow(2, DECIMAL128), DECIMAL128);
    }

    /** Simple-random-sampling fallback only; not a reconstruction of the institute's methodology. */
    public BigDecimal estimatedMarginOfError(Integer sampleSize) {
        if (sampleSize == null || sampleSize <= 0) return null;
        return ESTIMATED_MARGIN_COEFFICIENT.divide(BigDecimal.valueOf(sampleSize).sqrt(DECIMAL128), DECIMAL128);
    }

    private boolean hasDeclaredMargin(PollResponse poll) {
        return poll.marginOfError() != null && poll.marginOfError().signum() > 0;
    }

    public String precisionSource(PollResponse poll) {
        return hasDeclaredMargin(poll) ? "MARGIN_OF_ERROR" : poll.sampleSize() != null && poll.sampleSize() > 0 ? "SAMPLE_SIZE" : "NEUTRAL";
    }
}
