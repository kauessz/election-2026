package br.com.election.projection;
import br.com.election.tse.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import static br.com.election.projection.ProjectionMath.*;
public record BaselineData(String electionId,int round,OfficialElectionResult.Scope scope,long electorate,
 long totalVotes,long validVotes,Long blankVotes,Long nullVotes,BigDecimal validVotesPerElector,
 Map<String,BigDecimal> pairwiseShares,OfficialElectionResult officialResult,Instant sourceGeneratedAt,Instant capturedAt,String sourceUrl) {
 public static final String FIRST_ROUND_ELECTION="6257";
 public static BaselineData from(OfficialElectionResult r,Instant capturedAt,String sourceUrl){
  if(!FIRST_ROUND_ELECTION.equals(r.electionId())||r.round()!=1||!r.finalTotalization()||
   (r.scope().type()!=ElectionScopeType.NATIONAL&&r.scope().type()!=ElectionScopeType.STATE)||r.electorate().total()<=0||r.votes().validVotes()<=0)
   throw new IllegalArgumentException("Baseline exige eleição 6257, turno 1, totalização final e totais positivos");
  var a=r.candidates().stream().filter(c->PAIR.get(0).equals(c.candidateNumber())).findFirst().orElseThrow(()->new IllegalArgumentException("Baseline sem candidato 13"));
  var b=r.candidates().stream().filter(c->PAIR.get(1).equals(c.candidateNumber())).findFirst().orElseThrow(()->new IllegalArgumentException("Baseline sem candidato 22"));
  long pair=Math.addExact(a.votes(),b.votes());
  if(pair<=0||pair>r.votes().validVotes()||r.votes().validVotes()>r.electorate().total())throw new IllegalArgumentException("Baseline com contagens fora do domínio do modelo");
  BigDecimal share=ratio(a.votes(),pair);
  return new BaselineData(r.electionId(),1,r.scope(),r.electorate().total(),r.votes().totalVotes(),r.votes().validVotes(),r.votes().blankVotes(),r.votes().nullVotes(),ratio(r.votes().validVotes(),r.electorate().total()),Map.of(PAIR.get(0),share,PAIR.get(1),ONE.subtract(share)),r,r.generatedAt(),capturedAt,sourceUrl);
 }
}
