package br.com.election.candidate;
import java.util.UUID;
public record CandidateResponse(UUID id, String name, String displayName, String party, Integer ballotNumber, boolean active) {
    public static CandidateResponse from(Candidate c) { return new CandidateResponse(c.getId(), c.getName(), c.getDisplayName(), c.getParty(), c.getBallotNumber(), c.getActive()); }
}
