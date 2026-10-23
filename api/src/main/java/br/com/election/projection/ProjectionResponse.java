package br.com.election.projection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record ProjectionResponse(String kind,String model,boolean enabled,ProjectionStatus status,boolean publishable,
 Instant calculatedAt,Instant officialGeneratedAt,String officialGenerationKey,BigDecimal nationalElectorateProgress,
 int statesWithData,int statesTotal,boolean coverageComplete,boolean projectionIncomplete,ProjectionCoverage coverage,
 List<String> missingBaselineStates,List<String> missingOfficialStates,List<CandidateProjection> results,Spread spread,
 List<StateProjection> states,List<String> warnings,ProjectionProperties configuration,
 GeographicCoverage geography,OverseasContribution overseas) {
 public record CandidateProjection(String candidateNumber,String name,BigDecimal projectedVotes,BigDecimal projectedPercentage) {}
 public record GeographicCoverage(int regionsWithData,int regionsTotal,int keyStatesWithData,int keyStatesTotal,
  List<String> regions,List<String> keyStates,boolean thresholdsMet) {}
 public record ObservedCandidate(String candidateNumber,String name,long votes) {}
 public record OverseasContribution(boolean available,boolean incorporated,Instant officialGeneratedAt,
  List<ObservedCandidate> observedCandidates,String note) {}
 public record Spread(String leader,BigDecimal points) {}
 public record StateCandidate(String candidateNumber,String name,BigDecimal baselineShare,BigDecimal observedShare,
  BigDecimal projectedShare,BigDecimal projectedCandidateVotes) {}
 public record StateProjection(String uf,boolean available,Instant officialGeneratedAt,Instant baselineGeneratedAt,String baselineSourceUrl,
  Long totalElectorate,Long totalizedElectorate,BigDecimal electorateProgress,BigDecimal observationWeight,
  BigDecimal baselineValidRate,BigDecimal observedValidRate,BigDecimal projectedValidRate,BigDecimal projectedValidVotes,
  List<StateCandidate> candidates,List<String> warnings) {}
 public ProjectionResponse publicationView(boolean sourceFresh){
  if(publishable&&sourceFresh)return this;
  List<String> notes=new java.util.ArrayList<>(warnings);
  if(!sourceFresh)notes.add("Fonte oficial desatualizada ou indisponível: projeção não publicada.");
  return new ProjectionResponse(kind,model,enabled,status==ProjectionStatus.NOT_STARTED?status:ProjectionStatus.INSUFFICIENT_DATA,false,calculatedAt,officialGeneratedAt,officialGenerationKey,nationalElectorateProgress,statesWithData,statesTotal,coverageComplete,projectionIncomplete,coverage,missingBaselineStates,missingOfficialStates,List.of(),null,List.of(),List.copyOf(notes),configuration,geography,overseas);
 }
}
