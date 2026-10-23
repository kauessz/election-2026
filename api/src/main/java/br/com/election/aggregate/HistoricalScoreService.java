package br.com.election.aggregate;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import static java.math.MathContext.DECIMAL128;

@Service
public class HistoricalScoreService {
    private final AggregationProperties properties;
    public HistoricalScoreService(AggregationProperties properties) { this.properties = properties; }

    public BigDecimal factor(BigDecimal spreadError) {
        // With default bounds and neutral error 3: clamp(1.15 - spreadError / 20, 0.85, 1.15).
        if (spreadError == null) return BigDecimal.ONE;
        if (spreadError.signum() < 0) throw new IllegalArgumentException("O erro histórico não pode ser negativo.");
        BigDecimal neutral = properties.historicalNeutralSpreadError();
        BigDecimal score;
        if (spreadError.compareTo(neutral) <= 0) {
            score = BigDecimal.ONE.add(properties.historicalMaxFactor().subtract(BigDecimal.ONE)
                .multiply(BigDecimal.ONE.subtract(spreadError.divide(neutral, DECIMAL128)), DECIMAL128));
        } else {
            score = BigDecimal.ONE.subtract(BigDecimal.ONE.subtract(properties.historicalMinFactor())
                .multiply(spreadError.subtract(neutral).divide(neutral, DECIMAL128), DECIMAL128));
        }
        return score.max(properties.historicalMinFactor()).min(properties.historicalMaxFactor());
    }
}
