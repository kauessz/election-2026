package br.com.election.aggregate;

import org.springframework.stereotype.Component;
import java.math.*;
import java.util.*;
import static java.math.MathContext.DECIMAL128;

@Component
public class WeightAllocator {
    public static final int WEIGHT_SCALE = 16;
    public record Allocation(List<BigDecimal> weights, Set<Integer> capped, boolean capFeasible, BigDecimal effectiveCap) {}

    public Allocation allocate(List<BigDecimal> raw, BigDecimal cap) {
        if (raw.isEmpty()) return new Allocation(List.of(), Set.of(), true, cap);
        if (raw.stream().anyMatch(w -> w == null || w.signum() <= 0)) throw new IllegalArgumentException("Pesos brutos devem ser positivos.");
        boolean feasible = cap == null || cap.multiply(BigDecimal.valueOf(raw.size())).compareTo(BigDecimal.ONE) >= 0;
        if (!feasible) {
            List<BigDecimal> equal = Collections.nCopies(raw.size(), BigDecimal.ONE);
            List<BigDecimal> equalWeights = round(normalize(equal), null);
            return new Allocation(equalWeights, Set.of(), false, equalWeights.stream().max(BigDecimal::compareTo).orElseThrow());
        }
        List<BigDecimal> weights = new ArrayList<>(Collections.nCopies(raw.size(), BigDecimal.ZERO));
        Set<Integer> remaining = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) remaining.add(i);
        Set<Integer> capped = new HashSet<>();
        BigDecimal mass = BigDecimal.ONE;
        while (!remaining.isEmpty()) {
            BigDecimal sum = remaining.stream().map(raw::get).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<Integer> overflow = new ArrayList<>();
            for (int i : remaining) {
                BigDecimal proposed = raw.get(i).multiply(mass, DECIMAL128).divide(sum, DECIMAL128);
                weights.set(i, proposed);
                if (cap != null && proposed.compareTo(cap) > 0) overflow.add(i);
            }
            if (overflow.isEmpty()) break;
            for (int i : overflow) { weights.set(i, cap); remaining.remove(i); capped.add(i); mass = mass.subtract(cap); }
        }
        return new Allocation(round(weights, cap), Set.copyOf(capped), true, cap);
    }

    private List<BigDecimal> normalize(List<BigDecimal> raw) {
        BigDecimal sum = raw.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return raw.stream().map(w -> w.divide(sum, DECIMAL128)).toList();
    }

    private List<BigDecimal> round(List<BigDecimal> values, BigDecimal cap) {
        List<BigDecimal> rounded = new ArrayList<>(values.stream().map(w -> w.setScale(WEIGHT_SCALE, RoundingMode.DOWN)).toList());
        BigDecimal residue = BigDecimal.ONE.subtract(rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) order.add(i);
        order.sort(Comparator.<Integer, BigDecimal>comparing(values::get).reversed().thenComparingInt(i -> i));
        for (int i : order) {
            BigDecimal addition = cap == null ? residue : residue.min(cap.subtract(rounded.get(i)));
            rounded.set(i, rounded.get(i).add(addition)); residue = residue.subtract(addition);
            if (residue.signum() == 0) break;
        }
        if (residue.signum() != 0) throw new IllegalStateException("Não foi possível distribuir o resíduo dos pesos.");
        return List.copyOf(rounded);
    }
}
