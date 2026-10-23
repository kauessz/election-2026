package br.com.election.readiness;
import br.com.election.aggregate.*;
import br.com.election.poll.PollResponse;
import br.com.election.pollster.PollsterPerformanceResponse;
import java.time.Instant;
import java.util.*;
public record PreElectionFreeze(UUID id,String label,Instant frozenAt,String gitCommit,AggregationProperties aggregationConfiguration,
 List<UUID> pollIds,List<UUID> pollsterPerformanceIds,List<AggregateResponse> aggregateResults,Composition composition,String notes,boolean active){
 public record Composition(List<PollResponse> polls,List<PollsterPerformanceResponse> performances,Map<UUID,Integer> candidateBallotNumbers){}
}
