package br.com.election.candidate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import java.util.List;
@Service
public class CandidateService {
    private final CandidateRepository repository;
    public CandidateService(CandidateRepository repository) { this.repository = repository; }
    @Transactional(readOnly=true) public List<CandidateResponse> list() { return repository.findAll(Sort.by("displayName")).stream().map(CandidateResponse::from).toList(); }
    @Transactional public CandidateResponse create(CandidateRequest request) {
        Candidate c = new Candidate(); c.setName(request.name().trim()); c.setDisplayName(request.displayName().trim());
        c.setParty(request.party().trim()); c.setBallotNumber(request.ballotNumber()); c.setActive(!Boolean.FALSE.equals(request.active()));
        return CandidateResponse.from(repository.save(c));
    }
}
