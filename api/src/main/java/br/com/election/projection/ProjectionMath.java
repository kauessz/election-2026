package br.com.election.projection;
import java.math.*;
public final class ProjectionMath {
 public static final MathContext MC=MathContext.DECIMAL128;
 public static final BigDecimal ZERO=BigDecimal.ZERO,ONE=BigDecimal.ONE,HUNDRED=BigDecimal.valueOf(100);
 public static final java.util.List<String> PAIR=java.util.List.of("13","22");
 private ProjectionMath() {}
 public static BigDecimal ratio(long a,long b){return BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b),MC);}
 public static BigDecimal clamp(BigDecimal n,BigDecimal low,BigDecimal high){return n.max(low).min(high);}
 public static BigDecimal mix(BigDecimal observed,BigDecimal prior,BigDecimal weight){return observed.multiply(weight,MC).add(prior.multiply(ONE.subtract(weight),MC),MC);}
 public static BigDecimal weight(long represented,long electorate,BigDecimal priorEquivalentProgress){
  if(electorate<=0||represented<0||represented>electorate)throw new IllegalArgumentException("Eleitorado inválido");
  if(represented==electorate)return ONE; // Explicit endpoint guarantees exact convergence.
  return BigDecimal.valueOf(represented).divide(BigDecimal.valueOf(represented).add(BigDecimal.valueOf(electorate).multiply(priorEquivalentProgress,MC)),MC);
 }
}
