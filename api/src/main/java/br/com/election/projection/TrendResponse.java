package br.com.election.projection;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
public record TrendResponse(String kind,Instant calculatedAt,Instant officialGeneratedAt,List<Window> windows,List<String> warnings){
 public record CandidateShare(String candidateNumber,String name,long newVotes,BigDecimal share,BigDecimal percentage) {}
 public record Window(int minutes,Instant requestedFrom,Instant requestedTo,Instant from,Instant to,Long newValidVotes,
  List<CandidateShare> candidateShares,boolean available,int usedIntervals,int excludedIntervals,String reason) {}
}
