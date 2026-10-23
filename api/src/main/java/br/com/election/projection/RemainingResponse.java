package br.com.election.projection;
import br.com.election.tse.OfficialElectionResult;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
public record RemainingResponse(String kind,Instant calculatedAt,int statesAvailable,int statesTotal,List<State> states,List<String> warnings){
 public record State(String uf,boolean available,Long totalElectorate,Long totalizedElectorate,Long remainingElectorate,
  BigDecimal representedPercentage,BigDecimal remainingPercentage,OfficialElectionResult partialResult,String reason) {}
}
