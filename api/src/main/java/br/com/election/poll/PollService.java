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
    private final jakarta.validation.Validator validator;
    public PollService(PollRepository polls, PollResultRepository results, PollsterRepository pollsters,
        CandidateRepository candidates, PollNormalizationService normalization, jakarta.validation.Validator validator) {
        this.polls = polls; this.results = results; this.pollsters = pollsters; this.candidates = candidates; this.normalization = normalization; this.validator=validator;
    }
    public List<PollResponse> list() { return responses(polls.findAllByOrderByPublishedAtDescFieldEndDescCreatedAtDescIdDesc()); }
    public List<PollResponse> latest() { return responses(polls.findLatestByPollster()); }
    public List<PollResponse> latestBefore(java.time.Instant publishedBefore) { return responses(polls.findLatestByPollsterBefore(publishedBefore)); }
    public PollResponse get(UUID id) { return response(find(id)); }
    @Transactional public PollResponse create(PollRequest request) { return save(new Poll(), request); }
    @Transactional public PollResponse update(UUID id, PollRequest request) {
        Poll poll = find(id); results.deleteResults(id); return save(poll, request);
    }
    @Transactional public void delete(UUID id) { Poll poll = find(id); results.deleteResults(id); polls.delete(poll); }
    private Poll find(UUID id) { return polls.findById(id).orElseThrow(() -> new NotFoundException("Pesquisa não encontrada.")); }
    private PollResponse response(Poll p) { return PollResponse.from(p, results.findResults(p.getId())); }
    private List<PollResponse> responses(List<Poll> selected) {
        if (selected.isEmpty()) return List.of();
        List<UUID> ids = selected.stream().map(Poll::getId).toList();
        // Initialize pollsters in one fetch, including polls selected by the native latest query.
        Map<UUID, Poll> fetched = polls.findByIdIn(ids).stream().collect(java.util.stream.Collectors.toMap(Poll::getId, p -> p));
        Map<UUID, List<PollResult>> grouped = results.findResultsForPolls(ids).stream()
            .collect(java.util.stream.Collectors.groupingBy(PollResult::getPollId));
        return ids.stream().map(id -> PollResponse.from(fetched.get(id), grouped.getOrDefault(id, List.of()))).toList();
    }
    public List<java.math.BigDecimal> validateAndNormalize(PollRequest r) {
        var errors=validator.validate(r);
        if(!errors.isEmpty()) throw new IllegalArgumentException(errors.stream().map(e->e.getPropertyPath()+": "+e.getMessage()).sorted().collect(java.util.stream.Collectors.joining("; ")));
        if (r.fieldEnd().isBefore(r.fieldStart())) throw new IllegalArgumentException("O fim do campo não pode ser anterior ao início.");
        Set<UUID> ids = new HashSet<>();
        for (PollRequest.Result result : r.results()) if (!ids.add(result.candidateId())) throw new IllegalArgumentException("Candidato repetido na pesquisa.");
        var normalized = normalization.normalize(r.voteBasis(), r.results().stream().map(PollRequest.Result::originalPercentage).toList());
        if(!pollsters.existsById(r.pollsterId())) throw new NotFoundException("Instituto não encontrado.");
        for(var item:r.results()) if(!candidates.existsById(item.candidateId())) throw new NotFoundException("Candidato não encontrado.");
        return normalized;
    }
    private PollResponse save(Poll poll, PollRequest r) {
        var normalized=validateAndNormalize(r);
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
