package br.com.election.tse.dto.raw;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
@JsonIgnoreProperties(ignoreUnknown=true)
public record Ea20(String ele,String t,String f,String tpabr,String cdabr,String dg,String hg,String idg,String tf,
 List<Office> carg, Sections s, Electorate e, Votes v) {
 @JsonIgnoreProperties(ignoreUnknown=true) public record Office(String cd,List<Group> agr) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Group(List<Party> par) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Party(List<Candidate> cand) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Candidate(String n,String sqcand,String nm,String nmu,String vap,String pvap,String pvapn) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Sections(String ts,String st,String pst,String snt) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Electorate(String te,String est,String pest,String a,String pa,String c) {}
 @JsonIgnoreProperties(ignoreUnknown=true) public record Votes(String tv,String vv,String vb,String tvn,String vnom,String vvc) {}
}
