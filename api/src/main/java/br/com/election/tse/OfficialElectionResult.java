package br.com.election.tse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
public record OfficialElectionResult(String electionId, int round, Scope scope, Instant generatedAt,
 String generationId, boolean finalTotalization, Sections sections, Electorate electorate, Votes votes,
 List<CandidateResult> candidates, List<String> warnings) {
 public record Scope(ElectionScopeType type, String code, String name) {
  public static Scope national() { return new Scope(ElectionScopeType.NATIONAL,"BR","Brasil"); }
  public static Scope overseas() { return new Scope(ElectionScopeType.STATE,"ZZ","Exterior"); }
  public static Scope state(BrazilState uf) { return new Scope(ElectionScopeType.STATE,uf.name(),uf.displayName); }
 }
 public record Sections(Long total, Long totalized, BigDecimal percentageTotalized, Long notTotalized) {}
 public record Electorate(Long total, Long totalized, BigDecimal percentageTotalized, Long abstentions, BigDecimal abstentionPercentage, Long turnout) {}
 public record Votes(Long totalVotes, Long validVotes, Long blankVotes, Long nullVotes, Long nominalVotes, Long competingVotes) {}
 public record CandidateResult(String candidateNumber, String candidateSequence, String name, String displayName,
  Long votes, BigDecimal percentage, BigDecimal precisePercentage, UUID localCandidateId) {}
}
