package br.com.election.projection;
import br.com.election.tse.*;
import java.util.*;
public final class GeographicGate {
 public static final Set<BrazilState> KEY_STATES=Set.of(BrazilState.SP,BrazilState.MG,BrazilState.RJ,BrazilState.BA,BrazilState.PR,BrazilState.RS);
 private GeographicGate() {}
 public static ProjectionResponse.GeographicCoverage evaluate(Set<BrazilState> states,ProjectionProperties p){
  var regions=states.stream().map(BrazilRegion::of).distinct().sorted().map(Enum::name).toList();
  var keyStates=states.stream().filter(KEY_STATES::contains).map(Enum::name).sorted().toList();
  return new ProjectionResponse.GeographicCoverage(regions.size(),BrazilRegion.values().length,keyStates.size(),KEY_STATES.size(),regions,keyStates,
   regions.size()>=p.minimumRegionsWithData()&&keyStates.size()>=p.minimumKeyStatesWithData());
 }
}
