package br.com.election.poll;
import br.com.election.candidate.*;
import br.com.election.pollster.*;
import br.com.election.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@Transactional(readOnly=true)
public class PollService {
    private final PollRepository polls;
    private final PollResultRepository results;
    private final PollsterRepository pollsters;
    private final CandidateRepository candidates;
    private final PollNormalizationService normalization;
    public PollService(PollRepository polls, PollResultRepository results, PollsterRepository pollsters,
        CandidateRepository candidates, PollNormalizationService normalization) {
        this.polls = polls; this.results = results; this.pollsters = pollsters; this.candidates = candidates; this.normalization = normalization;
    }
    public List<PollResponse> list() { return polls.findAllByOrderByPublishedAtDescFieldEndDescCreatedAtDescIdDesc().stream().map(this::response).toList(); }
    public List<PollResponse> latest() { return polls.findLatestByPollster().stream().map(this::response).toList(); }
    public PollResponse get(UUID id) { return response(find(id)); }
    @Transactional public PollResponse create(PollRequest request) { return save(new Poll(), request); }
    @Transactional public PollResponse update(UUID id, PollRequest request) {
        Poll poll = find(id); results.deleteResults(id); return save(poll, request);
    }
    @Transactional public void delete(UUID id) { Poll poll = find(id); results.deleteResults(id); polls.delete(poll); }
    private Poll find(UUID id) { return polls.findById(id).orElseThrow(() -> new NotFoundException("Pesquisa não encontrada.")); }
    private PollResponse response(Poll p) { return PollResponse.from(p, results.findResults(p.getId())); }
    private PollResponse save(Poll poll, PollRequest r) {
        if (r.fieldEnd().isBefore(r.fieldStart())) throw new IllegalArgumentException("O fim do campo não pode ser anterior ao início.");
        Set<UUID> ids = new HashSet<>();
        for (PollRequest.Result result : r.results()) if (!ids.add(result.candidateId())) throw new IllegalArgumentException("Candidato repetido na pesquisa.");
        var normalized = normalization.normalize(r.voteBasis(), r.results().stream().map(PollRequest.Result::originalPercentage).toList());
        poll.setPollster(pollsters.findById(r.pollsterId()).orElseThrow(() -> new NotFoundException("Instituto não encontrado.")));
        poll.setTseRegistrationNumber(r.registrationNumber()); poll.setFieldStart(r.fieldStart()); poll.setFieldEnd(r.fieldEnd());
        poll.setPublishedAt(r.publishedAt()); poll.setSampleSize(r.sampleSize()); poll.setMarginOfError(r.marginOfError());
        poll.setMethodology(r.methodology()); poll.setSourceUrl(r.sourceUrl()); poll.setVoteBasis(r.voteBasis());
        poll.touch();
        polls.saveAndFlush(poll);
        List<PollResult> saved = new ArrayList<>();
        for (int i = 0; i < r.results().size(); i++) {
            var item = r.results().get(i);
            Candidate candidate = candidates.findById(item.candidateId()).orElseThrow(() -> new NotFoundException("Candidato não encontrado."));
            saved.add(new PollResult(poll, candidate, item.originalPercentage(), normalized.get(i)));
        }
        results.saveAllAndFlush(saved); return response(poll);
    }
}
