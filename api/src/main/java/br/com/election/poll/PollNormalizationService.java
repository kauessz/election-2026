package br.com.election.poll;
import org.springframework.stereotype.Service;
import java.math.*;
import java.util.*;
@Service
public class PollNormalizationService {
    public static final int SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    public List<BigDecimal> normalize(VoteBasis basis, List<BigDecimal> values) {
        if (basis == null || values == null || values.isEmpty()) throw new IllegalArgumentException("Informe o tipo de voto e pelo menos um resultado.");
        for (BigDecimal value : values) {
            if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0 || value.stripTrailingZeros().scale() > SCALE)
                throw new IllegalArgumentException("Percentuais devem estar entre 0 e 100, com até quatro casas decimais.");
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.signum() == 0) throw new IllegalArgumentException("A soma dos candidatos deve ser maior que zero.");
        if (basis == VoteBasis.VALID) return values.stream().map(v -> v.setScale(SCALE, RoundingMode.HALF_UP)).toList();
        if (sum.compareTo(HUNDRED) > 0) throw new IllegalArgumentException("A soma dos percentuais dos candidatos em votos totais não pode ultrapassar 100%.");
        List<BigDecimal> normalized = new ArrayList<>(values.stream()
            .map(v -> v.multiply(HUNDRED).divide(sum, SCALE, RoundingMode.HALF_UP)).toList());
        // Assign the rounding residue to the largest share, preserving zero shares.
        BigDecimal residue = HUNDRED.subtract(normalized.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        int largest = 0;
        for (int i = 1; i < values.size(); i++) if (values.get(i).compareTo(values.get(largest)) > 0) largest = i;
        normalized.set(largest, normalized.get(largest).add(residue));
        return List.copyOf(normalized);
    }
}
